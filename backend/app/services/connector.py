"""Client de la passerelle « session WhatsApp de l'utilisateur ».

ARCHITECTURE (et pourquoi elle est ainsi) :

    [Téléphone de l'utilisateur]  ==  WhatsApp (protocole multi-appareils officiel)
              |
              |  (l'appareil lié tourne CHEZ l'utilisateur, pas chez nous)
              v
     [Passerelle locale : gateway/]  <--- HMAC --->  [Backend SignalPro]

Le backend ne détient jamais les clés de session WhatsApp d'un utilisateur : il
envoie des ordres signés à une passerelle qui tourne dans l'environnement de
l'utilisateur. Si la passerelle n'est pas joignable, l'action ÉCHOUE et est
enregistrée comme échec — jamais comme un succès.

`capabilities()` interroge la passerelle pour savoir ce qu'elle sait réellement
faire. Si la passerelle ne sait pas déclencher le signalement natif, le backend
bascule honnêtement sur le parcours guidé (`manual_guided`) au lieu de prétendre
avoir signalé à la place de l'utilisateur.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Any

import httpx

from ..config import get_settings
from ..security import sign_connector_request

log = logging.getLogger("signalpro.connector")


class ConnectorError(Exception):
    """Erreur fonctionnelle remontée telle quelle à l'utilisateur."""


class ConnectorUnavailable(ConnectorError):
    pass


@dataclass
class GatewayCapabilities:
    report_native: bool
    block_contact: bool
    contact_sync: bool
    version: str = "unknown"

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "GatewayCapabilities":
        caps = payload.get("capabilities", {}) or {}
        return cls(
            report_native=bool(caps.get("report_native")),
            block_contact=bool(caps.get("block_contact")),
            contact_sync=bool(caps.get("contact_sync", True)),
            version=str(payload.get("version", "unknown")),
        )


class ConnectorClient:
    def __init__(self, base_url: str | None = None, secret: str | None = None, timeout: float = 20.0):
        s = get_settings()
        self.base_url = (base_url or s.connector_base_url).rstrip("/")
        self.secret = secret or s.connector_shared_secret
        self.timeout = timeout

    @property
    def configured(self) -> bool:
        return bool(self.base_url and self.secret)

    def _request(self, method: str, path: str, payload: dict | None = None) -> dict[str, Any]:
        if not self.configured:
            raise ConnectorUnavailable(
                "Passerelle WhatsApp non configurée. Renseignez CONNECTOR_BASE_URL et CONNECTOR_SHARED_SECRET, "
                "puis démarrez la passerelle (dossier gateway/) sur la machine de l'utilisateur."
            )
        body = json.dumps(payload or {}, separators=(",", ":")).encode() if payload is not None else b""
        headers = {"Content-Type": "application/json", **sign_connector_request(method, path, body)}
        try:
            resp = httpx.request(
                method, f"{self.base_url}{path}", content=body, headers=headers, timeout=self.timeout
            )
        except httpx.ConnectError as exc:
            raise ConnectorUnavailable(
                f"Passerelle WhatsApp injoignable sur {self.base_url} : {exc}. "
                "Vérifiez que la passerelle tourne et que l'appareil est bien lié."
            ) from exc
        except httpx.TimeoutException as exc:
            raise ConnectorUnavailable(f"Passerelle WhatsApp : délai dépassé ({self.timeout}s).") from exc

        if resp.status_code >= 400:
            detail = resp.text[:500]
            try:
                detail = resp.json().get("error", detail)
            except Exception:  # noqa: BLE001
                pass
            raise ConnectorError(f"Passerelle WhatsApp a refusé l'action ({resp.status_code}) : {detail}")
        try:
            return resp.json()
        except json.JSONDecodeError as exc:  # pragma: no cover
            raise ConnectorError("Réponse illisible de la passerelle WhatsApp.") from exc

    # -- Cycle de vie de session -------------------------------------------
    def capabilities(self) -> GatewayCapabilities:
        return GatewayCapabilities.from_payload(self._request("GET", "/v1/capabilities"))

    def start_session(self, user_ref: str, label: str, client_public_key: str = "") -> dict[str, Any]:
        return self._request(
            "POST", "/v1/sessions", {"user_ref": user_ref, "label": label, "client_public_key": client_public_key}
        )

    def session_state(self, session_ref: str) -> dict[str, Any]:
        return self._request("GET", f"/v1/sessions/{session_ref}")

    def sync_contacts(self, session_ref: str) -> dict[str, Any]:
        return self._request("POST", f"/v1/sessions/{session_ref}/contacts/sync")

    def revoke_session(self, session_ref: str) -> dict[str, Any]:
        return self._request("DELETE", f"/v1/sessions/{session_ref}")

    # -- Actions WhatsApp réelles ------------------------------------------
    def native_report(
        self,
        session_ref: str,
        peer_jid: str,
        category: str,
        message_ids: list[str],
        note: str = "",
    ) -> dict[str, Any]:
        """Déclenche le signalement natif depuis le compte de l'utilisateur.

        La passerelle exécute l'action de signalement côté client WhatsApp.
        Elle renvoie `performed=true` uniquement si l'action a réellement été
        transmise au serveur WhatsApp.
        """
        return self._request(
            "POST",
            f"/v1/sessions/{session_ref}/actions/report",
            {
                "peer_jid": peer_jid,
                "category": category,
                "message_ids": message_ids[:6],  # WhatsApp transmet les derniers messages
                "note": note[:1000],
            },
        )

    def block_contact(self, session_ref: str, peer_jid: str) -> dict[str, Any]:
        return self._request("POST", f"/v1/sessions/{session_ref}/actions/block", {"peer_jid": peer_jid})

    def unblock_contact(self, session_ref: str, peer_jid: str) -> dict[str, Any]:
        return self._request("POST", f"/v1/sessions/{session_ref}/actions/unblock", {"peer_jid": peer_jid})

    def fetch_recent_messages(self, session_ref: str, peer_jid: str, limit: int = 5) -> dict[str, Any]:
        """Récupère les derniers messages pour constituer la preuve (avec consentement)."""
        return self._request(
            "POST", f"/v1/sessions/{session_ref}/messages/recent", {"peer_jid": peer_jid, "limit": min(limit, 20)}
        )


_client: ConnectorClient | None = None


def get_connector() -> ConnectorClient:
    global _client
    if _client is None:
        _client = ConnectorClient()
    return _client
