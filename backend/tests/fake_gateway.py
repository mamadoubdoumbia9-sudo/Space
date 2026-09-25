"""Fausse passerelle WhatsApp pour les tests d'intégration.

Elle implémente le même protocole que `gateway/` (signature HMAC obligatoire,
cycle de session, actions report/block, synchronisation des conversations) afin
de tester le backend de bout en bout SANS jamais toucher à un vrai compte.

Cette fausse passerelle est le seul « mock » du projet : il est là pour tester
l'intégration HTTP réelle, pas pour simuler des fonctionnalités en production.
"""
from __future__ import annotations

import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SHARED_SECRET = "test-gateway-secret"
SESSIONS: dict[str, dict] = {}
CALLS: list[dict] = []


def _verify(secret: str, method: str, path: str, body: bytes, ts: str, sig: str) -> bool:
    import hashlib
    import hmac

    try:
        ts_i = int(ts)
    except (TypeError, ValueError):
        return False
    if abs(int(time.time()) - ts_i) > 300:
        return False
    expected = hmac.new(secret.encode(), b"%s\n%s\n%d\n" % (method.encode(), path.encode(), ts_i) + body,
                        hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, sig or "")


BASE_CONTACTS = [
    {"jid": "22361234567@s.whatsapp.net", "is_group": False, "last_message_at": 1773000000},
    {"jid": "22367788990@s.whatsapp.net", "is_group": False, "last_message_at": 1773100000},
    {"jid": "123456789-987654321@g.us", "is_group": True, "last_message_at": 1773200000},
]


class Handler(BaseHTTPRequestHandler):
    capabilities = {"report_native": True, "block_contact": True, "contact_sync": True}
    session_connected = True
    fail_next_report = False
    # Conversations réellement présentes sur le « téléphone » simulé. Les tests
    # peuvent en ajouter pour représenter d'autres utilisateurs ayant été contactés.
    contacts: list[dict] = list(BASE_CONTACTS)

    def log_message(self, *args):  # silence
        return

    def _body(self) -> bytes:
        length = int(self.headers.get("content-length") or 0)
        return self.rfile.read(length) if length else b""

    def _respond(self, code: int, payload: dict) -> None:
        raw = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _handle(self, method: str) -> None:
        path = self.path.split("?")[0]
        body = self._body()
        if path == "/v1/capabilities" and method == "GET":
            return self._respond(200, {"version": "fake-1.0", "capabilities": self.capabilities})
        if not _verify(SHARED_SECRET, method, path, body, self.headers.get("X-SignalPro-Timestamp", ""),
                       self.headers.get("X-SignalPro-Signature", "")):
            return self._respond(401, {"error": "signature invalide"})
        CALLS.append({"method": method, "path": path, "body": body.decode() or None})

        if path == "/v1/sessions" and method == "POST":
            payload = json.loads(body or b"{}")
            ref = f"ref-{len(SESSIONS) + 1}"
            SESSIONS[ref] = {"user_ref": payload.get("user_ref"), "label": payload.get("label"),
                             "status": "awaiting_scan", "jid": None, "number": None}
            return self._respond(200, {"session_ref": ref, "status": "awaiting_scan",
                                       "pairing_payload": "2@abcdefghijklmnopqrstuvwxyz,ABCDEF...",
                                       "expires_in": 120})

        if path.startswith("/v1/sessions/") and method == "GET":
            ref = path.split("/")[3]
            s = SESSIONS.get(ref)
            if not s:
                return self._respond(404, {"error": "session inconnue"})
            if self.session_connected and s["jid"] is None:
                s.update(status="connected", jid="22361234567@s.whatsapp.net", number="22361234567",
                         platform="android")
            return self._respond(200, s)

        if path.endswith("/contacts/sync") and method == "POST":
            ref = path.split("/")[3]
            s = SESSIONS.get(ref)
            chats = list(self.contacts)
            if s:
                s["contacts_synced"] = True
            return self._respond(200, {"chats": chats, "count": len(chats)})

        if path.endswith("/actions/report") and method == "POST":
            if self.fail_next_report:
                type(self).fail_next_report = False
                return self._respond(200, {"performed": False, "detail": "action refusée par WhatsApp (test)"})
            payload = json.loads(body or b"{}")
            return self._respond(200, {"performed": True, "reference": "wa-report-1",
                                       "peer_jid": payload.get("peer_jid"),
                                       "messages_attached": len(payload.get("message_ids") or [])})

        if path.endswith("/actions/block") and method == "POST":
            return self._respond(200, {"performed": True, "reference": "wa-block-1"})

        if path.endswith("/actions/unblock") and method == "POST":
            return self._respond(200, {"performed": True})

        if path.endswith("/messages/recent") and method == "POST":
            return self._respond(200, {"messages": [{"id": "MSG1", "text": "Extrait de test"}]})

        if path.startswith("/v1/sessions/") and method == "DELETE":
            ref = path.split("/")[3]
            SESSIONS.pop(ref, None)
            return self._respond(200, {"revoked": True})

        return self._respond(404, {"error": f"route inconnue {method} {path}"})

    def do_GET(self):
        self._handle("GET")

    def do_POST(self):
        self._handle("POST")

    def do_DELETE(self):
        self._handle("DELETE")


class FakeGateway:
    def __init__(self, secret: str = SHARED_SECRET):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)

    @property
    def url(self) -> str:
        host, port = self.server.server_address[:2]
        return f"http://{host}:{port}"

    def start(self) -> str:
        self.thread.start()
        return self.url

    def stop(self) -> None:
        self.server.shutdown()
        self.server.server_close()
