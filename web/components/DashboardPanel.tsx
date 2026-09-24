"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { downloadAuthenticated } from "@/lib/download";

type Usage = {
  reports_last_hour?: number;
  reports_last_day?: number;
  hourly_limit?: number;
  daily_limit?: number;
  remaining_today?: number;
  strikes?: number;
  status?: string;
  ban_threshold?: number;
};

type Overview = {
  usage?: Usage;
  reports?: {
    total?: number;
    pending_evidence?: number;
    pending_verification?: number;
    verified?: number;
    submitted?: number;
    rejected?: number;
  };
  devices?: Array<Record<string, unknown>>;
  suspensions?: { confirmed_from_my_reports?: number; note?: string };
  unread_notifications?: number;
};

export default function DashboardPanel({
  me,
  onRefreshProfile,
}: {
  me: any;
  onRefreshProfile: () => Promise<void>;
}) {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [notifications, setNotifications] = useState<any[]>([]);
  const [alerts, setAlerts] = useState<any[]>([]);
  const [devices, setDevices] = useState<any[]>([]);
  const [gateway, setGateway] = useState<any>(null);
  const [business, setBusiness] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [suspensionPhone, setSuspensionPhone] = useState("");
  const [suspensionNote, setSuspensionNote] = useState("");

  const load = useCallback(async () => {
    setError(null);
    try {
      const [data, notifs, alertList, deviceList, gatewayStatus, businessStatus] = await Promise.all([
        api.overview(),
        api.notifications(),
        api.alerts(),
        api.devices(),
        api.gatewayStatus(),
        api.businessStatus().catch(() => null),
      ]);
      setOverview(data);
      setNotifications(notifs);
      setAlerts(alertList);
      setDevices(deviceList);
      setGateway(gatewayStatus);
      setBusiness(businessStatus);
    } catch (err) {
      setError((err as ApiError).message ?? "Chargement impossible.");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<void>, message?: string) {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      await action();
      if (message) setInfo(message);
    } catch (err) {
      setError((err as ApiError).message ?? "Opération impossible.");
    } finally {
      setBusy(false);
    }
  }

  const usage = overview?.usage ?? {};
  const suspensions = overview?.suspensions ?? {};
  const stats = overview?.reports ?? {};

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>Où j'en suis</h2>
        <div className="grid-2">
          <div>
            <p>
              Signalements envoyés aujourd'hui : <strong>{String(usage.reports_last_day ?? "—")}</strong> /{" "}
              {String(usage.daily_limit ?? 20)} — reste {String(usage.remaining_today ?? "—")}
            </p>
            <p>
              Sur la dernière heure : <strong>{String(usage.reports_last_hour ?? "—")}</strong> /{" "}
              {String(usage.hourly_limit ?? 5)}
            </p>
            <p className="muted">
              Avertissements reçus : {String(usage.strikes ?? 0)} — bannissement définitif au-delà de{" "}
              {String(usage.ban_threshold ?? 2)} signalement(s) jugé(s) abusif(s) après vérification.
            </p>
          </div>
          <div>
            <p>
              Comptes confirmés suspendus grâce à mes signalements :{" "}
              <strong>{String(suspensions.confirmed_from_my_reports ?? 0)}</strong>
            </p>
            <p className="muted">
              {suspensions.note ??
                "Ce chiffre ne compte que les suspensions confirmées, jamais supposées."}
            </p>
          </div>
        </div>
        <div className="row">
          <button className="action secondary" onClick={() => void run(load)} disabled={busy}>
            Rafraîchir
          </button>
          <button
            className="action secondary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await downloadAuthenticated("/api/v1/dashboard/export/reports", "mes-signalements.csv");
                setInfo("Export CSV téléchargé.");
              })
            }
          >
            Exporter mes signalements (CSV)
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Compte, vérification et appareils</h2>
        <p>
          Email : <strong>{String(me.email ?? "—")}</strong> · téléphone{" "}
          <strong>{String(me.phone ?? "—")}</strong> · vérifié :{" "}
          <strong>{me.is_verified ? "oui" : "non"}</strong>
        </p>
        <p className="muted">
          La vérification par email <em>et</em> téléphone est obligatoire et ne peut pas être désactivée : elle
          empêche la création de comptes jetables pour des campagnes de faux signalements.
        </p>
        <div className="grid-2">
          <div>
            <h3>Passerelle WhatsApp locale</h3>
            {gateway ? (
              <p className="muted">
                {gateway.configured
                  ? "Configurée : l'appareil lié peut synchroniser contacts et conversations."
                  : "Non configurée : les signalements restent possibles, mais la vérification automatique de contact est indisponible."}
              </p>
            ) : (
              <p className="muted">Inconnu.</p>
            )}
          </div>
          <div>
            <h3>WhatsApp Business Platform</h3>
            {business ? (
              <p className="muted">
                {business.linked
                  ? business.live_check
                    ? `Lié et vérifié : ${JSON.stringify(business.account)}`
                    : `Lié (${business.reason ?? "vérification en attente"})`
                  : "Non lié : le mode API Cloud reste indisponible."}
              </p>
            ) : (
              <p className="muted">
                Non lié. Le mode officiel WhatsApp Business (Cloud API) exige un compte business ; il ne
                concerne que les entreprises qui reçoivent réellement ces messages.
              </p>
            )}
          </div>
        </div>
        <div>
          <h3>Appareils liés</h3>
          {devices.length === 0 && <p className="muted">Aucun appareil lié.</p>}
          {devices.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Appareil</th>
                  <th>Mode</th>
                  <th>Statut</th>
                  <th>Numéro</th>
                  <th>Dernier contact</th>
                </tr>
              </thead>
              <tbody>
                {devices.map((device) => (
                  <tr key={String(device.id)}>
                    <td>{String(device.label ?? "—")}</td>
                    <td>{String(device.mode ?? "—")}</td>
                    <td>
                      <span className="badge">{String(device.status ?? "—")}</span>
                    </td>
                    <td>{String(device.masked ?? "—")}</td>
                    <td>{String(device.last_seen_at ?? "—")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      <section className="card">
        <h2>Alerte : un numéro malveillant connu m'a contacté</h2>
        <p className="muted">
          Le serveur compare les conversations synchronisées avec la liste communautaire des numéros
          confirmés malveillants (au moins 3 signalements vérifiés). Toute correspondance déclenche une
          alerte ici et une notification.
        </p>
        <div className="row">
          <button
            className="action"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                const result = await api.scanAlerts();
                await load();
                setInfo(
                  Number(result?.matches ?? 0) > 0
                    ? `${result.matches} numéro(s) malveillant(s) détecté(s) dans vos conversations.`
                    : "Aucune nouvelle correspondance détectée.",
                );
              })
            }
          >
            Analyser mes conversations
          </button>
          <button className="action secondary" disabled={busy || notifications.length === 0} onClick={() => void run(async () => { await api.markNotificationsRead(); await load(); })}>
            Marquer les notifications comme lues
          </button>
        </div>
        {alerts.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Numéro masqué</th>
                <th>Motif</th>
                <th>Signalements vérifiés</th>
                <th>Conseil</th>
              </tr>
            </thead>
            <tbody>
              {alerts.map((alert) => (
                <tr key={String(alert.target_id)}>
                  <td>{String(alert.phone_masked ?? "—")}</td>
                  <td>{String(alert.category_label ?? "—")}</td>
                  <td>{String(alert.verified_reports ?? 0)}</td>
                  <td>{String(alert.advice ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {notifications.length > 0 && (
          <>
            <h3>Notifications</h3>
            <ul>
              {notifications.slice(0, 10).map((notification) => (
                <li key={String(notification.id)}>
                  {String(notification.title ?? notification.kind ?? "notification")} —{" "}
                  {String(notification.body ?? "—")}{" "}
                  {notification.read_at ? (
                    <span className="badge">lu</span>
                  ) : (
                    <span className="badge">nouveau</span>
                  )}
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section className="card">
        <h2>Un numéro que j'avais signalé a été suspendu</h2>
        <p className="muted">
          Si WhatsApp vous a informé qu'un numéro a été suspendu, vous pouvez le déclarer. Le statut est
          enregistré comme déclaration d'utilisateur (jamais comme confirmation Meta) et vérifié par un
          modérateur humain avant d'être comptabilisé.
        </p>
        <div className="grid-2">
          <label>
            Numéro concerné
            <input value={suspensionPhone} onChange={(event) => setSuspensionPhone(event.target.value)} placeholder="+223…" />
          </label>
          <label>
            Ce que WhatsApp m'a indiqué
            <input value={suspensionNote} onChange={(event) => setSuspensionNote(event.target.value)} />
          </label>
        </div>
        <div className="row">
          <button
            className="action"
            disabled={busy || !suspensionPhone || suspensionNote.trim().length < 5}
            onClick={() =>
              void run(async () => {
                await api.reportSuspension(suspensionPhone, suspensionNote);
                setSuspensionPhone("");
                setSuspensionNote("");
                setInfo("Déclaration enregistrée : un modérateur la vérifiera avant tout affichage.");
              }, "Déclaration enregistrée.")
            }
          >
            Enregistrer la déclaration
          </button>
          <button className="action secondary" disabled={busy} onClick={() => void onRefreshProfile()}>
            Rafraîchir mon profil
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Chiffres publics du service</h2>
        <p className="muted">
          Ces statistiques proviennent du serveur et ne sont pas mises en avant pour vous inciter à signaler :
          elles servent à rendre transparent le taux de vérification.
        </p>
        <pre className="content-box">{JSON.stringify(stats, null, 2)}</pre>
      </section>
    </>
  );
}
