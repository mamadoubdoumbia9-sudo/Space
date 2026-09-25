"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError, clearTokens } from "@/lib/api";

const CONSENT_KINDS = [
  { value: "terms", label: "Conditions d'utilisation" },
  { value: "privacy", label: "Politique de confidentialité" },
  { value: "wa_web_risk", label: "Risque lié à la liaison WhatsApp (WhatsApp Web)" },
  { value: "message_storage", label: "Stockage du texte des messages (facultatif)" },
];

type Row = Record<string, any>;

export default function SettingsPanel({ me, onChanged }: { me: any; onChanged: () => Promise<void> }) {
  const [limits, setLimits] = useState<Row | null>(null);
  const [consents, setConsents] = useState<Row[]>([]);
  const [devices, setDevices] = useState<Row[]>([]);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [deletePassword, setDeletePassword] = useState("");
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [wabaId, setWabaId] = useState("");
  const [phoneNumberId, setPhoneNumberId] = useState("");
  const [businessToken, setBusinessToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [limitData, consentList, deviceList] = await Promise.all([
        api.limits(),
        api.consents(),
        api.devices(),
      ]);
      setLimits(limitData);
      setConsents(consentList);
      setDevices(deviceList);
    } catch (err) {
      setError((err as ApiError).message ?? "Chargement impossible.");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError((err as ApiError).message ?? "Opération impossible.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>Mon compte</h2>
        <p>
          Email : <strong>{String(me.email ?? "—")}</strong> · téléphone{" "}
          <strong>{String(me.phone ?? "—")}</strong> · rôle {String(me.role)} · statut{" "}
          <strong>{String(me.status)}</strong> · avertissements {String(me.strikes ?? 0)}
        </p>
        <p className="muted">
          Tant que votre compte n'est pas vérifié (email et téléphone), l'accès aux signalements reste bloqué :
          cette règle est appliquée par le serveur et ne peut pas être contournée.
        </p>
        <button className="action secondary" disabled={busy} onClick={() => void onChanged()}>
          Rafraîchir mon profil
        </button>
      </section>

      <section className="card">
        <h2>Limites anti-abus en vigueur</h2>
        {limits ? (
          <ul>
            <li>Signalements par heure : {String(limits.max_reports_per_hour_user)}</li>
            <li>Signalements par jour : {String(limits.max_reports_per_day_user)}</li>
            <li>Actions par minute : {String(limits.max_actions_per_minute_user)}</li>
            <li>Avertissements avant bannissement définitif : {String(limits.max_abusive_strikes)}</li>
          </ul>
        ) : (
          <p className="muted">Chargement…</p>
        )}
        <p className="muted">
          Ces plafonds sont intentionnellement non désactivables : ils empêchent les campagnes de faux
          signalements. Un signalement sans preuve n'est jamais transmis, et un faux signalement confirmé
          entraîne un avertissement, puis le bannissement définitif.
        </p>
        <pre className="content-box">{JSON.stringify(limits, null, 2)}</pre>
      </section>

      <section className="card">
        <h2>Mes consentements</h2>
        <p className="muted">
          Chaque acceptation est horodatée et conservée : c'est votre preuve et la nôtre. Le stockage du texte
          des messages est refusé par défaut et n'est activé que si vous l'acceptez explicitement.
        </p>
        <table>
          <thead>
            <tr>
              <th>Objet</th>
              <th>Version</th>
              <th>Accepté</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            {consents.map((consent, index) => (
              <tr key={`${consent.kind}-${index}`}>
                <td>{String(consent.kind)}</td>
                <td>{String(consent.version)}</td>
                <td>{consent.accepted ? "oui" : "refusé"}</td>
                <td>{String(consent.accepted_at ?? "—")}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="row">
          {CONSENT_KINDS.map((kind) => (
            <button
              key={kind.value}
              className="action secondary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api.recordConsent(kind.value, true);
                  await load();
                  setInfo(`Consentement « ${kind.label} » enregistré.`);
                })
              }
            >
              Accepter : {kind.label}
            </button>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>Appareils liés</h2>
        {devices.length === 0 && (
          <p className="muted">
            Aucun appareil lié. La liaison se fait depuis l'application Android (scan du QR affiché par
            WhatsApp) : les identifiants de session restent sur votre appareil et sur la passerelle locale,
            jamais dans le navigateur.
          </p>
        )}
        {devices.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Appareil</th>
                <th>Mode</th>
                <th>Statut</th>
                <th>Numéro</th>
                <th>Contacts</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {devices.map((device) => (
                <tr key={String(device.id)}>
                  <td>{String(device.label ?? "—")}</td>
                  <td>{String(device.mode)}</td>
                  <td>
                    <span className="badge">{String(device.status)}</span>
                  </td>
                  <td>{String(device.masked ?? "—")}</td>
                  <td>{String(device.contacts_count ?? 0)}</td>
                  <td>
                    <div className="row">
                      <button
                        className="action secondary"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            const result = await api.syncDevice(Number(device.id));
                            setInfo(`Synchronisation effectuée : ${JSON.stringify(result).slice(0, 160)}`);
                            await load();
                          })
                        }
                      >
                        Synchroniser
                      </button>
                      <button
                        className="action danger"
                        disabled={busy}
                        onClick={() =>
                          void run(async () => {
                            await api.revokeDevice(Number(device.id));
                            setInfo(
                              "Appareil révoqué : la session est supprimée côté passerelle et côté WhatsApp.",
                            );
                            await load();
                          })
                        }
                      >
                        Révoquer
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>WhatsApp Business Platform (entreprises uniquement)</h2>
        <p className="muted">
          Le mode officiel ne s'applique qu'aux entreprises qui reçoivent réellement ces messages sur un numéro
          professionnel. Il ne permet pas de signaler un utilisateur : WhatsApp n'expose aucune API de
          signalement. Le jeton est stocké chiffré et n'est jamais renvoyé.
        </p>
        <div className="grid-2">
          <label>
            Identifiant WABA
            <input value={wabaId} onChange={(event) => setWabaId(event.target.value)} />
          </label>
          <label>
            Identifiant du numéro (phone_number_id)
            <input value={phoneNumberId} onChange={(event) => setPhoneNumberId(event.target.value)} />
          </label>
        </div>
        <label>
          Jeton système (permission whatsapp_business_messaging)
          <input
            type="password"
            value={businessToken}
            onChange={(event) => setBusinessToken(event.target.value)}
            autoComplete="off"
          />
        </label>
        <div className="row">
          <button
            className="action"
            disabled={busy || !wabaId || !phoneNumberId || businessToken.length < 20}
            onClick={() =>
              void run(async () => {
                const result = await api.linkBusiness({
                  waba_id: wabaId,
                  phone_number_id: phoneNumberId,
                  access_token: businessToken,
                });
                setBusinessToken("");
                setInfo(`Compte business lié : ${JSON.stringify(result).slice(0, 200)}`);
              })
            }
          >
            Lier le compte business
          </button>
          <button
            className="action secondary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                const status = await api.businessStatus();
                setInfo(`État business : ${JSON.stringify(status)}`);
              })
            }
          >
            Vérifier l'état
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Mot de passe</h2>
        <div className="grid-2">
          <label>
            Mot de passe actuel
            <input
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              autoComplete="current-password"
            />
          </label>
          <label>
            Nouveau mot de passe (10 caractères minimum, lettres et chiffres)
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              autoComplete="new-password"
            />
          </label>
        </div>
        <button
          className="action"
          disabled={busy || newPassword.length < 10 || !currentPassword}
          onClick={() =>
            void run(async () => {
              await api.changePassword(currentPassword, newPassword);
              setCurrentPassword("");
              setNewPassword("");
              setInfo("Mot de passe modifié. Les sessions existantes restent valides jusqu'à expiration du jeton.");
            })
          }
        >
          Changer le mot de passe
        </button>
      </section>

      <section className="card">
        <h2>Supprimer mon compte et mes données</h2>
        <div className="notice warn">
          Suppression immédiate et définitive : compte, signalements, preuves chiffrées, appareils liés et
          historique. Nous ne conservons aucune copie, et nous ne vendons ni ne partageons jamais vos données
          ni vos listes de numéros. Seuls les journaux d'audit strictement nécessaires à une éventuelle
          demande judiciaire sont conservés sous forme pseudonymisée.
        </div>
        <div className="grid-2">
          <label>
            Mot de passe
            <input
              type="password"
              value={deletePassword}
              onChange={(event) => setDeletePassword(event.target.value)}
              autoComplete="current-password"
            />
          </label>
          <label>
            Écrivez SUPPRIMER pour confirmer
            <input value={deleteConfirm} onChange={(event) => setDeleteConfirm(event.target.value)} />
          </label>
        </div>
        <button
          className="action danger"
          disabled={busy || deleteConfirm !== "SUPPRIMER" || !deletePassword}
          onClick={() =>
            void run(async () => {
              const result = await api.deleteAccount(deletePassword, deleteConfirm);
              clearTokens();
              setDeletePassword("");
              setDeleteConfirm("");
              setInfo(`Compte supprimé : ${JSON.stringify(result).slice(0, 200)}`);
              window.location.reload();
            })
          }
        >
          Supprimer définitivement mon compte
        </button>
      </section>
    </>
  );
}
