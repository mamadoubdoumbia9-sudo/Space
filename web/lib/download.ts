"use client";

/**
 * Téléchargement de fichiers protégés.
 *
 * Un lien `<a href="/proxy/...">` n'enverrait aucun en-tête `Authorization` et le
 * serveur répondrait 401. On récupère donc le contenu via `fetch` avec le jeton,
 * puis on déclenche l'enregistrement local du fichier.
 */
export async function downloadAuthenticated(path: string, filename: string): Promise<void> {
  const token = window.localStorage.getItem("signalpro.access_token") ?? "";
  const response = await fetch(`/proxy${path.startsWith("/") ? path : `/${path}`}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    let message = `Téléchargement refusé (HTTP ${response.status}).`;
    try {
      const payload = (await response.json()) as { detail?: string };
      if (payload?.detail) message = payload.detail;
    } catch {
      /* corps non JSON */
    }
    throw new Error(message);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
