"use client";

/**
 * Client d'API du front web.
 *
 * · toutes les requêtes utilisent des URL **relatives** (`/proxy/...`) : le
 *   navigateur ne connaît ni l'URL du backend ni un quelconque 127.0.0.1 ;
 * · le jeton d'accès est conservé côté navigateur (localStorage) et injecté en
 *   `Authorization: Bearer` ; le jeton de rafraîchissement n'est jamais affiché ;
 * · toute erreur remonte avec le message exact du serveur : l'interface n'affiche
 *   jamais un succès quand l'appel a échoué.
 */

const TOKEN_KEY = "signalpro.access_token";
const REFRESH_KEY = "signalpro.refresh_token";

export type ApiError = { status: number; message: string };

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setTokens(access: string, refresh: string) {
  window.localStorage.setItem(TOKEN_KEY, access);
  window.localStorage.setItem(REFRESH_KEY, refresh);
}

export function clearTokens() {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
}

async function refreshAccessToken(): Promise<boolean> {
  const refresh = window.localStorage.getItem(REFRESH_KEY);
  if (!refresh) return false;
  const response = await fetch("/proxy/api/v1/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refresh_token: refresh }),
  });
  if (!response.ok) {
    clearTokens();
    return false;
  }
  const data = (await response.json()) as { access_token: string; refresh_token: string };
  setTokens(data.access_token, data.refresh_token);
  return true;
}

function absolute(path: string): string {
  if (path.startsWith("/proxy/")) return path;
  return `/proxy${path.startsWith("/") ? path : `/${path}`}`;
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  retryOn401 = true,
): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  headers.set("X-Client", "signalpro-web");
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(absolute(path), { ...init, headers });

  if (response.status === 401 && retryOn401 && (await refreshAccessToken())) {
    return apiFetch<T>(path, init, false);
  }

  if (!response.ok) {
    let message = `Erreur ${response.status}.`;
    try {
      const payload = (await response.json()) as { detail?: string };
      if (payload?.detail) message = payload.detail;
    } catch {
      /* corps non JSON : on garde le message générique */
    }
    throw { status: response.status, message } satisfies ApiError;
  }

  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) return (await response.json()) as T;
  return (await response.text()) as unknown as T;
}

export const api = {
  // --- Authentification ---------------------------------------------------
  register: (body: Record<string, unknown>) =>
    apiFetch<{ user_id: number; dev_code?: string | null; delivered: boolean; delivery_detail?: string }>(
      "/api/v1/auth/register",
      { method: "POST", body: JSON.stringify(body) },
    ),
  verify: (email: string, code: string) =>
    apiFetch<{ access_token: string; refresh_token: string }>("/api/v1/auth/verify", {
      method: "POST",
      body: JSON.stringify({ email, code }),
    }),
  login: (email: string, password: string) =>
    apiFetch<{ access_token: string; refresh_token: string }>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  me: () => apiFetch<Record<string, unknown>>("/api/v1/auth/me"),
  limits: () => apiFetch<Record<string, unknown>>("/api/v1/auth/limits"),
  consents: () => apiFetch<Array<Record<string, unknown>>>("/api/v1/auth/consents"),
  // Les consentements sont enregistrés par paramètres de requête (pas de corps JSON) :
  // chaque acceptation est horodatée et archivée pour l'audit.
  recordConsent: (kind: string, accepted = true) =>
    apiFetch<{ ok: boolean }>(`/api/v1/auth/consents?kind=${encodeURIComponent(kind)}&accepted=${accepted}`, {
      method: "POST",
    }),
  resendCode: (email: string, phone: string, password: string) =>
    apiFetch<{ dev_code?: string | null; delivery_detail?: string }>("/api/v1/auth/resend-code", {
      method: "POST",
      body: JSON.stringify({ email, phone, password }),
    }),
  changePassword: (current_password: string, new_password: string) =>
    apiFetch<{ ok: boolean; detail?: string }>("/api/v1/auth/change-password", {
      method: "POST",
      body: JSON.stringify({ current_password, new_password }),
    }),
  deleteAccount: (password: string, confirm: string) =>
    apiFetch<Record<string, unknown>>("/api/v1/auth/account", {
      method: "DELETE",
      body: JSON.stringify({ password, confirm }),
    }),

  // --- Signalements -------------------------------------------------------
  reports: (query = "") => apiFetch<{ total: number; items: any[] }>(`/api/v1/reports${query}`),
  report: (id: number) => apiFetch<any>(`/api/v1/reports/${id}`),
  usage: () => apiFetch<any>("/api/v1/reports/usage"),
  createReport: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/reports", { method: "POST", body: JSON.stringify(body) }),
  uploadEvidence: (reportId: number, kind: string, file: File) => {
    const form = new FormData();
    form.append("kind", kind);
    form.append("file", file);
    return apiFetch<any>(`/api/v1/reports/${reportId}/evidence`, { method: "POST", body: form });
  },
  submitReport: (reportId: number, adapter: string, manual_ack: boolean, deviceId?: number) => {
    const form = new FormData();
    form.append("adapter", adapter);
    form.append("manual_ack", String(manual_ack));
    if (deviceId) form.append("device_id", String(deviceId));
    return apiFetch<any>(`/api/v1/reports/${reportId}/submit`, { method: "POST", body: form });
  },
  markManualDone: (reportId: number, note: string) => {
    const form = new FormData();
    form.append("done", "true");
    form.append("note", note);
    return apiFetch<any>(`/api/v1/reports/${reportId}/mark-manual-done`, { method: "POST", body: form });
  },
  exportReportText: (reportId: number) =>
    apiFetch<string>(`/api/v1/reports/${reportId}/export`),
  importTemplate: () => apiFetch<string>("/api/v1/reports/import/template"),
  importPreview: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiFetch<any>("/api/v1/reports/import/preview", { method: "POST", body: form });
  },
  importCommit: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiFetch<any>("/api/v1/reports/import/commit", { method: "POST", body: form });
  },

  // --- Communauté ---------------------------------------------------------
  blacklist: (limit = 200) => apiFetch<any>(`/api/v1/community/blacklist?limit=${limit}`),
  blacklistExport: () => apiFetch<{ count: number; numbers: string[]; disclaimer: string }>(
    "/api/v1/community/blacklist/export",
  ),
  blockAll: (device_id: number, max_numbers: number) =>
    apiFetch<any>("/api/v1/community/block-all", {
      method: "POST",
      body: JSON.stringify({ device_id, max_numbers, consent_ack: true }),
    }),
  createAppeal: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/community/appeals", { method: "POST", body: JSON.stringify(body) }),
  communityStats: () => apiFetch<any>("/api/v1/community/stats"),
  appealStatus: (publicRef: string) => apiFetch<any>(`/api/v1/community/appeals/${encodeURIComponent(publicRef)}`),

  // --- Appareils ----------------------------------------------------------
  devices: () => apiFetch<any[]>("/api/v1/devices"),
  gatewayStatus: () => apiFetch<any>("/api/v1/devices/gateway/status"),
  businessStatus: () => apiFetch<any>("/api/v1/devices/business/status"),
  linkBusiness: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/devices/business/link", { method: "POST", body: JSON.stringify(body) }),
  syncDevice: (id: number) => apiFetch<any>(`/api/v1/devices/${id}/sync`, { method: "POST" }),
  refreshDevice: (id: number) => apiFetch<any>(`/api/v1/devices/${id}/refresh`, { method: "POST" }),
  // Aucun corps : le motif de révocation est déterminé côté serveur (état réel de la passerelle).
  revokeDevice: (id: number) => apiFetch<any>(`/api/v1/devices/${id}`, { method: "DELETE" }),
  reportSuspension: (target_phone: string, note: string) =>
    apiFetch<any>(
      `/api/v1/devices/report-suspension?target_phone=${encodeURIComponent(target_phone)}&note=${encodeURIComponent(note)}`,
      { method: "POST" },
    ),

  // --- Tableau de bord ----------------------------------------------------
  overview: () => apiFetch<any>("/api/v1/dashboard/overview"),
  notifications: () => apiFetch<any[]>("/api/v1/dashboard/notifications"),
  markNotificationsRead: () => apiFetch<any>("/api/v1/dashboard/notifications/read", { method: "POST" }),
  alerts: () => apiFetch<any[]>("/api/v1/dashboard/alerts"),
  scanAlerts: () => apiFetch<any>("/api/v1/dashboard/alerts/scan", { method: "POST" }),
  exportMyReports: () => apiFetch<string>("/api/v1/dashboard/export/reports"),

  // --- Détection ----------------------------------------------------------
  signatures: () => apiFetch<any>("/api/v1/detect/signatures"),
  scan: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/detect/scan", { method: "POST", body: JSON.stringify(body) }),
  hits: () => apiFetch<any[]>("/api/v1/detect/hits"),

  // --- Campagnes ----------------------------------------------------------
  campaignPreview: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/campaigns/preview", { method: "POST", body: JSON.stringify(body) }),
  campaignCreate: (body: Record<string, unknown>) =>
    apiFetch<any>("/api/v1/campaigns", { method: "POST", body: JSON.stringify(body) }),
  campaigns: () => apiFetch<any[]>("/api/v1/campaigns"),
  campaignCancel: (id: number) =>
    apiFetch<any>(`/api/v1/campaigns/${id}/cancel`, { method: "POST" }),

  // --- Modération ---------------------------------------------------------
  moderationQueue: (status = "pending_verification") =>
    apiFetch<any[]>(`/api/v1/moderation/queue?status_filter=${status}`),
  decideReport: (id: number, decision: string, reason: string) =>
    apiFetch<any>(`/api/v1/moderation/reports/${id}/decision`, {
      method: "POST",
      body: JSON.stringify({ decision, reason }),
    }),
  moderationAppeals: () => apiFetch<any[]>("/api/v1/moderation/appeals"),
  decideAppeal: (id: number, decision: string, reason: string) =>
    apiFetch<any>(`/api/v1/moderation/appeals/${id}/decision`, {
      method: "POST",
      body: JSON.stringify({ decision, reason }),
    }),
  confirmSuspension: (targetId: number, confirmed: boolean, evidence: string) =>
    apiFetch<any>(
      `/api/v1/moderation/targets/${targetId}/suspension?confirmed=${confirmed}&evidence=${encodeURIComponent(evidence)}`,
      { method: "POST" },
    ),
  buildDossier: (targetId: number) =>
    apiFetch<any>(`/api/v1/moderation/targets/${targetId}/dossier`, { method: "POST" }),
  moderationDossiers: () => apiFetch<any[]>("/api/v1/moderation/dossiers"),
  // Avertissement (faux signalement) puis sanctions de compte.
  strikeUser: (userId: number, reason: string, reportId?: number) =>
    apiFetch<any>(
      `/api/v1/moderation/users/${userId}/strike?reason=${encodeURIComponent(reason)}` +
        (reportId ? `&report_id=${reportId}` : ""),
      { method: "POST" },
    ),
  sanctionUser: (userId: number, decision: "ban_user" | "suspend_user" | "reinstate_user", reason: string) =>
    apiFetch<any>(`/api/v1/moderation/users/${userId}/sanction`, {
      method: "POST",
      body: JSON.stringify({ decision, reason }),
    }),
  moderationAudit: () => apiFetch<any[]>("/api/v1/moderation/audit?limit=50"),
  moderationStats: () => apiFetch<any>("/api/v1/moderation/stats"),
};
