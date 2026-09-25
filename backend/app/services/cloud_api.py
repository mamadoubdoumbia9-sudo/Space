"""WhatsApp Business Platform (Cloud API) — usage strictement conforme.

⚠️ CE QUE CETTE API PERMET RÉELLEMENT (état vérifié en septembre 2026)
---------------------------------------------------------------------
WhatsApp Business Platform est une API de MESSAGERIE d'entreprise. Meta n'expose
AUCUN endpoint public permettant à un tiers de « signaler » ou de « faire bannir »
le compte WhatsApp d'une autre personne. Toute bibliothèque ou service qui
prétendrait le contraire est soit inexact, soit une violation des CGU.

Ce module implemente donc uniquement des appels officiels et utiles :
  * vérification de la configuration du compte professionnel ;
  * réception et vérification de signature des webhooks (permet de détecter que
    des escrocs ciblent les clients d'une entreprise, et de notifier le propriétaire) ;
  * réception de `account_update` : si META restreint le compte professionnel,
    l'information vient de Meta elle-même (seule source légitime) ;
  * envoi réel de modèles de message autorisés (ex. alerte interne au gérant).

Pour l'escalade d'un dossier vers Meta, nous passons par le canal officiel
documenté (formulaire/point de contact abus configuré par l'opérateur :
`META_ABUSE_ESCALATION_EMAIL`), avec un dossier PDF complet et une trace d'envoi
SMTP réelle. L'application ne prétend jamais qu'un compte a été banni.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import logging
from dataclasses import dataclass
from typing import Any

import httpx

from ..config import get_settings

log = logging.getLogger("signalpro.cloudapi")


class CloudApiError(Exception):
    pass


class CloudApiNotConfigured(CloudApiError):
    pass


@dataclass
class WebhookEvent:
    field: str
    payload: dict[str, Any]
    raw: dict[str, Any]


class WhatsAppCloudClient:
    def __init__(self, token: str | None = None, phone_number_id: str | None = None, version: str | None = None):
        s = get_settings()
        self.token = token or s.cloud_api_token
        self.phone_number_id = phone_number_id or s.cloud_api_phone_number_id
        self.version = version or s.cloud_api_version
        self.app_secret = s.cloud_api_app_secret

    @property
    def configured(self) -> bool:
        return bool(self.token and self.phone_number_id)

    def _url(self, path: str) -> str:
        return f"https://graph.facebook.com/{self.version}/{path.lstrip('/')}"

    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self.token}", "Content-Type": "application/json"}

    def _request(self, method: str, path: str, **kwargs) -> dict[str, Any]:
        if not self.configured:
            raise CloudApiNotConfigured(
                "WhatsApp Business Platform non configurée : renseignez CLOUD_API_TOKEN et "
                "CLOUD_API_PHONE_NUMBER_ID (jeton système Meta)."
            )
        try:
            resp = httpx.request(method, self._url(path), headers=self._headers(), timeout=25, **kwargs)
        except httpx.HTTPError as exc:
            raise CloudApiError(f"Réseau indisponible vers graph.facebook.com : {exc}") from exc
        if resp.status_code >= 400:
            try:
                err = resp.json().get("error", {})
                msg = err.get("message", resp.text[:300])
                code = err.get("code")
                sub = err.get("error_subcode")
            except Exception:  # noqa: BLE001
                msg, code, sub = resp.text[:300], None, None
            raise CloudApiError(f"Meta a refusé la requête ({resp.status_code}, code={code}/{sub}) : {msg}")
        return resp.json()

    # --- Diagnostics / exploitation --------------------------------------
    def verify_configuration(self) -> dict[str, Any]:
        """Vérifie réellement le compte pro auprès de Meta."""
        data = self._request("GET", self.phone_number_id, params={"fields": "display_phone_number,verified_name,quality_rating,platform_type,code_verification_status,account_mode"})
        return data

    def list_templates(self, limit: int = 50) -> list[dict[str, Any]]:
        s = get_settings()
        if not s.cloud_api_phone_number_id:
            raise CloudApiNotConfigured("CLOUD_API_PHONE_NUMBER_ID manquant.")
        waba = s.cloud_api_phone_number_id
        data = self._request("GET", f"{waba}/message_templates", params={"limit": limit})
        return data.get("data", [])

    def send_text(self, to_e164: str, body: str) -> dict[str, Any]:
        """Envoi de service dans la fenêtre de 24 h (conversation ouverte)."""
        return self._request(
            "POST",
            f"{self.phone_number_id}/messages",
            json={
                "messaging_product": "whatsapp",
                "recipient_type": "individual",
                "to": to_e164.lstrip("+"),
                "type": "text",
                "text": {"preview_url": False, "body": body[:4000]},
            },
        )

    def send_template(self, to_e164: str, template_name: str, language: str = "fr", components: list | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "messaging_product": "whatsapp",
            "to": to_e164.lstrip("+"),
            "type": "template",
            "template": {"name": template_name, "language": {"code": language}},
        }
        if components:
            payload["template"]["components"] = components
        return self._request("POST", f"{self.phone_number_id}/messages", json=payload)

    def mark_read(self, wa_message_id: str) -> dict[str, Any]:
        return self._request(
            "POST",
            f"{self.phone_number_id}/messages",
            json={"messaging_product": "whatsapp", "status": "read", "message_id": wa_message_id},
        )

    # --- Webhooks ----------------------------------------------------------
    @staticmethod
    def verify_webhook_challenge(mode: str | None, token: str | None, challenge: str | None) -> str | None:
        s = get_settings()
        if mode == "subscribe" and token and s.cloud_api_verify_token and hmac.compare_digest(token, s.cloud_api_verify_token):
            return challenge
        return None

    def verify_signature(self, raw_body: bytes, signature_header: str | None) -> bool:
        if not self.app_secret:
            log.warning("Webhook reçu sans CLOUD_API_APP_SECRET : signature NON vérifiée (refus).")
            return False
        if not signature_header or not signature_header.startswith("sha256="):
            return False
        expected = hmac.new(self.app_secret.encode(), raw_body, hashlib.sha256).hexdigest()
        return hmac.compare_digest(expected, signature_header.split("=", 1)[1])

    @staticmethod
    def parse_webhook(raw_body: bytes) -> list[WebhookEvent]:
        try:
            payload = json.loads(raw_body.decode())
        except (json.JSONDecodeError, UnicodeDecodeError):
            return []
        events: list[WebhookEvent] = []
        for entry in payload.get("entry", []) or []:
            for change in entry.get("changes", []) or []:
                events.append(
                    WebhookEvent(
                        field=change.get("field", "unknown"),
                        payload=change.get("value", {}) or {},
                        raw={"entry_id": entry.get("id"), "change": change},
                    )
                )
        return events


_client: WhatsAppCloudClient | None = None


def get_cloud_client() -> WhatsAppCloudClient:
    global _client
    if _client is None:
        _client = WhatsAppCloudClient()
    return _client
