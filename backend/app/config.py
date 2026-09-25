"""Configuration centrale — toute valeur sensible vient de l'environnement.

Aucun secret n'est écrit en dur : au démarrage, l'application refuse de tourner
en mode `production` si les clés restent sur leurs valeurs de développement.
"""
from __future__ import annotations

import base64
import functools
import os
import secrets
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


def _b64_key(raw: str) -> bytes:
    return base64.urlsafe_b64decode(raw.encode())


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # --- Environnement -----------------------------------------------------
    env: Literal["dev", "test", "production"] = "dev"
    app_name: str = "SignalPro CI — Signalement conforme WhatsApp"
    public_base_url: str = "http://localhost:8000"

    # --- Base de données ---------------------------------------------------
    # dev/test: sqlite ; production: postgresql+psycopg://user:pass@host/db
    database_url: str = "sqlite:///./signalpro.db"

    # --- Secrets (OBLIGATOIRES de façon sûre en production) -----------------
    jwt_secret: str = "dev-jwt-secret-change-me"
    jwt_access_ttl_min: int = 30
    jwt_refresh_ttl_days: int = 30

    # Clé AES-256-GCM (40 caractères base64 urlsafe ~ 32 octets) pour le
    # chiffrement des données sensibles (numéros, pièces de preuve).
    field_key_b64: str = ""
    # Clé HMAC pour les index aveugles (recherche/anti-doublon sur numéros chiffrés).
    fingerprint_key: str = "dev-fingerprint-key-change-me"

    # --- Stockage des preuves ---------------------------------------------
    storage_backend: Literal["local", "s3"] = "local"
    storage_dir: str = "./var/evidence"
    s3_bucket: str = ""
    s3_region: str = ""
    s3_endpoint: str = ""
    s3_access_key: str = ""
    s3_secret_key: str = ""

    # --- Email / SMS (vérification obligatoire des comptes) ---------------
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    smtp_from: str = "no-reply@signalpro.example"
    sms_provider_url: str = ""
    sms_provider_token: str = ""
    # Si aucun fournisseur n'est configuré, les codes sont écrits dans le
    # journal applicatif (utilisable en dev) — l'API le signale explicitement.
    allow_console_verification: bool = True

    # --- WhatsApp Business Platform (Cloud API) ---------------------------
    # Utilisée UNIQUEMENT pour les comptes professionnels vérifiés.
    cloud_api_version: str = "v21.0"
    cloud_api_phone_number_id: str = ""
    cloud_api_token: str = ""
    cloud_api_app_secret: str = ""
    cloud_api_verify_token: str = ""

    # --- Connecteur "session WhatsApp de l'utilisateur" -------------------
    # Passerelle locale (protocole multi-appareils officiel de WhatsApp).
    # Le backend ne se connecte JAMAIS directement : il parle à la passerelle
    # via HTTP signé. Cela permet de faire tourner la passerelle chez
    # l'utilisateur (le plus protecteur) ou dans une image dédiée.
    connector_base_url: str = ""          # ex: http://127.0.0.1:8787
    connector_shared_secret: str = ""     # HMAC des requêtes backend <-> passerelle
    connector_remote_mode: bool = False   # True = passerelle hébergée (déconseillé)

    # --- Limites anti-abus (NON désactivables) ----------------------------
    max_reports_per_hour_user: int = 5
    max_reports_per_day_user: int = 20
    max_actions_per_minute_user: int = 10
    max_abusive_strikes: int = 2          # au-delà -> bannissement définitif
    min_verifications_to_publish: int = 3  # signalements vérifiés avant publication
    min_verifications_to_escalate: int = 3  # dossier groupé vers Meta
    max_pairing_per_day: int = 3
    max_evidence_bytes: int = 12 * 1024 * 1024
    campaign_max_targets_per_day: int = 10   # campagnes par utilisateur / jour
    campaign_hard_cap: int = 500             # plafond d'exécutions par campagne

    # --- Divers ------------------------------------------------------------
    retention_days_evidence: int = 730       # conservation légale des preuves
    audit_log_retention_days: int = 3650     # journalisation longue (judiciaire)
    cors_origins: str = "*"
    docs_enabled: bool = True

    # ----------------------------------------------------------------------
    @property
    def field_key(self) -> bytes:
        if self.field_key_b64:
            return _b64_key(self.field_key_b64)
        # Clé dérivée déterministe en dev uniquement (jamais en production).
        import hashlib

        return hashlib.sha256(f"dev-field-key::{self.fingerprint_key}".encode()).digest()

    @property
    def is_prod(self) -> bool:
        return self.env == "production"

    def validate_runtime(self) -> list[str]:
        """Retourne la liste des problèmes bloquants en production."""
        problems: list[str] = []
        if not self.is_prod:
            return problems
        if self.jwt_secret.startswith("dev-"):
            problems.append("JWT_SECRET non configuré")
        if not self.field_key_b64:
            problems.append("FIELD_KEY_B64 non configuré (chiffrement des données sensibles)")
        if self.fingerprint_key.startswith("dev-"):
            problems.append("FINGERPRINT_KEY non configuré")
        if self.database_url.startswith("sqlite"):
            problems.append("DATABASE_URL doit pointer vers PostgreSQL en production")
        if self.connector_remote_mode:
            problems.append(
                "CONNECTOR_REMOTE_MODE=1 : héberger la session WhatsApp d'un utilisateur "
                "côté serveur est interdit par nos règles de sécurité. Utilisez la passerelle locale."
            )
        return problems


@functools.lru_cache
def get_settings() -> Settings:
    return Settings()


def generate_secret(n: int = 32) -> str:
    return base64.urlsafe_b64encode(secrets.token_bytes(n)).decode()


if __name__ == "__main__":  # pragma: no cover - utilitaire de mise en route
    print("JWT_SECRET=" + generate_secret(48))
    print("FIELD_KEY_B64=" + generate_secret(32))
    print("FINGERPRINT_KEY=" + generate_secret(32))
    print("CONNECTOR_SHARED_SECRET=" + generate_secret(32))
