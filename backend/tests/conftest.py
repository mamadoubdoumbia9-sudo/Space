"""Configuration des tests : base isolée, stockage temporaire, passerelle locale réelle."""
from __future__ import annotations

import base64
import itertools
import os
import tempfile
from pathlib import Path

TMP = Path(tempfile.mkdtemp(prefix="signalpro-tests-"))

os.environ["ENV"] = "test"
os.environ["DATABASE_URL"] = f"sqlite:///{TMP}/test.db"
os.environ["STORAGE_DIR"] = str(TMP / "evidence")
os.environ["JWT_SECRET"] = "test-jwt-secret-not-for-production"
os.environ["FIELD_KEY_B64"] = base64.urlsafe_b64encode(b"test-field-key-32-bytes-long!!!!").decode()
os.environ["FINGERPRINT_KEY"] = "test-fingerprint-key"
os.environ["CONNECTOR_SHARED_SECRET"] = "test-gateway-secret"
os.environ["CLOUD_API_APP_SECRET"] = "test-app-secret"
os.environ["CLOUD_API_VERIFY_TOKEN"] = "test-verify-token"
os.environ["ALLOW_CONSOLE_VERIFICATION"] = "true"
os.environ["MAX_REPORTS_PER_HOUR_USER"] = "5"
os.environ["MAX_REPORTS_PER_DAY_USER"] = "20"
os.environ["MIN_VERIFICATIONS_TO_PUBLISH"] = "3"
os.environ["MIN_VERIFICATIONS_TO_ESCALATE"] = "3"

from tests.fake_gateway import FakeGateway  # noqa: E402

# La passerelle doit démarrer AVANT l'import de l'application : la configuration
# (et donc CONNECTOR_BASE_URL) est figée à l'import, comme en production.
gateway = FakeGateway()
GATEWAY_URL = gateway.start()
os.environ["CONNECTOR_BASE_URL"] = GATEWAY_URL

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.db import SessionLocal, init_db  # noqa: E402
from app.models import User  # noqa: E402
from app.security import encrypt_str, fingerprint, hash_password  # noqa: E402


@pytest.fixture(scope="session", autouse=True)
def _gateway():
    yield GATEWAY_URL
    gateway.stop()


@pytest.fixture(scope="session")
def client():
    from app.main import app

    init_db()
    with TestClient(app) as c:
        yield c


@pytest.fixture()
def db():
    with SessionLocal() as session:
        yield session


_phone_counter = itertools.count(1)


def make_user(email: str, password: str = "MotDePasse123", role: str = "user", verified: bool = True,
              phone: str | None = None, status: str = "active") -> int:
    """Crée un utilisateur de test avec un numéro RÉELLEMENT unique."""
    with SessionLocal() as db:
        existing = db.query(User).filter(User.email_fp == fingerprint(email)).one_or_none()
        if existing:
            return existing.id
        candidate = phone or ""
        while True:
            if not candidate:
                candidate = f"+2236{next(_phone_counter):08d}"
            taken = db.query(User).filter(User.phone_fp == fingerprint(candidate)).one_or_none()
            if taken is None:
                break
            candidate = ""
        user = User(
            email_enc=encrypt_str(email),
            email_fp=fingerprint(email),
            phone_enc=encrypt_str(candidate),
            phone_fp=fingerprint(candidate),
            display_name=email.split("@")[0],
            password_hash=hash_password(password),
            role=role,
            status=status,
            is_verified=verified,
        )
        db.add(user)
        db.commit()
        return user.id


def auth_headers(client: TestClient, email: str, password: str = "MotDePasse123") -> dict[str, str]:
    resp = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    assert resp.status_code == 200, resp.text
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}


def make_png(width: int = 400, height: int = 300, seed: int = 7) -> bytes:
    """Génère une vraie image PNG non uniforme (validation réelle de preuve)."""
    import io

    from PIL import Image, ImageDraw

    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    for i in range(0, width, 7):
        draw.line([(i, 0), (width - i, height)], fill=((i * seed) % 255, (i * 3) % 255, (i * 7) % 255), width=2)
    draw.text((10, 10), f"Conversation WhatsApp preuve {seed}", fill="black")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def make_wa_export(target_phone: str = "+22361234567") -> bytes:
    lines = [
        f"[12/03/2026, 14:22:05] {target_phone}: Bonjour, votre colis est bloqué à la douane",
        "[12/03/2026, 14:22:40] Moi: Je n'ai rien commandé",
        f"[12/03/2026, 14:23:10] {target_phone}: Payez 25 000 FCFA de frais de douane via Wave puis envoyez la capture",
        "[12/03/2026, 14:25:00] Moi: Non merci.",
    ]
    return "\n".join(lines).encode("utf-8")


def add_gateway_contact(phone: str, is_group: bool = False) -> None:
    """Déclare qu'un numéro a réellement contacté l'utilisateur (côté « téléphone »).

    Cela ne contourne aucune règle métier : le backend vérifie toujours, via la
    synchronisation, que le numéro figure dans les conversations de l'appareil.
    """
    from tests.fake_gateway import Handler

    digits = "".join(c for c in phone if c.isdigit())
    jid = digits + ("-999@g.us" if is_group else "@s.whatsapp.net")
    if any(c["jid"] == jid for c in Handler.contacts):
        return
    Handler.contacts.append({"jid": jid, "is_group": is_group, "last_message_at": 1773300000})


def link_device(client, headers: dict, label: str = "Appareil") -> int:
    """Lie un appareil (réutilise l'appareil connecté existant) et synchronise les conversations."""
    existing = client.get("/api/v1/devices", headers=headers).json()
    connected = next((d for d in existing if d["status"] == "connected"), None)
    if connected:
        sync_contacts(client, headers)
        return connected["id"]
    started = client.post(
        "/api/v1/devices/link/start",
        headers=headers,
        json={"label": label, "risk_consent": True, "consent_version": "2026-09-1"},
    )
    assert started.status_code == 200, started.text
    device_id = started.json()["device_id"]
    confirmed = client.post("/api/v1/devices/link/confirm", headers=headers, json={"device_id": device_id})
    assert confirmed.status_code == 200, confirmed.text
    sync_contacts(client, headers)
    return device_id


def sync_contacts(client, headers: dict) -> None:
    for device in client.get("/api/v1/devices", headers=headers).json():
        if device["status"] == "connected":
            client.post(f"/api/v1/devices/{device['id']}/sync", headers=headers)
