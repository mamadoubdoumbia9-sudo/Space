"""Point d'entrée de l'API SignalPro.

Démarrage :
    uvicorn app.main:app --host 0.0.0.0 --port 8000

Le cycle de vie démarre le worker de transmissions (file réelle), installe les
signatures de détection et vérifie la configuration de sécurité.
"""
from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import select

from .config import get_settings
from .constants import DISCLAIMER_SHORT_FR
from .db import SessionLocal, init_db
from .routers import auth, campaigns, community, dashboard, detect, devices, moderation, reports, webhooks
from .schemas import HealthOut
from .services import jobs
from .services.spam import seed_signatures, signatures_fingerprint
from .services.storage import get_storage

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s :: %(message)s",
)
log = logging.getLogger("signalpro")

VERSION = "1.0.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    problems = settings.validate_runtime()
    if problems:
        # En production, une configuration dangereuse empêche le démarrage.
        raise RuntimeError("Configuration de production invalide : " + " | ".join(problems))

    init_db()
    with SessionLocal() as db:
        created = seed_signatures(db)
        db.commit()
        log.info("Signatures de détection : %d ajoutées (version %s).", created, signatures_fingerprint(db))

    worker = asyncio.create_task(jobs.run_worker())
    if settings.connector_remote_mode:
        log.warning("CONNECTOR_REMOTE_MODE actif : déconseillé, la passerelle doit tourner chez l'utilisateur.")
    log.info("API SignalPro %s prête (env=%s).", VERSION, settings.env)
    try:
        yield
    finally:
        worker.cancel()
        with contextlib_suppress():
            await worker


class contextlib_suppress:
    """Petit utilitaire local : évite un import supplémentaire pour un seul usage."""

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return exc_type is not None and issubclass(exc_type, asyncio.CancelledError)


app = FastAPI(
    title="SignalPro — Signalement conforme WhatsApp",
    description=(
        DISCLAIMER_SHORT_FR
        + "\n\nCette API ne dispose d'aucun moyen de forcer la suspension d'un compte : elle organise "
        "la collecte de preuves, la relecture humaine, la transmission par canaux officiels et le suivi."
    ),
    version=VERSION,
    lifespan=lifespan,
    docs_url="/docs" if get_settings().docs_enabled else None,
    redoc_url=None,
)

_origins = [o.strip() for o in get_settings().cors_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_origins or ["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST", "DELETE", "PATCH", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Hub-Signature-256", "X-SignalPro-Signature",
                   "X-SignalPro-Timestamp"],
)


@app.middleware("http")
async def security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    if request.url.path.startswith("/api"):
        response.headers.setdefault("Cache-Control", "no-store")
    return response


@app.exception_handler(Exception)
async def unhandled(request: Request, exc: Exception):  # pragma: no cover - filet de sécurité
    log.exception("Erreur non gérée sur %s", request.url.path)
    return JSONResponse(
        status_code=500,
        content={"detail": "Erreur interne. L'incident est journalisé ; aucune action n'a été supposée réussie."},
    )


@app.get("/health", response_model=HealthOut, tags=["Système"])
def health():
    s = get_settings()
    db_ok = "sqlite" if s.database_url.startswith("sqlite") else "postgresql"
    storage_ok = "inconnu"
    try:
        storage = get_storage()
        probe_key = "health/probe.bin"
        storage.put(probe_key, b"signalpro-health")
        read_back = storage.get(probe_key)
        storage.delete(probe_key)
        storage_ok = "ok" if read_back == b"signalpro-health" else "écriture incohérente"
    except Exception as exc:  # noqa: BLE001
        storage_ok = f"indisponible: {exc}"
    return HealthOut(
        status="ok" if storage_ok == "ok" else "degraded",
        version=VERSION,
        database=db_ok,
        storage=f"{getattr(get_storage(), 'name', 'local')} ({storage_ok})",
        connector_configured=bool(s.connector_base_url and s.connector_shared_secret),
        cloud_api_configured=bool(s.cloud_api_token and s.cloud_api_phone_number_id),
        verification_delivery="smtp" if s.smtp_host else ("sms" if s.sms_provider_url else "console"),
    )


@app.get("/config/check", tags=["Système"])
def config_check():
    """Diagnostic de mise en route : dit précisément ce qui manque, sans exposer de secret."""
    s = get_settings()
    return {
        "env": s.env,
        "production_problems": s.validate_runtime(),
        "database": "sqlite" if s.database_url.startswith("sqlite") else "postgresql",
        "encryption": {
            "field_key_configured": bool(s.field_key_b64),
            "fingerprint_key_default": s.fingerprint_key.startswith("dev-"),
        },
        "verification": {"smtp": bool(s.smtp_host), "sms": bool(s.sms_provider_url),
                         "console_fallback": s.allow_console_verification},
        "connector": {
            "base_url_configured": bool(s.connector_base_url),
            "shared_secret_configured": bool(s.connector_shared_secret),
            "remote_mode": s.connector_remote_mode,
        },
        "cloud_api": {"token": bool(s.cloud_api_token), "phone_number_id": bool(s.cloud_api_phone_number_id),
                      "app_secret": bool(s.cloud_api_app_secret), "verify_token": bool(s.cloud_api_verify_token)},
        "escalation_channel": {
            "configured": bool(__import__("os").environ.get("META_ABUSE_ESCALATION_EMAIL")),
            "note": (
                "Aucun endpoint public de « signalement » n'existe côté WhatsApp Business Platform. "
                "L'escalade se fait par le canal officiel d'abus configuré par l'exploitant."
            ),
        },
        "limits": {
            "per_hour": s.max_reports_per_hour_user,
            "per_day": s.max_reports_per_day_user,
            "actions_per_minute": s.max_actions_per_minute_user,
            "strikes_before_ban": s.max_abusive_strikes,
            "min_verified_to_publish": s.min_verifications_to_publish,
            "min_verified_to_escalate": s.min_verifications_to_escalate,
        },
        "worker": jobs.state.snapshot(),
    }


@app.get("/", tags=["Système"])
def root():
    return {
        "name": "SignalPro API",
        "version": VERSION,
        "disclaimer": DISCLAIMER_SHORT_FR,
        "docs": "/docs" if get_settings().docs_enabled else None,
        "honest_capabilities": [
            "collecte de preuves vérifiées (capture, export, identifiants de message)",
            "relecture humaine avant toute transmission",
            "transmission réelle via l'appareil lié de l'utilisateur ou le canal officiel Meta",
            "suivi de l'état des signalements et des suspensions confirmées par Meta",
            "aucun moyen de forcer la suspension d'un compte",
        ],
    }


api_prefix = "/api/v1"
for r in (
    auth.router,
    devices.router,
    reports.router,
    campaigns.router,
    community.router,
    detect.router,
    moderation.router,
    dashboard.router,
    webhooks.router,
):
    app.include_router(r, prefix=api_prefix)
