"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { downloadAuthenticated } from "@/lib/download";

// Miroir exact de l'énumération serveur (app/constants.py: InfractionCategory) :
// toute valeur absente de cette liste serait refusée par l'API (HTTP 422).
const CATEGORIES = [
  { value: "spam", label: "Spam / messages non sollicités" },
  { value: "financial_scam", label: "Arnaque financière (dont hameçonnage par lien)" },
  { value: "impersonation", label: "Usurpation d'identité" },
  { value: "harassment", label: "Harcèlement / menaces" },
  { value: "hate_speech", label: "Discours haineux" },
  { value: "illegal_content", label: "Diffusion de contenu illégal" },
  { value: "other", label: "Autre infraction aux CGU de WhatsApp" },
];

const EVIDENCE_KINDS = [
  { value: "screenshot", label: "Capture d'écran (PNG/JPEG/WebP, 12 Mo max)" },
  { value: "chat_export", label: "Export de conversation (TXT/ZIP/JSON)" },
  { value: "message_id", label: "Identifiant de message WhatsApp" },
  { value: "header_dump", label: "En-têtes techniques (JSON/TXT)" },
];

type Row = Record<string, any>;

export default function ReportsPanel() {
  const [reports, setReports] = useState<Row[]>([]);
  const [usage, setUsage] = useState<Row | null>(null);
  const [selected, setSelected] = useState<Row | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // formulaire de création
  const [targetPhone, setTargetPhone] = useState("");
  const [category, setCategory] = useState("financial_scam");
  const [occurredAt, setOccurredAt] = useState("");
  const [description, setDescription] = useState("");
  const [messageIds, setMessageIds] = useState("");
  const [storeMessages, setStoreMessages] = useState(false);
  const [excerpt, setExcerpt] = useState("");
  const [contactProof, setContactProof] = useState<"linked_device_scan" | "manual_declaration">(
    "linked_device_scan",
  );
  const [createdReport, setCreatedReport] = useState<Row | null>(null);
  const [evidenceKind, setEvidenceKind] = useState("screenshot");
  const [preview, setPreview] = useState<Row | null>(null);
  const importInput = useRef<HTMLInputElement>(null);
  const pendingImport = useRef<File | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [list, usageData] = await Promise.all([api.reports(), api.usage()]);
      setReports(list.items ?? []);
      setUsage(usageData);
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

  const createReport = () =>
    run(async () => {
      setInfo(null);
      const payload: Record<string, unknown> = {
        target_phone: targetPhone,
        category,
        occurred_at: new Date(occurredAt).toISOString(),
        description,
        contact_proof_method: contactProof,
        store_messages: storeMessages,
      };
      if (messageIds.trim()) {
        payload.message_ids = messageIds
          .split(",")
          .map((value) => value.trim())
          .filter(Boolean);
      }
      if (storeMessages && excerpt.trim()) payload.message_excerpt = excerpt;
      const report = await api.createReport(payload);
      setCreatedReport(report);
      setInfo(
        "Signalement créé. Il reste en « preuve manquante » tant qu'aucune preuve n'est jointe : sans preuve, " +
          "il n'est jamais transmis.",
      );
      await load();
    });

  const uploadEvidence = (file: File) =>
    run(async () => {
      if (!createdReport) return;
      await api.uploadEvidence(Number(createdReport.id), evidenceKind, file);
      const refreshed = await api.report(Number(createdReport.id));
      setCreatedReport(refreshed);
      setInfo("Preuve enregistrée et vérifiée (format, taille, contenu).");
    });

  const submitReport = (report: Row, adapter: string) =>
    run(async () => {
      const response = await api.submitReport(Number(report.id), adapter, true);
      if (response.queued) {
        setInfo(`Transmission enregistrée (adaptateur ${response.adapter}) : ${response.detail ?? ""}`);
      } else {
        setInfo(
          `Aucun envoi automatique possible : ${response.fallback}. Étapes guidées fournies : ` +
            `${(response.steps ?? []).join(" ")}`,
        );
      }
      await load();
      setSelected(await api.report(Number(report.id)));
    });

  const markManualDone = (report: Row) =>
    run(async () => {
      await api.markManualDone(Number(report.id), "Signalement effectué depuis WhatsApp, capture conservée.");
      setInfo("Marqué comme effectué : le statut reste honnête (aucune confirmation Meta inventée).");
      await load();
    });

  const doImport = (commit: boolean) =>
    run(async () => {
      const file = pendingImport.current ?? importInput.current?.files?.[0];
      if (!file) {
        setError("Choisissez d'abord un fichier CSV ou Excel.");
        return;
      }
      pendingImport.current = file;
      const result = commit ? await api.importCommit(file) : await api.importPreview(file);
      setPreview(result);
      if (commit) {
        setInfo(
          `${result.created_report_ids?.length ?? 0} signalement(s) importé(s) sur ${result.valid_rows} ligne(s) ` +
            `valide(s) ; ${result.rejected_rows} ligne(s) refusée(s) faute de catégorie ou de preuve.`,
        );
        pendingImport.current = null;
        await load();
      } else {
        setInfo(
          `Analyse : ${result.valid_rows} ligne(s) exploitable(s), ${result.rejected_rows} refusée(s). ` +
            "Rien n'a été envoyé tant que vous n'avez pas confirmé l'import.",
        );
      }
    });

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>Nouveau signalement</h2>
        <div className="notice warn">
          Seuls les numéros qui <strong>vous ont réellement contacté</strong> peuvent être signalés (exception :
          modérateurs de groupe, avec justificatif). Le serveur vérifie cette preuve de contact via votre appareil
          lié ; la déclaration manuelle est contrôlée par un modérateur humain.
        </div>
        <div className="grid-2">
          <label>
            Numéro complet au format international
            <input value={targetPhone} onChange={(event) => setTargetPhone(event.target.value)} placeholder="+22365551234" />
          </label>
          <label>
            Catégorie d'infraction
            <select value={category} onChange={(event) => setCategory(event.target.value)}>
              {CATEGORIES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Date et heure de réception
            <input type="datetime-local" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} />
          </label>
          <label>
            Preuve de contact
            <select value={contactProof} onChange={(event) => setContactProof(event.target.value as any)}>
              <option value="linked_device_scan">Vérification via mon appareil lié (recommandé)</option>
              <option value="manual_declaration">Déclaration manuelle (contrôlée par un modérateur)</option>
            </select>
          </label>
        </div>
        <label>
          Description courte (10 caractères minimum, factuelle)
          <textarea value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <div className="grid-2">
          <label>
            Identifiants de message (séparés par des virgules)
            <input value={messageIds} onChange={(event) => setMessageIds(event.target.value)} placeholder="3EB0A1B2C3D4E5F6" />
          </label>
          <label>
            Conserver le texte des messages ? (désactivé par défaut)
            <select value={storeMessages ? "yes" : "no"} onChange={(event) => setStoreMessages(event.target.value === "yes")}>
              <option value="no">Non — aucun texte conservé (recommandé)</option>
              <option value="yes">Oui — je consens explicitement au stockage de l'extrait</option>
            </select>
          </label>
        </div>
        {storeMessages && (
          <label>
            Extrait autorisé (20 000 caractères max)
            <textarea value={excerpt} onChange={(event) => setExcerpt(event.target.value)} />
          </label>
        )}
        <div className="row">
          <button className="action" disabled={busy} onClick={createReport}>
            Créer le signalement
          </button>
          <span className="muted">
            Quota : {String(usage?.used_today ?? "—")} / {String(usage?.daily_limit ?? 20)} aujourd'hui ·{" "}
            {String(usage?.used_this_hour ?? "—")} / {String(usage?.hourly_limit ?? 5)} sur l'heure
          </span>
        </div>

        {createdReport && (
          <div className="notice info">
            <strong>{String(createdReport.public_ref)}</strong> — statut{" "}
            {String(createdReport.status_label ?? createdReport.status)} · preuve de contact :{" "}
            {createdReport.contact_verified ? "vérifiée" : "non vérifiée"}
            <div className="grid-2" style={{ marginTop: 10 }}>
              <label>
                Type de preuve à joindre (obligatoire)
                <select value={evidenceKind} onChange={(event) => setEvidenceKind(event.target.value)}>
                  {EVIDENCE_KINDS.map((kind) => (
                    <option key={kind.value} value={kind.value}>
                      {kind.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Fichier
                <input
                  type="file"
                  disabled={busy}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) void uploadEvidence(file);
                  }}
                />
              </label>
            </div>
            <div className="row" style={{ marginTop: 8 }}>
              <button className="action" disabled={busy} onClick={() => void submitReport(createdReport, "user_native")}>
                Transmettre (appareil lié)
              </button>
              <button className="action secondary" disabled={busy} onClick={() => void submitReport(createdReport, "manual_guided")}>
                Mode guidé WhatsApp
              </button>
            </div>
          </div>
        )}
      </section>

      <section className="card">
        <h2>Import CSV / Excel en masse</h2>
        <p className="muted">
          Toute ligne sans catégorie ou sans preuve est <strong>refusée</strong> et comptée comme telle. Aucune
          ligne incomplète n'est enregistrée. 50 lignes maximum par import.
        </p>
        <div className="row">
          <button
            className="action secondary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await downloadAuthenticated("/api/v1/reports/import/template", "modele-import-signalements.csv");
                setInfo("Modèle CSV téléchargé.");
              })
            }
          >
            Télécharger le modèle CSV
          </button>
        </div>
        <input ref={importInput} type="file" accept=".csv,.xlsx,.xls" />
        <div className="row">
          <button className="action secondary" disabled={busy} onClick={() => void doImport(false)}>
            Analyser sans importer
          </button>
          <button className="action" disabled={busy} onClick={() => void doImport(true)}>
            Importer les lignes valides
          </button>
        </div>
        {preview && (
          <>
            <p className="muted">
              {String(preview.filename)} — {String(preview.total_rows)} ligne(s) lue(s),{" "}
              {String(preview.valid_rows)} valide(s), {String(preview.rejected_rows)} refusée(s).
              {preview.columns_detected ? ` Colonnes détectées : ${JSON.stringify(preview.columns_detected)}` : ""}
            </p>
            <table>
              <thead>
                <tr>
                  <th>Ligne</th>
                  <th>Numéro</th>
                  <th>Catégorie</th>
                  <th>Preuve</th>
                  <th>Valide</th>
                  <th>Motifs de refus</th>
                </tr>
              </thead>
              <tbody>
                {(preview.rows ?? []).slice(0, 50).map((row: Row) => (
                  <tr key={String(row.line)}>
                    <td>{String(row.line)}</td>
                    <td>{String(row.target_phone ?? "—")}</td>
                    <td>{String(row.category ?? "—")}</td>
                    <td>{String(row.evidence_ref ?? "—")}</td>
                    <td>{row.valid ? "oui" : "non"}</td>
                    <td>{(row.errors ?? []).join(" ") || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <section className="card">
        <h2>Mes signalements ({reports.length})</h2>
        {reports.length === 0 && <p className="muted">Aucun signalement pour l'instant.</p>}
        {reports.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Référence</th>
                <th>Numéro</th>
                <th>Catégorie</th>
                <th>Reçu le</th>
                <th>Statut</th>
                <th>Contact</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {reports.map((report) => (
                <tr key={String(report.id)}>
                  <td>{String(report.public_ref)}</td>
                  <td>{String(report.target_phone_masked ?? report.target_phone ?? "—")}</td>
                  <td>{String(report.category_label ?? report.category)}</td>
                  <td>{String(report.occurred_at ?? "—")}</td>
                  <td>
                    <span className="badge">{String(report.status_label ?? report.status)}</span>
                  </td>
                  <td>{report.contact_verified ? "vérifié" : "non vérifié"}</td>
                  <td>
                    <button className="action secondary" disabled={busy} onClick={() => void run(async () => setSelected(await api.report(Number(report.id))))}>
                      Ouvrir
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {selected && (
        <section className="card">
          <h2>
            Détail {String(selected.public_ref)} <span className="badge">{String(selected.status_label ?? selected.status)}</span>
          </h2>
          <p>
            Numéro : <strong>{String(selected.target_phone ?? selected.target_phone_masked)}</strong> · statut de
            la cible : {String(selected.target_status ?? "—")} · suspension :{" "}
            {String(selected.suspension_status ?? "inconnue")} · transmis à Meta :{" "}
            {String(selected.sent_to_meta_at ?? "pas encore")}
          </p>
          <div className="row">
            <button
              className="action secondary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  const text = await api.exportReportText(Number(selected.id));
                  const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
                  const url = URL.createObjectURL(blob);
                  const anchor = document.createElement("a");
                  anchor.href = url;
                  anchor.download = `${String(selected.public_ref)}.txt`;
                  document.body.appendChild(anchor);
                  anchor.click();
                  anchor.remove();
                  URL.revokeObjectURL(url);
                  setInfo("Texte du signalement enregistré : utilisez-le pour le signalement officiel dans WhatsApp.");
                })
              }
            >
              Télécharger le texte du signalement
            </button>
            <button className="action" disabled={busy} onClick={() => void submitReport(selected, "user_native")}>
              Retenter la transmission
            </button>
            <button className="action secondary" disabled={busy} onClick={() => void markManualDone(selected)}>
              J'ai signalé depuis WhatsApp
            </button>
          </div>
          <h3>Preuves ({selected.evidences?.length ?? 0})</h3>
          <ul>
            {(selected.evidences ?? []).map((evidence: Row) => (
              <li key={String(evidence.id)}>
                {String(evidence.kind)} — {String(evidence.filename)} ({Math.round(Number(evidence.size_bytes ?? 0) / 1024)} Ko) ·
                intégrité : {evidence.integrity_ok ? "vérifiée" : "non vérifiée"}
              </li>
            ))}
          </ul>
          <h3>Historique de transmission</h3>
          <ul>
            {(selected.submissions ?? []).map((submission: Row) => (
              <li key={String(submission.id)}>
                {String(submission.adapter)} · {String(submission.status)} · tentative {String(submission.attempts)} ·{" "}
                {String(submission.response_summary ?? submission.error ?? "")}
              </li>
            ))}
          </ul>
          <pre className="content-box">{JSON.stringify(selected, null, 2)}</pre>
        </section>
      )}
    </>
  );
}
