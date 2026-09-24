"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { downloadAuthenticated } from "@/lib/download";

// Filtres alignés sur l'énumération serveur : aucun libellé inventé.
const CATEGORY_FILTERS = [
  { value: "", label: "Toutes catégories" },
  { value: "spam", label: "Spam" },
  { value: "financial_scam", label: "Arnaque financière" },
  { value: "impersonation", label: "Usurpation d'identité" },
  { value: "harassment", label: "Harcèlement" },
  { value: "hate_speech", label: "Discours haineux" },
  { value: "illegal_content", label: "Contenu illégal" },
  { value: "other", label: "Autre" },
];

type Row = Record<string, any>;

export default function CommunityPanel() {
  const [blacklist, setBlacklist] = useState<Row | null>(null);
  const [devices, setDevices] = useState<Row[]>([]);
  const [stats, setStats] = useState<Row | null>(null);
  const [category, setCategory] = useState("");
  const [exportData, setExportData] = useState<Row | null>(null);
  const [deviceId, setDeviceId] = useState<string>("");
  const [consent, setConsent] = useState(false);
  const [maxNumbers, setMaxNumbers] = useState(50);
  const [blockResult, setBlockResult] = useState<Row | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Contestation (le formulaire est aussi utilisable par le propriétaire d'un numéro)
  const [appealPhone, setAppealPhone] = useState("");
  const [appealContact, setAppealContact] = useState("");
  const [appealStatement, setAppealStatement] = useState("");
  const [appealNote, setAppealNote] = useState("");
  const [appealRef, setAppealRef] = useState("");
  const [appealResult, setAppealResult] = useState<Row | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [list, deviceList, communityStats] = await Promise.all([
        api.blacklist(200),
        api.devices(),
        api.communityStats(),
      ]);
      setBlacklist(list);
      setDevices(deviceList);
      setStats(communityStats);
      const connected = deviceList.find((device) => device.status === "connected");
      setDeviceId((current) => current || String(connected?.id ?? ""));
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

  const connectableDevices = devices.filter((device) => device.status === "connected");

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>Liste communautaire des numéros confirmés malveillants</h2>
        <div className="notice info">
          {blacklist?.note ??
            "Cette liste n'est publiée qu'après vérification humaine de plusieurs signalements provenant de personnes distinctes."}
        </div>
        <div className="row">
          <label style={{ maxWidth: 260 }}>
            Filtrer par catégorie
            <select
              value={category}
              onChange={(event) => {
                setCategory(event.target.value);
                void run(async () => {
                  setBlacklist(await api.blacklist(200));
                });
              }}
            >
              {CATEGORY_FILTERS.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <button className="action secondary" disabled={busy} onClick={() => void run(load)}>
            Rafraîchir
          </button>
          <span className="muted">
            {String(blacklist?.total ?? 0)} numéro(s) publié(s) · seuil :{" "}
            {String(blacklist?.min_reports_required ?? 3)} signalements vérifiés par des personnes distinctes
          </span>
        </div>
        {(blacklist?.items ?? []).length === 0 && (
          <p className="muted">Aucun numéro publié pour l'instant (ou filtre trop restrictif).</p>
        )}
        {(blacklist?.items ?? []).length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Numéro</th>
                <th>Catégorie</th>
                <th>Signalements vérifiés</th>
                <th>Personnes distinctes</th>
                <th>Suspension</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {(blacklist?.items ?? []).map((item: Row) => (
                <tr key={`${item.phone_masked}-${item.first_published_at}`}>
                  <td>{String(item.phone_masked)}</td>
                  <td>{String(item.category_label)}</td>
                  <td>{String(item.verified_reports)}</td>
                  <td>{String(item.distinct_reporters)}</td>
                  <td>
                    <span className="badge">{String(item.suspension_status)}</span>
                  </td>
                  <td>{String(item.suspension_source)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="muted">
          Le numéro complet n'est jamais affiché publiquement : il n'est fourni qu'au téléchargement
          authentifié, et uniquement pour vous permettre de bloquer.
        </p>
      </section>

      <section className="card">
        <h2>Bloquer en un clic</h2>
        {connectableDevices.length === 0 ? (
          <div className="notice warn">
            Aucun appareil WhatsApp connecté : le blocage ne peut pas être exécuté réellement. Connectez votre
            appareil dans l'application Android (« Lier mon WhatsApp »), ou utilisez l'export de la liste pour
            bloquer manuellement.
          </div>
        ) : (
          <>
            <div className="grid-2">
              <label>
                Appareil qui exécutera les blocages
                <select value={deviceId} onChange={(event) => setDeviceId(event.target.value)}>
                  {connectableDevices.map((device) => (
                    <option key={String(device.id)} value={String(device.id)}>
                      {String(device.label || device.mode)} — {String(device.masked ?? device.mode)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Nombre maximum de numéros à bloquer (1 à 1000)
                <input
                  type="number"
                  min={1}
                  max={1000}
                  value={maxNumbers}
                  onChange={(event) => setMaxNumbers(Number(event.target.value))}
                />
              </label>
            </div>
            <label>
              <span>
                J'autorise le blocage de ces numéros depuis mon compte WhatsApp. Je comprends que bloquer ne
                déclenche ni ne garantit aucune suspension.
              </span>
              <input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} />
            </label>
            <div className="row">
              <button
                className="action"
                disabled={busy || !consent || !deviceId}
                onClick={() =>
                  void run(async () => {
                    const result = await api.blockAll(Number(deviceId), maxNumbers);
                    setBlockResult(result);
                    setInfo(
                      `Blocages réellement exécutés : ${result.blocked} réussi(s), ${result.failed} échec(s) sur ${result.requested} demandé(s).`,
                    );
                  })
                }
              >
                Bloquer depuis mon compte
              </button>
              <button
                className="action secondary"
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    const data = await api.blacklistExport();
                    setExportData(data);
                    setInfo(
                      `${data.count} numéro(s) récupéré(s) pour votre usage personnel. ${data.disclaimer}`,
                    );
                  })
                }
              >
                Exporter la liste (numéros complets)
              </button>
              {exportData && (
                <button
                  className="action secondary"
                  disabled={busy}
                  onClick={() => {
                    const csv = ["numero,categorie", ...(exportData.numbers ?? []).map((n: string) => `${n},`)]
                      .join("\\n");
                    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
                    const url = URL.createObjectURL(blob);
                    const anchor = document.createElement("a");
                    anchor.href = url;
                    anchor.download = "numeros-malveillants-confirmes.csv";
                    document.body.appendChild(anchor);
                    anchor.click();
                    anchor.remove();
                    URL.revokeObjectURL(url);
                    setInfo(
                      "Liste enregistrée (CSV). Conservez-la hors ligne : nous ne suivons ni ne revendons aucun usage de ces données.",
                    );
                  }}
                >
                  Enregistrer la liste (CSV)
                </button>
              )}
            </div>
          </>
        )}

        {blockResult && (
          <>
            <p className="muted">
              Demandé : {String(blockResult.requested)} · bloqués : {String(blockResult.blocked)} · échecs :{" "}
              {String(blockResult.failed)} · état de l'appareil : {String(blockResult.device_status)}
            </p>
            <table>
              <thead>
                <tr>
                  <th>Numéro</th>
                  <th>Résultat</th>
                  <th>Détail</th>
                </tr>
              </thead>
              <tbody>
                {(blockResult.details ?? []).map((detail: Row, index: number) => (
                  <tr key={`${detail.phone_masked}-${index}`}>
                    <td>{String(detail.phone_masked)}</td>
                    <td>{detail.ok ? "bloqué" : "échec"}</td>
                    <td>{String(detail.detail ?? "—")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <section className="card">
        <h2>Contester un référencement (procédure d'appel)</h2>
        <p className="muted">
          Si votre numéro est listé à tort, déposez une contestation : elle est examinée par un modérateur
          humain. Si les preuves s'avèrent fausses, le numéro est retiré de la liste et les signalements en
          cause rejetés.
        </p>
        <div className="grid-2">
          <label>
            Numéro concerné (format international)
            <input value={appealPhone} onChange={(event) => setAppealPhone(event.target.value)} placeholder="+223…" />
          </label>
          <label>
            Comment vous joindre (email ou téléphone)
            <input value={appealContact} onChange={(event) => setAppealContact(event.target.value)} />
          </label>
        </div>
        <label>
          Exposé (20 caractères minimum)
          <textarea value={appealStatement} onChange={(event) => setAppealStatement(event.target.value)} />
        </label>
        <label>
          Éléments joints (facultatif, décrits en clair)
          <input value={appealNote} onChange={(event) => setAppealNote(event.target.value)} />
        </label>
        <div className="row">
          <button
            className="action"
            disabled={busy || appealStatement.trim().length < 20 || !appealPhone || !appealContact}
            onClick={() =>
              void run(async () => {
                const result = await api.createAppeal({
                  target_phone: appealPhone,
                  claimant_contact: appealContact,
                  statement: appealStatement,
                  evidence_note: appealNote || undefined,
                });
                setAppealResult(result);
                setAppealRef(result.public_ref);
                setInfo(
                  `Contestation enregistrée sous la référence ${result.public_ref}. Conservez-la pour suivre le traitement.`,
                );
              })
            }
          >
            Déposer la contestation
          </button>
        </div>

        <h3>Suivre une contestation</h3>
        <div className="row">
          <label style={{ maxWidth: 320 }}>
            Référence (APL-…)
            <input value={appealRef} onChange={(event) => setAppealRef(event.target.value)} />
          </label>
          <button
            className="action secondary"
            disabled={busy || !appealRef}
            onClick={() =>
              void run(async () => {
                setAppealResult(await api.appealStatus(appealRef.trim()));
              })
            }
          >
            Vérifier l'état
          </button>
        </div>
        {appealResult && (
          <div className="notice info">
            <strong>{String(appealResult.public_ref)}</strong> · numéro {String(appealResult.target_phone_masked)} ·
            état {String(appealResult.status)}
            {appealResult.decided_at ? ` · décision le ${String(appealResult.decided_at)}` : ""}
            {appealResult.decision_reason ? ` · motif : ${String(appealResult.decision_reason)}` : ""}
          </div>
        )}
      </section>

      <section className="card">
        <h2>Transparence du service</h2>
        <p className="muted">
          {String(stats?.honesty_note ?? "Les chiffres proviennent du serveur.")}
        </p>
        <div className="grid-2">
          <div>
            <p>
              Numéros publiés : <strong>{String(stats?.published_targets ?? "—")}</strong>
            </p>
            <p>
              Signalements transmis : <strong>{String(stats?.reports_submitted_total ?? "—")}</strong>
            </p>
          </div>
          <div>
            <p>
              Suspensions confirmées : <strong>{String(stats?.suspensions_confirmed ?? "—")}</strong>
            </p>
            <p className="muted">
              Une suspension n'est comptée comme confirmée que si Meta l'a signalée (webhook) ou si un
              modérateur l'a constatée. Les déclarations d'utilisateurs ne sont jamais comptées ici.
            </p>
          </div>
        </div>
        <pre className="content-box">{JSON.stringify(stats, null, 2)}</pre>
      </section>
    </>
  );
}
