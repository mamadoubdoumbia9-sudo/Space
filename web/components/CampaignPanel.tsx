"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { NO_GUARANTEE_NOTE } from "@/lib/legal";

// Miroir exact de l'énumération serveur (app/constants.py: InfractionCategory).
const CATEGORIES = [
  { value: "spam", label: "Spam / messages non sollicités" },
  { value: "financial_scam", label: "Arnaque financière (dont hameçonnage par lien)" },
  { value: "impersonation", label: "Usurpation d'identité" },
  { value: "harassment", label: "Harcèlement / menaces" },
  { value: "hate_speech", label: "Discours haineux" },
  { value: "illegal_content", label: "Diffusion de contenu illégal" },
  { value: "other", label: "Autre infraction aux CGU de WhatsApp" },
];

type Row = Record<string, any>;

/**
 * Demande groupée : l'utilisateur indique le numéro cible ET le nombre de
 * signalements souhaité. Le serveur répond ensuite ce qui est RÉELLEMENT
 * exécutable : seuls les comptes distincts ayant réellement reçu des messages de
 * la cible peuvent signaler. Demander 100 signalements ne crée pas 100
 * signalements — l'interface ne doit jamais laisser croire le contraire.
 */
export default function CampaignPanel() {
  const [campaigns, setCampaigns] = useState<Row[]>([]);
  const [targetPhone, setTargetPhone] = useState("");
  const [requestedCount, setRequestedCount] = useState("3");
  const [requestedMax, setRequestedMax] = useState(500);
  const [category, setCategory] = useState("financial_scam");
  const [occurredAt, setOccurredAt] = useState("");
  const [description, setDescription] = useState("");
  const [consent, setConsent] = useState(false);
  const [preview, setPreview] = useState<Row | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const list = await api.campaigns();
      setCampaigns(Array.isArray(list) ? list : []);
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

  const payload = () => ({
    target_phone: targetPhone,
    requested_count: Number(requestedCount),
    category,
    occurred_at: new Date(occurredAt).toISOString(),
    description,
    consent_ack: consent,
  });

  // Aucun chiffre n'est affiché avant d'avoir interrogé le serveur : le nombre
  // exécutable est décidé par les comptes réellement contactés, pas par l'écran.
  const checkFeasibility = () =>
    run(async () => {
      setInfo(null);
      const result = await api.campaignPreview(payload());
      setPreview(result);
      setRequestedMax(Number(result.requested_hard_cap ?? requestedMax));
    });

  const createCampaign = () =>
    run(async () => {
      setInfo(null);
      const campaign = await api.campaignCreate(payload());
      setInfo(
        `Demande ${String(campaign.public_ref)} enregistrée : ${String(campaign.executed_count)} signalement(s) ` +
          `réellement mis en file sur ${String(campaign.requested_count)} demandé(s). ` +
          String(campaign.notes ?? ""),
      );
      await load();
    });

  const cancelCampaign = (id: number) =>
    run(async () => {
      setInfo(null);
      const campaign = await api.campaignCancel(id);
      setInfo(`Demande arrêtée : ${String(campaign.last_error ?? "transmissions retirées de la file.")}`);
      await load();
    });

  const executable = preview ? Number(preview.executable_count ?? 0) : null;

  return (
    <>
      {error && <div className="notice error">{error}</div>}
      {info && <div className="notice ok">{info}</div>}

      <section className="card">
        <h2>Demande groupée pour un même numéro</h2>
        <div className="notice warn">{NO_GUARANTEE_NOTE}</div>
        <p className="muted">
          Vous indiquez le numéro cible et le nombre de signalements souhaité. Le serveur calcule ensuite ce qui
          est <strong>réellement exécutable</strong> : chaque signalement est émis depuis un compte distinct qui a
          véritablement reçu des messages de ce numéro, avec sa propre preuve. Aucun compte n&apos;est créé, aucun
          quota n&apos;est contourné, et le nombre demandé n&apos;est jamais un nombre garanti.
        </p>

        <div className="grid-2">
          <label>
            Numéro à signaler (format international)
            <input
              value={targetPhone}
              onChange={(event) => setTargetPhone(event.target.value)}
              placeholder="+22361234567"
              inputMode="tel"
            />
          </label>
          <label>
            Nombre de signalements souhaité (1 à {requestedMax})
            <input
              value={requestedCount}
              onChange={(event) => setRequestedCount(event.target.value)}
              inputMode="numeric"
            />
          </label>
          <label>
            Catégorie d&apos;infraction
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
            <input
              type="datetime-local"
              value={occurredAt}
              onChange={(event) => setOccurredAt(event.target.value)}
            />
          </label>
        </div>

        <label>
          Description commune des faits (10 à 4000 caractères)
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            placeholder="Décrivez les messages reçus : demandes d'argent, liens, usurpation d'identité…"
          />
        </label>

        <label className="checkbox">
          <input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} />
          Je confirme avoir subi ces messages, je comprends qu&apos;un faux signalement est illégal et passible de
          poursuites, et qu&apos;aucune suspension n&apos;est garantie.
        </label>

        <div className="row">
          <button className="action secondary" onClick={checkFeasibility} disabled={busy || !targetPhone}>
            Vérifier ce qui est réellement possible
          </button>
          <button
            className="action"
            onClick={createCampaign}
            disabled={busy || !consent || !targetPhone || !description || !occurredAt}
          >
            Créer la demande
          </button>
        </div>

        {preview && (
          <div className="notice info">
            <strong>Ce qui est réellement exécutable</strong>
            <ul>
              <li>Demandés : {String(preview.requested_count)}</li>
              <li>Comptes éligibles (réellement contactés, consentants, appareil connecté) : {String(preview.eligible_accounts)}</li>
              <li>
                Signalements réellement exécutables : <strong>{String(executable)}</strong>
              </li>
              <li>Vos appareils connectés : {String(preview.your_connected_devices)}</li>
            </ul>
            <p className="muted">{String(preview.explanation ?? "")}</p>
            {preview.blocking_reason ? <p className="muted">{String(preview.blocking_reason)}</p> : null}
          </div>
        )}
      </section>

      <section className="card">
        <h2>Mes demandes groupées</h2>
        {campaigns.length === 0 && <p className="muted">Aucune demande groupée enregistrée.</p>}
        {campaigns.map((campaign) => (
          <div key={String(campaign.id)} className="notice info">
            <strong>
              {String(campaign.public_ref)} · {String(campaign.target_phone_masked)}
            </strong>
            <p>
              Demandé : {String(campaign.requested_count)} · éligibles : {String(campaign.eligible_count)} · mis en
              file : {String(campaign.executed_count)} · transmis : {String(campaign.succeeded_count)} · état :{" "}
              {String(campaign.status)}
            </p>
            {campaign.notes ? <p className="muted">{String(campaign.notes)}</p> : null}
            {campaign.eligibility_explanation ? (
              <p className="muted">{String(campaign.eligibility_explanation)}</p>
            ) : null}
            {campaign.last_error ? <p className="muted">{String(campaign.last_error)}</p> : null}
            {campaign.status !== "completed" && campaign.status !== "aborted" && (
              <button className="action secondary" onClick={() => cancelCampaign(Number(campaign.id))} disabled={busy}>
                Arrêter cette demande
              </button>
            )}
          </div>
        ))}
        <button className="action secondary" onClick={() => void run(load)} disabled={busy}>
          Actualiser la liste
        </button>
      </section>
    </>
  );
}
