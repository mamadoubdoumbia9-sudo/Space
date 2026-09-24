"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { downloadAuthenticated } from "@/lib/download";

const QUEUE_FILTERS = [
  { value: "pending_verification", label: "En attente de vérification" },
  { value: "pending_evidence", label: "Preuve manquante (jamais transmis)" },
  { value: "verified", label: "Vérifiés" },
  { value: "rejected", label: "Rejetés" },
  { value: "rejected_abusive", label: "Rejetés comme abusifs" },
  { value: "submitted", label: "Transmis à Meta" },
];

const REPORT_DECISIONS = [
  { value: "verify", label: "Valider (preuve conforme)" },
  { value: "reject", label: "Rejeter (non conforme)" },
  { value: "reject_abusive", label: "Rejeter comme abusif (avertissement)" },
];

type Row = Record<string, any>;

export default function ModerationPanel() {
  const [stats, setStats] = useState<Row | null>(null);
  const [filter, setFilter] = useState("pending_verification");
  const [queue, setQueue] = useState<Row[]>([]);
  const [appeals, setAppeals] = useState<Row[]>([]);
  const [dossiers, setDossiers] = useState<Row[]>([]);
  const [audit, setAudit] = useState<Row[]>([]);
  const [selected, setSelected] = useState<Row | null>(null);
  const [decision, setDecision] = useState("verify");
  const [reason, setReason] = useState("");
  const [suspensionEvidence, setSuspensionEvidence] = useState("");
  const [targetId, setTargetId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(
    async (statusFilter = filter) => {
      setError(null);
      try {
        const [dashboardStats, items, appealList, dossierList, auditList] = await Promise.all([
          api.moderationStats(),
          api.moderationQueue(statusFilter),
          api.moderationAppeals(),
          api.moderationDossiers(),
          api.moderationAudit(),
        ]);
        setStats(dashboardStats);
        setQueue(items);
        setAppeals(appealList);
        setDossiers(dossierList);
        setAudit(auditList);
      } catch (err) {
        setError((err as ApiError).message ?? "Chargement impossible.");
      }
    },
    [filter],
  );

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

  const decideReport = (item: Row) =>
    run(async () => {
      const result = await api.decideReport(Number(item.entity_id), decision, reason);
      setInfo(
        `Décision « ${decision} » enregistrée : statut du signalement ${result.status}, cible ${result.target_status}, ` +
          `${result.verified_reports} signalement(s) vérifié(s). ${result.escalation_ready ? "Dossier d'escalade possible." : "Seuil non atteint."}`,
      );
      setReason("");
      setSelected(null);
      await load();
    });

  const confirmSuspension = (confirmed: boolean) =>
    run(async () => {
      const result = await api.confirmSuspension(Number(targetId), confirmed, suspensionEvidence);
      setInfo(
        confirmed
          ? `Suspension enregistrée comme confirmée (source ${result.source}). Elle est désormais comptée honnêtement.`
          : `Suspension non confirmée (${result.status}) : aucune suspension n'est affichée sans preuve.`,
      );
      setSuspensionEvidence("");
      await load();
    });

  const buildDossier = () =>
    run(async () => {
      const result = await api.buildDossier(Number(targetId));
      setInfo(
        `Dossier ${result.dossier_ref} constitué avec ${result.reports} signalement(s) vérifié(s). ` +
          `Empreinte SHA-256 : ${result.pdf_sha256.slice(0, 16)}…`,
      );
      await load();
    });

  const decideAppeal = (appeal: Row, appealDecision: string) =>
    run(async () => {
      const result = await api.decideAppeal(Number(appeal.id), appealDecision, reason);
      setInfo(
        appealDecision === "clear_target"
          ? "Contestation acceptée : le numéro est retiré de la liste publique et les signalements en cause rejetés."
          : `Décision « ${appealDecision} » enregistrée (contestation ${result.appeal_status}).`,
      );
      setReason("");
      await load();
    });

  const strikeReporter = (userId: number) =>
    run(async () => {
      const result = await api.strikeUser(userId, reason);
      setInfo(
        `Avertissement enregistré : ${result.strikes} au total, statut du compte ${result.status}. ` +
          "Le bannissement est automatique au seuil configuré.",
      );
      await load();
    });

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>File de modération</h2>
        <p className="muted">
          Chaque décision exige un motif écrit (5 caractères minimum), journalisé et opposable en cas de
          demande judiciaire. Vérifiez réellement la preuve avant de valider : un signalement validé à tort
          nuit à une personne réelle.
        </p>
        <div className="row">
          <label style={{ maxWidth: 300 }}>
            Filtrer
            <select
              value={filter}
              onChange={(event) => {
                setFilter(event.target.value);
                void load(event.target.value);
              }}
            >
              {QUEUE_FILTERS.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </label>
          <button className="action secondary" disabled={busy} onClick={() => void load()}>
            Rafraîchir
          </button>
          {stats && (
            <span className="muted">
              En attente : {String(stats.reports_pending_verification)} · sans preuve :{" "}
              {String(stats.reports_pending_evidence)} · vérifiés : {String(stats.reports_verified)} · abusifs :{" "}
              {String(stats.reports_rejected_abusive)} · contestations : {String(stats.appeals_open)}
            </span>
          )}
        </div>

        {queue.length === 0 && <p className="muted">Aucun élément dans cette file.</p>}
        {queue.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Référence</th>
                <th>Numéro</th>
                <th>Catégorie</th>
                <th>Preuves</th>
                <th>Contact</th>
                <th>Signaux auto.</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {queue.map((item) => (
                <tr key={`${item.entity_type}-${item.entity_id}`}>
                  <td>{String(item.public_ref)}</td>
                  <td>{String(item.target_phone_masked)}</td>
                  <td>{String(item.category)}</td>
                  <td>{String(item.evidences_count)}</td>
                  <td>{String(item.contact_proof_method ?? "—")}</td>
                  <td>{(item.auto_flags ?? []).join(", ") || "—"}</td>
                  <td>
                    <button
                      className="action secondary"
                      disabled={busy}
                      onClick={() => {
                        setSelected(item);
                        setTargetId(String(item.target_id ?? ""));
                        setDecision("verify");
                        setReason("");
                      }}
                    >
                      Examiner
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {selected && (
          <div className="notice info">
            <h3>
              {String(selected.public_ref)} — {String(selected.target_phone_masked)}
            </h3>
            <p>
              Catégorie : {String(selected.category)} · reçu le {String(selected.occurred_at ?? "—")} · source{" "}
              {String(selected.source ?? "—")} · cible #{String(selected.target_id)} ({String(selected.target_status)},
              suspension {String(selected.suspension_status)}) · auteur #{String(selected.reporter_id)} (
              {String(selected.reporter_strikes ?? 0)} avertissement(s))
            </p>
            <p>
              <strong>Description fournie par l'auteur :</strong> {String(selected.description ?? selected.summary)}
            </p>
            <h3>Preuves à contrôler ({String(selected.evidences_count)})</h3>
            {(selected.evidences ?? []).length === 0 ? (
              <p className="muted">
                Aucune preuve : ce signalement ne peut pas être validé et ne sera jamais transmis.
              </p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Fichier</th>
                    <th>Taille</th>
                    <th>Empreinte</th>
                    <th>Intégrité</th>
                    <th>Identifiants</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {(selected.evidences ?? []).map((evidence: Row) => (
                    <tr key={String(evidence.id)}>
                      <td>{String(evidence.kind)}</td>
                      <td>{String(evidence.filename)}</td>
                      <td>{Math.round(Number(evidence.size_bytes) / 1024)} Ko</td>
                      <td>{String(evidence.sha256 ?? "—")}</td>
                      <td>{evidence.integrity_ok ? "vérifiée" : "à vérifier"}</td>
                      <td>{(evidence.message_ids ?? []).length}</td>
                      <td>
                        <button
                          className="action secondary"
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await downloadAuthenticated(
                                `/api/v1/reports/evidence/${evidence.id}/download`,
                                `preuve-${evidence.id}-${evidence.filename}`,
                              );
                              setInfo("Preuve téléchargée et déchiffrée pour contrôle humain.");
                            })
                          }
                        >
                          Ouvrir
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            <div className="grid-2" style={{ marginTop: 10 }}>
              <label>
                Décision sur le signalement
                <select value={decision} onChange={(event) => setDecision(event.target.value)}>
                  {REPORT_DECISIONS.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Motif écrit (obligatoire, 5 caractères minimum)
                <input value={reason} onChange={(event) => setReason(event.target.value)} />
              </label>
            </div>
            <div className="row">
              <button className="action" disabled={busy || reason.trim().length < 5} onClick={() => decideReport(selected)}>
                Enregistrer la décision
              </button>
              <button
                className="action secondary"
                disabled={busy || reason.trim().length < 5 || !selected.reporter_id}
                onClick={() => strikeReporter(Number(selected.reporter_id))}
              >
                Avertir l'auteur (faux signalement)
              </button>
            </div>
          </div>
        )}
      </section>

      <section className="card">
        <h2>Suspension d'un numéro (honnêteté des statuts)</h2>
        <p className="muted">
          Un statut « suspendu » n'est enregistré que si Meta l'a signalé ou si vous l'avez constaté vous-même.
          Dans tous les autres cas, répondez « non confirmée » : l'application ne doit jamais afficher une
          suspension inventée.
        </p>
        <div className="grid-2">
          <label>
            Identifiant de la cible (target_id)
            <input value={targetId} onChange={(event) => setTargetId(event.target.value)} inputMode="numeric" />
          </label>
          <label>
            Preuve de la suspension (ce que vous avez constaté, référence)
            <input value={suspensionEvidence} onChange={(event) => setSuspensionEvidence(event.target.value)} />
          </label>
        </div>
        <div className="row">
          <button className="action" disabled={busy || !targetId || suspensionEvidence.trim().length < 5} onClick={() => confirmSuspension(true)}>
            Confirmer la suspension
          </button>
          <button className="action secondary" disabled={busy || !targetId || suspensionEvidence.trim().length < 5} onClick={() => confirmSuspension(false)}>
            Non confirmée
          </button>
          <button className="action secondary" disabled={busy || !targetId} onClick={buildDossier}>
            Constituer le dossier PDF
          </button>
        </div>
      </section>

      <section className="card">
        <h2>Contestations à examiner</h2>
        {appeals.length === 0 && <p className="muted">Aucune contestation en attente.</p>}
        {appeals.map((appeal) => (
          <div className="notice warn" key={String(appeal.id)}>
            <strong>{String(appeal.public_ref)}</strong> — numéro {String(appeal.target_phone_masked)} · demandeur{" "}
            {String(appeal.claimant_contact)} · déposée le {String(appeal.created_at)}
            <p>{String(appeal.statement)}</p>
            {appeal.evidence_note && <p className="muted">Éléments annoncés : {String(appeal.evidence_note)}</p>}
            <div className="row">
              <button className="action" disabled={busy || reason.trim().length < 5} onClick={() => decideAppeal(appeal, "verify")}>
                Maintenir le référencement (contestation refusée)
              </button>
              <button
                className="action danger"
                disabled={busy || reason.trim().length < 5}
                onClick={() => decideAppeal(appeal, "clear_target")}
              >
                Retirer le numéro (preuves jugées fausses)
              </button>
              <button className="action secondary" disabled={busy || reason.trim().length < 5} onClick={() => decideAppeal(appeal, "reject")}>
                Rejeter la contestation
              </button>
            </div>
            {reason.trim().length < 5 && (
              <p className="muted">Renseignez le motif ci-dessus (champ « Motif écrit ») pour décider.</p>
            )}
          </div>
        ))}
      </section>

      <section className="card">
        <h2>Dossiers constitués</h2>
        {dossiers.length === 0 && <p className="muted">Aucun dossier pour l'instant.</p>}
        {dossiers.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Référence</th>
                <th>Cible</th>
                <th>Signalements</th>
                <th>Personnes</th>
                <th>État d'envoi</th>
                <th>Généré le</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {dossiers.map((dossier) => (
                <tr key={String(dossier.public_ref)}>
                  <td>{String(dossier.public_ref)}</td>
                  <td>#{String(dossier.target_id)}</td>
                  <td>{String(dossier.reports)}</td>
                  <td>{String(dossier.reporters)}</td>
                  <td>{String(dossier.dispatch_status)}</td>
                  <td>{String(dossier.generated_at)}</td>
                  <td>
                    <button
                      className="action secondary"
                      disabled={busy}
                      onClick={() =>
                        void run(async () => {
                          await downloadAuthenticated(
                            `/api/v1/moderation/dossiers/${dossier.public_ref}/download`,
                            `${dossier.public_ref}.pdf`,
                          );
                          setInfo("Dossier PDF téléchargé (intégrité vérifiée avant remise).");
                        })
                      }
                    >
                      Télécharger
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="card">
        <h2>Journal d'audit (50 dernières actions)</h2>
        <p className="muted">
          Ce journal est conservé pour répondre aux demandes judiciaires : qui a fait quoi, quand, et depuis
          quelle adresse.
        </p>
        <table>
          <thead>
            <tr>
              <th>Date</th>
              <th>Action</th>
              <th>Auteur</th>
              <th>Rôle</th>
              <th>Entité</th>
              <th>Détail</th>
            </tr>
          </thead>
          <tbody>
            {audit.map((entry) => (
              <tr key={String(entry.id)}>
                <td>{String(entry.created_at)}</td>
                <td>{String(entry.action)}</td>
                <td>{String(entry.actor_user_id ?? "—")}</td>
                <td>{String(entry.actor_role ?? "—")}</td>
                <td>
                  {String(entry.entity_type ?? "—")} #{String(entry.entity_id ?? "—")}
                </td>
                <td>{String(entry.detail ?? "—").slice(0, 120)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  );
}
