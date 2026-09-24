"use client";

import { useCallback, useEffect, useState } from "react";
import AuthPanel from "@/components/AuthPanel";
import DashboardPanel from "@/components/DashboardPanel";
import ReportsPanel from "@/components/ReportsPanel";
import CommunityPanel from "@/components/CommunityPanel";
import ModerationPanel from "@/components/ModerationPanel";
import SettingsPanel from "@/components/SettingsPanel";
import { api, clearTokens, getToken } from "@/lib/api";
import { DISCLAIMER, NO_GUARANTEE_NOTE } from "@/lib/legal";

type Tab = "dashboard" | "reports" | "community" | "moderation" | "settings";

export default function Home() {
  const [ready, setReady] = useState(false);
  const [me, setMe] = useState<any>(null);
  const [tab, setTab] = useState<Tab>("dashboard");

  const loadProfile = useCallback(async () => {
    if (!getToken()) {
      setMe(null);
      return;
    }
    try {
      const profile = await api.me();
      setMe(profile);
    } catch {
      clearTokens();
      setMe(null);
    }
  }, []);

  useEffect(() => {
    (async () => {
      await loadProfile();
      setReady(true);
    })();
  }, [loadProfile]);

  const isModerator = Boolean(me && me.role && me.role !== "user");

  return (
    <>
      <header className="app-header">
        <h1>SignalPro</h1>
        <span className="who">
          {me
            ? `${String(me.display_name || me.email || "compte")} · rôle ${String(me.role)} · ${
                me.is_verified ? "vérifié" : "non vérifié"
              }`
            : "Signalements conformes — aucune promesse de bannissement"}
        </span>
      </header>

      {me && (
        <nav className="tabs">
          <button className={tab === "dashboard" ? "active" : ""} onClick={() => setTab("dashboard")}>
            Tableau de bord
          </button>
          <button className={tab === "reports" ? "active" : ""} onClick={() => setTab("reports")}>
            Signalements &amp; import
          </button>
          <button className={tab === "community" ? "active" : ""} onClick={() => setTab("community")}>
            Communauté
          </button>
          {isModerator && (
            <button className={tab === "moderation" ? "active" : ""} onClick={() => setTab("moderation")}>
              Modération
            </button>
          )}
          <button className={tab === "settings" ? "active" : ""} onClick={() => setTab("settings")}>
            Réglages
          </button>
        </nav>
      )}

      <main>
        {/* Avertissement affiché en permanence, y compris sans session et dans le
            HTML initial : c'est une obligation d'information, pas un élément
            conditionnel. */}
        <section className="card">
          <h2>Avertissement obligatoire</h2>
          <div className="notice error">
            <strong>Un faux signalement est illégal.</strong> {DISCLAIMER}
          </div>
          <div className="notice warn">{NO_GUARANTEE_NOTE}</div>
          <p className="muted">
            SignalPro ne peut pas suspendre un compte WhatsApp : seuls Meta et WhatsApp examinent les
            signalements et décident. Aucune fonction de l'application ne contourne les limites de WhatsApp,
            et aucun message n'est conservé sans votre consentement explicite.
          </p>
        </section>

        {!ready && <p className="muted">Vérification de votre session…</p>}

        {ready && !me && <AuthPanel onAuthenticated={loadProfile} />}

        {ready && me && (
          <>
            {tab === "dashboard" && <DashboardPanel me={me} onRefreshProfile={loadProfile} />}
            {tab === "reports" && <ReportsPanel />}
            {tab === "community" && <CommunityPanel />}
            {tab === "moderation" && isModerator && <ModerationPanel />}
            {tab === "settings" && <SettingsPanel me={me} onChanged={loadProfile} />}
          </>
        )}
      </main>
    </>
  );
}
