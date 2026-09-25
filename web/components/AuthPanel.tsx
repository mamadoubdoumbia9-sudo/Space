"use client";

import { useState } from "react";
import { api, ApiError, setTokens } from "@/lib/api";

const PHONE_HINT = "+223 61 23 45 67";

export default function AuthPanel({ onAuthenticated }: { onAuthenticated: () => Promise<void> }) {
  const [mode, setMode] = useState<"login" | "register" | "verify">("login");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("+223");
  const [password, setPassword] = useState("");
  const [channel, setChannel] = useState<"email" | "sms">("email");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [devCode, setDevCode] = useState<string | null>(null);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      const apiError = err as ApiError;
      setError(apiError?.message ?? "Erreur inattendue.");
    } finally {
      setBusy(false);
    }
  }

  const register = () =>
    run(async () => {
      const response = await api.register({
        email,
        phone,
        password,
        channel,
        accept_terms: true,
        accept_privacy: true,
      });
      setInfo(
        response.delivery_detail ??
          "Compte créé. Saisissez le code reçu pour activer l'accès aux signalements.",
      );
      if (response.dev_code) setDevCode(response.dev_code);
      setMode("verify");
    });

  const verify = () =>
    run(async () => {
      const tokens = await api.verify(email, code);
      setTokens(tokens.access_token, tokens.refresh_token);
      await onAuthenticated();
    });

  const login = () =>
    run(async () => {
      const tokens = await api.login(email, password);
      setTokens(tokens.access_token, tokens.refresh_token);
      await onAuthenticated();
    });

  return (
    <>
      <section className="card">
        <h2>À lire avant tout signalement</h2>
        <div className="notice warn">
          Un faux signalement est <strong>passible de poursuites judiciaires</strong> et entraîne le
          bannissement définitif de votre compte SignalPro (au-delà de 2 signalements jugés abusifs après
          vérification). SignalPro <strong>ne peut pas suspendre un compte WhatsApp</strong> : seul Meta
          examine les signalements et décide. Aucun statut « suspendu » n'est affiché sans confirmation.
        </div>
        <p className="muted">
          Tous les comptes sont vérifiés par email <em>et</em> téléphone : c'est la première protection contre
          les faux signalements en série. Les limites (5 signalements/heure, 20/jour, 10 actions/minute) sont
          appliquées par le serveur et ne peuvent pas être désactivées.
        </p>
      </section>

      <section className="card">
        <h2>
          {mode === "login" ? "Connexion" : mode === "register" ? "Créer un compte" : "Vérification obligatoire"}
        </h2>

        {error && <div className="notice error">{error}</div>}
        {info && <div className="notice info">{info}</div>}
        {devCode && (
          <div className="notice warn">
            Mode développement : code de vérification <strong>{devCode}</strong> (en production, il est envoyé
            par email ou SMS et n'est jamais renvoyé par l'API).
          </div>
        )}

        {mode !== "verify" && (
          <div className="grid-2">
            <label>
              Adresse email
              <input value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" />
            </label>
            <label>
              Mot de passe (10 caractères minimum)
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete={mode === "login" ? "current-password" : "new-password"}
              />
            </label>
            {mode === "register" && (
              <>
                <label>
                  Numéro de téléphone (format international)
                  <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder={PHONE_HINT} />
                </label>
                <label>
                  Recevoir le code par
                  <select value={channel} onChange={(event) => setChannel(event.target.value as "email" | "sms")}>
                    <option value="email">Email</option>
                    <option value="sms">SMS</option>
                  </select>
                </label>
              </>
            )}
          </div>
        )}

        {mode === "verify" && (
          <div className="grid-2">
            <label>
              Email du compte
              <input value={email} onChange={(event) => setEmail(event.target.value)} />
            </label>
            <label>
              Code à 6 chiffres
              <input value={code} onChange={(event) => setCode(event.target.value)} inputMode="numeric" />
            </label>
          </div>
        )}

        <div className="row">
          {mode === "login" && (
            <>
              <button className="action" onClick={login} disabled={busy}>
                Se connecter
              </button>
              <button className="action secondary" onClick={() => setMode("register")} disabled={busy}>
                Créer un compte
              </button>
            </>
          )}
          {mode === "register" && (
            <>
              <button className="action" onClick={register} disabled={busy}>
                Créer mon compte
              </button>
              <button className="action secondary" onClick={() => setMode("login")} disabled={busy}>
                J'ai déjà un compte
              </button>
            </>
          )}
          {mode === "verify" && (
            <>
              <button className="action" onClick={verify} disabled={busy || !code}>
                Vérifier
              </button>
              <button className="action secondary" onClick={() => setMode("login")} disabled={busy}>
                Se connecter autrement
              </button>
            </>
          )}
        </div>
      </section>
    </>
  );
}
