"""Tests d'intégration : parcours complets, protections anti-abus, conformité.

Chaque test vérifie un comportement RÉEL du serveur : aucune simulation.
La passerelle WhatsApp utilisée est un faux serveur HTTP local (tests/fake_gateway.py)
qui applique les mêmes signatures HMAC que la vraie passerelle : l'intégration
réseau est donc réellement testée, mais aucun compte WhatsApp réel n'est touché.
"""
from __future__ import annotations

import io
import json

import pytest

from app.constants import ReportStatus, SubmissionStatus, UserStatus
from app.db import SessionLocal
from app.models import AuditLog, Campaign, Evidence, Report, ReportSubmission, Target, User
from app.security import fingerprint
from tests.conftest import (
    add_gateway_contact,
    auth_headers,
    link_device,
    make_png,
    make_user,
    make_wa_export,
    sync_contacts,
)

MOD = "2026-09-1"  # version de consentement


# ---------------------------------------------------------------------------
# Système
# ---------------------------------------------------------------------------
def test_health_and_config(client):
    health = client.get("/health")
    assert health.status_code == 200
    body = health.json()
    assert body["status"] == "ok", body
    assert "aesgcm" in body["storage"]

    cfg = client.get("/config/check").json()
    assert cfg["limits"]["per_hour"] == 5
    assert cfg["limits"]["per_day"] == 20
    assert cfg["limits"]["actions_per_minute"] == 10
    assert cfg["limits"]["strikes_before_ban"] == 2
    assert cfg["connector"]["base_url_configured"] is True


def test_disclaimers_present_everywhere(client):
    limits = client.get("/api/v1/auth/limits").json()
    short = limits["disclaimers"]["short"].lower()
    assert "ne garantit pas le bannissement" in short
    assert "poursuites judiciaires" in short
    assert limits["disclaimers"]["consent_version"] == MOD


# ---------------------------------------------------------------------------
# Authentification et vérification obligatoire
# ---------------------------------------------------------------------------
def test_register_requires_verification_then_login(client):
    email = "nouveau@example.com"
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": email, "phone": "+22366112233", "password": "MotDePasse123",
              "channel": "email", "accept_terms": True, "accept_privacy": True},
    )
    assert resp.status_code == 201, resp.text
    payload = resp.json()
    assert payload["delivered"] is False  # pas de SMTP en test -> écrit dans le journal

    login = client.post("/api/v1/auth/login", json={"email": email, "password": "MotDePasse123"})
    assert login.status_code == 403
    assert "non vérifié" in login.json()["detail"].lower()

    verified = client.post("/api/v1/auth/verify", json={"email": email, "code": payload["dev_code"]})
    assert verified.status_code == 200, verified.text
    token = verified.json()["access_token"]
    me = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"}).json()
    assert me["is_verified"] is True and me["status"] == "active"


def test_register_rejects_duplicate_and_weak_password(client):
    make_user("dup@example.com")
    resp = client.post("/api/v1/auth/register", json={"email": "dup@example.com", "phone": "+22366119989",
                                                      "password": "MotDePasse123", "channel": "email"})
    assert resp.status_code == 409
    weak = client.post("/api/v1/auth/register", json={"email": "faible@example.com", "phone": "+22366119990",
                                                      "password": "motdepasse", "channel": "email"})
    assert weak.status_code == 422


def test_verification_code_wrong_then_correct(client):
    resp = client.post("/api/v1/auth/register", json={"email": "code@example.com", "phone": "+22366119991",
                                                      "password": "MotDePasse123", "channel": "email"})
    assert resp.status_code == 201, resp.text
    assert client.post("/api/v1/auth/verify", json={"email": "code@example.com", "code": "000000"}).status_code == 400
    ok = client.post("/api/v1/auth/verify", json={"email": "code@example.com", "code": resp.json()["dev_code"]})
    assert ok.status_code == 200


def test_change_password_flow(client):
    email = "motdepasse@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    wrong = client.post("/api/v1/auth/change-password", headers=headers,
                        json={"current_password": "FauxMotDePasse1", "new_password": "NouveauMotDePasse2"})
    assert wrong.status_code == 400
    ok = client.post("/api/v1/auth/change-password", headers=headers,
                     json={"current_password": "MotDePasse123", "new_password": "NouveauMotDePasse2"})
    assert ok.status_code == 200
    assert client.post("/api/v1/auth/login", json={"email": email, "password": "MotDePasse123"}).status_code == 401
    assert client.post("/api/v1/auth/login", json={"email": email, "password": "NouveauMotDePasse2"}).status_code == 200


# ---------------------------------------------------------------------------
# Connexion WhatsApp obligatoire avant toute fonctionnalité
# ---------------------------------------------------------------------------
def test_features_blocked_without_linked_whatsapp(client):
    make_user("sanslien@example.com")
    headers = auth_headers(client, "sanslien@example.com")

    report = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "financial_scam",
        "occurred_at": "2026-03-12T14:22:05Z",
        "description": "Arnaque au colis bloqué avec demande de paiement Wave.",
    })
    assert report.status_code == 422
    assert "preuve de contact" in report.json()["detail"].lower()

    submit = client.post("/api/v1/reports/1/submit", headers=headers,
                         data={"adapter": "user_native", "manual_ack": "true"})
    assert submit.status_code in (404, 409)


def test_link_requires_explicit_consent(client):
    make_user("lien@example.com")
    headers = auth_headers(client, "lien@example.com")
    no_consent = client.post("/api/v1/devices/link/start", headers=headers,
                             json={"label": "Test", "risk_consent": False, "consent_version": MOD})
    assert no_consent.status_code == 400
    assert "consentement" in no_consent.json()["detail"].lower()

    stale = client.post("/api/v1/devices/link/start", headers=headers,
                        json={"label": "Test", "risk_consent": True, "consent_version": "2020-01-1"})
    assert stale.status_code == 400

    started = client.post("/api/v1/devices/link/start", headers=headers,
                          json={"label": "Test", "risk_consent": True, "consent_version": MOD})
    assert started.status_code == 200, started.text
    assert started.json()["pairing_payload"]
    assert "risque" in started.json()["notice"].lower() or "risk" in started.json()["notice"].lower()


def test_confirm_link_uses_real_gateway_identity(client):
    make_user("lien2@example.com")
    headers = auth_headers(client, "lien2@example.com")
    device_id = link_device(client, headers, "Pixel")
    devices = client.get("/api/v1/devices", headers=headers).json()
    device = next(d for d in devices if d["id"] == device_id)
    assert device["status"] == "connected"
    assert device["wa_number"] == "+22361234567"      # identifiant réel remonté par la passerelle
    assert device["contacts_count"] >= 3              # conversations réellement synchronisées
    assert device["risk_consent"] is True

    status = client.get("/api/v1/devices/gateway/status", headers=headers).json()
    assert status["reachable"] is True
    assert status["capabilities"] == {"report_native": True, "block_contact": True, "contact_sync": True}


def test_revoke_device_calls_gateway(client):
    make_user("revoke@example.com")
    headers = auth_headers(client, "revoke@example.com")
    device_id = link_device(client, headers)
    revoked = client.delete(f"/api/v1/devices/{device_id}", headers=headers)
    assert revoked.status_code == 200, revoked.text
    assert "révoquée" in revoked.json()["detail"]
    device = next(d for d in client.get("/api/v1/devices", headers=headers).json() if d["id"] == device_id)
    assert device["status"] == "revoked"


# ---------------------------------------------------------------------------
# Preuves réelles
# ---------------------------------------------------------------------------
@pytest.fixture()
def prepared_user(client):
    """Utilisateur vérifié, WhatsApp lié, conversations synchronisées.

    Le fixture réutilise l'appareil déjà lié : c'est aussi la preuve que la limite
    de 3 appairages par jour est appliquée par le serveur.
    """
    email = "preuve@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    return headers, link_device(client, headers, "Pixel")


def test_report_flow_with_real_evidence_validation(client, prepared_user):
    headers, _device = prepared_user
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "financial_scam",
        "occurred_at": "2026-03-12T14:22:05Z",
        "description": "Faux livreur : frais de douane à payer par Wave, colis inexistant.",
        "message_ids": ["false_22361234567@c.us_3EB0A1B2C3D4E5F6"],
        "contact_proof_method": "linked_device_scan",
    })
    assert created.status_code == 201, created.text
    report_id = created.json()["id"]
    assert created.json()["status"] == ReportStatus.PENDING_VERIFICATION  # identifiants valides = preuve
    assert created.json()["contact_verified"] is True

    png = make_png(seed=11)
    upload = client.post(f"/api/v1/reports/{report_id}/evidence", headers=headers,
                         data={"kind": "screenshot"}, files={"file": ("capture.png", png, "image/png")})
    assert upload.status_code == 201, upload.text
    assert upload.json()["integrity_ok"] is True and upload.json()["sha256"]

    export = client.post(f"/api/v1/reports/{report_id}/evidence", headers=headers,
                         data={"kind": "chat_export"},
                         files={"file": ("export.txt", make_wa_export(), "text/plain")})
    assert export.status_code == 201, export.text
    assert "total_lines" in (export.json()["validation_detail"] or "")

    download = client.get(f"/api/v1/reports/evidence/{upload.json()['id']}/download", headers=headers)
    assert download.status_code == 200 and download.content == png

    usage = client.get("/api/v1/reports/usage", headers=headers).json()
    assert usage["reports_last_hour"] == 1 and usage["reports_last_day"] == 1
    assert usage["remaining_today"] == 19


def test_invalid_evidence_is_rejected(client, prepared_user):
    headers, _ = prepared_user
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22367788990", "category": "spam", "occurred_at": "2026-03-14T09:05:00Z",
        "description": "Messages publicitaires répétés non sollicités chaque jour."})
    report_id = created.json()["id"]

    bad = client.post(f"/api/v1/reports/{report_id}/evidence", headers=headers,
                      data={"kind": "screenshot"},
                      files={"file": ("capture.png", b"ceci-nest-pas-une-image", "image/png")})
    assert bad.status_code == 422

    tiny = client.post(f"/api/v1/reports/{report_id}/evidence", headers=headers,
                       data={"kind": "screenshot"},
                       files={"file": ("petit.png", make_png(120, 90, seed=3), "image/png")})
    assert tiny.status_code == 422

    mismatch = client.post(f"/api/v1/reports/{report_id}/evidence", headers=headers,
                           data={"kind": "chat_export"},
                           files={"file": ("export.txt", make_wa_export("+22369999999"), "text/plain")})
    assert mismatch.status_code == 422
    assert "n'apparaît pas" in mismatch.json()["detail"]

    detail = client.get(f"/api/v1/reports/{report_id}", headers=headers).json()
    assert detail["status"] == ReportStatus.PENDING_EVIDENCE  # jamais transmis sans preuve


def test_duplicate_evidence_across_accounts_is_blocked(client, prepared_user):
    headers, _ = prepared_user
    email2 = "preuve2@example.com"
    make_user(email2)
    headers2 = auth_headers(client, email2)
    link_device(client, headers2, "Second")

    png = make_png(seed=42)
    first = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-03-15T10:00:00Z",
        "description": "Publicités non sollicitées et répétées."}).json()
    assert client.post(f"/api/v1/reports/{first['id']}/evidence", headers=headers,
                       data={"kind": "screenshot"},
                       files={"file": ("capture.png", png, "image/png")}).status_code == 201

    second = client.post("/api/v1/reports", headers=headers2, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-03-15T11:00:00Z",
        "description": "Le même message publicitaire non sollicité."}).json()
    dup = client.post(f"/api/v1/reports/{second['id']}/evidence", headers=headers2,
                      data={"kind": "screenshot"}, files={"file": ("capture.png", png, "image/png")})
    assert dup.status_code == 422
    assert "déjà été déposée" in dup.json()["detail"]

    with SessionLocal() as db:
        report = db.get(Report, second["id"])
        assert report.status == ReportStatus.REJECTED_ABUSIVE   # le marquage survit au refus
        assert "preuve_identique" in (report.auto_flags or "")


# ---------------------------------------------------------------------------
# Limites anti-abus
# ---------------------------------------------------------------------------
def test_daily_and_hourly_report_limits(client):
    """Quotas durs : 5 signalements/heure et 20/jour, comptés côté serveur."""
    user_id = make_user("quota2@example.com")
    from app.services import limits

    with SessionLocal() as db:
        for _ in range(5):
            limits.consume_report_creation(db, user_id)
        with pytest.raises(limits.RateLimitExceeded) as exc:
            limits.consume_report_creation(db, user_id)
        assert exc.value.limit == 5
        db.rollback()

    with SessionLocal() as db:
        for _ in range(20):
            limits.consume(db, user_id, "report_create", "day", 20)
        with pytest.raises(limits.RateLimitExceeded):
            limits.consume(db, user_id, "report_create", "day", 20)
        db.rollback()


def test_api_returns_429_when_hourly_quota_reached(client):
    email = "quota3@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    link_device(client, headers)
    last = None
    for i in range(6):
        last = client.post("/api/v1/reports", headers=headers, json={
            "target_phone": "+22361234567", "category": "spam",
            "occurred_at": f"2026-05-{10 + i:02d}T09:00:00Z",
            "description": f"Message non sollicité numéro {i} de la série."})
    assert last.status_code == 429, last.text
    assert "limite" in last.json()["detail"].lower()


def test_action_limit_10_per_minute(client):
    user_id = make_user("quota@example.com")
    from app.services import limits

    with SessionLocal() as db:
        for _ in range(10):
            limits.consume_action(db, user_id, "block")
        with pytest.raises(limits.RateLimitExceeded) as exc:
            limits.consume_action(db, user_id, "block")
        assert exc.value.limit == 10
        db.rollback()


def test_pairing_limit_three_per_day(client):
    email = "paires@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    for i in range(3):
        resp = client.post("/api/v1/devices/link/start", headers=headers,
                           json={"label": f"T{i}", "risk_consent": True, "consent_version": MOD})
        assert resp.status_code == 200, resp.text
    blocked = client.post("/api/v1/devices/link/start", headers=headers,
                          json={"label": "T4", "risk_consent": True, "consent_version": MOD})
    assert blocked.status_code == 429


def test_two_abusive_strikes_ban_permanently(client):
    email = "fraudeur@example.com"
    make_user(email)
    make_user("modo@example.com", role="moderator")
    headers_user = auth_headers(client, email)
    headers_modo = auth_headers(client, "modo@example.com")
    add_gateway_contact("+22367000111")
    link_device(client, headers_user, "Fraudeur")
    sync_contacts(client, headers_user)

    report_ids = []
    for i in range(2):
        r = client.post("/api/v1/reports", headers=headers_user, json={
            "target_phone": "+22367000111", "category": "spam",
            "occurred_at": f"2026-03-1{i + 1}T08:00:00Z",
            "description": "Signalement de test pour vérifier le mécanisme de sanction."})
        assert r.status_code == 201, r.text
        report_ids.append(r.json()["id"])

    for rid in report_ids:
        decision = client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                               json={"decision": "reject_abusive",
                                     "reason": "Preuve fabriquée, conversation inexistante."})
        assert decision.status_code == 200, decision.text

    with SessionLocal() as db:
        user = db.query(User).filter(User.email_fp == fingerprint(email)).one()
        assert user.strikes == 2
        assert user.status == UserStatus.BANNED
        assert user.banned_at is not None

    assert client.get("/api/v1/auth/me", headers=headers_user).status_code == 403
    assert client.post("/api/v1/auth/login",
                       json={"email": email, "password": "MotDePasse123"}).status_code == 403


def test_single_strike_does_not_ban(client):
    email = "averti@example.com"
    make_user(email)
    make_user("modo9@example.com", role="moderator")
    headers_user = auth_headers(client, email)
    headers_modo = auth_headers(client, "modo9@example.com")
    add_gateway_contact("+22367000222")
    link_device(client, headers_user)
    sync_contacts(client, headers_user)
    rid = client.post("/api/v1/reports", headers=headers_user, json={
        "target_phone": "+22367000222", "category": "spam", "occurred_at": "2026-03-20T08:00:00Z",
        "description": "Signalement qui se révélera abusif après vérification."}).json()["id"]
    client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                json={"decision": "reject_abusive", "reason": "Aucune preuve réelle fournie."})
    with SessionLocal() as db:
        user = db.query(User).filter(User.email_fp == fingerprint(email)).one()
        assert user.strikes == 1 and user.status == UserStatus.ACTIVE
    assert client.get("/api/v1/auth/me", headers=headers_user).status_code == 200


# ---------------------------------------------------------------------------
# Vérification humaine, seuils, escalade
# ---------------------------------------------------------------------------
def _create_pending_reports(client, users: list[str], target: str, start_day: int = 1,
                            with_ids: bool = True) -> list[int]:
    add_gateway_contact(target)
    ids = []
    for idx, email in enumerate(users):
        make_user(email)
        headers = auth_headers(client, email)
        link_device(client, headers)
        sync_contacts(client, headers)
        payload = {
            "target_phone": target,
            "category": "financial_scam",
            "occurred_at": f"2026-04-{start_day + idx:02d}T12:00:00Z",
            "description": "Arnaque financière documentée avec demande de paiement mobile money.",
        }
        if with_ids:
            payload["message_ids"] = [f"false_{target.strip('+')}@c.us_ABCDEF{start_day + idx:02d}"]
        created = client.post("/api/v1/reports", headers=headers, json=payload)
        assert created.status_code == 201, created.text
        ids.append(created.json()["id"])
    return ids


def test_publication_requires_three_distinct_verified_reports(client):
    make_user("modo2@example.com", role="moderator")
    headers_modo = auth_headers(client, "modo2@example.com")
    target = "+22365544332"
    ids = _create_pending_reports(client, ["a1@example.com", "a2@example.com"], target)
    for rid in ids:
        client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                    json={"decision": "verify", "reason": "Preuve cohérente et vérifiable."})

    with SessionLocal() as db:
        tgt = db.query(Target).filter(Target.phone_fp == fingerprint(target)).one()
        assert tgt.verified_reports == 2 and tgt.distinct_reporters == 2
        assert tgt.status != "confirmed_malicious"   # 2 < 3 donc pas publié

    third = _create_pending_reports(client, ["a3@example.com"], target, start_day=3)[0]
    client.post(f"/api/v1/moderation/reports/{third}/decision", headers=headers_modo,
                json={"decision": "verify", "reason": "Troisième preuve indépendante."})

    with SessionLocal() as db:
        tgt = db.query(Target).filter(Target.phone_fp == fingerprint(target)).one()
        assert tgt.verified_reports == 3 and tgt.distinct_reporters == 3
        assert tgt.status == "confirmed_malicious" and tgt.published_at is not None
        from app.services import reports as reports_svc

        assert reports_svc.should_escalate(db, tgt) is True

    listing = client.get("/api/v1/community/blacklist", headers=headers_modo).json()
    assert listing["total"] >= 1 and listing["min_reports_required"] == 3
    assert "ne garantissons aucune suspension" in listing["note"].lower()
    assert all(i["phone"] is None for i in listing["items"])   # jamais en clair dans la liste

    export = client.get("/api/v1/community/blacklist/export", headers=headers_modo).json()
    assert target in export["numbers"]
    assert "ne déclenche ni ne garantit" in export["disclaimer"].lower()


def test_same_reporter_cannot_escalate_alone(client):
    """Un seul plaignant, même avec trois preuves, ne suffit pas : indépendance exigée."""
    make_user("modo3@example.com", role="moderator")
    headers_modo = auth_headers(client, "modo3@example.com")
    target = "+22365544333"
    add_gateway_contact(target)
    email = "solo@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    link_device(client, headers, "Solo")
    sync_contacts(client, headers)

    ids = []
    for i in range(3):
        r = client.post("/api/v1/reports", headers=headers, json={
            "target_phone": target, "category": "spam", "occurred_at": f"2026-05-0{i + 1}T09:00:00Z",
            "description": "Série de messages publicitaires non sollicités.",
            "message_ids": [f"false_22365544333@c.us_SOLO{i}"]})
        assert r.status_code == 201, r.text
        ids.append(r.json()["id"])
    for rid in ids:
        client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                    json={"decision": "verify", "reason": "Preuves cohérentes."})

    with SessionLocal() as db:
        from app.services import reports as reports_svc

        tgt = db.query(Target).filter(Target.phone_fp == fingerprint(target)).one()
        assert tgt.verified_reports == 3 and tgt.distinct_reporters == 1
        assert tgt.status != "confirmed_malicious"
        assert reports_svc.should_escalate(db, tgt) is False


def test_dossier_is_built_and_dispatch_is_honest(client):
    """Le dossier groupé se construit réellement ; sans canal officiel il n'est pas « envoyé »."""
    make_user("modo10@example.com", role="moderator")
    headers_modo = auth_headers(client, "modo10@example.com")
    target = "+22365544335"
    ids = _create_pending_reports(client, ["d1@example.com", "d2@example.com", "d3@example.com"], target)
    for rid in ids:
        client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                    json={"decision": "verify", "reason": "Preuves concordantes et horodatées."})

    with SessionLocal() as db:
        tgt = db.query(Target).filter(Target.phone_fp == fingerprint(target)).one()
        target_id = tgt.id

    built = client.post(f"/api/v1/moderation/targets/{target_id}/dossier", headers=headers_modo)
    assert built.status_code == 200, built.text
    ref = built.json()["dossier_ref"]
    assert built.json()["reports"] == 3

    pdf = client.get(f"/api/v1/moderation/dossiers/{ref}/download", headers=headers_modo)
    assert pdf.status_code == 200
    assert pdf.content.startswith(b"%PDF")
    assert len(pdf.content) > 3000

    from app.services import jobs

    jobs.tick_sync()
    dossiers = client.get("/api/v1/moderation/dossiers", headers=headers_modo).json()
    entry = next(d for d in dossiers if d["public_ref"] == ref)
    assert entry["dispatch_status"] in ("awaiting_channel", "pending", "failed")
    assert entry["dispatch_status"] != "sent"   # aucun canal officiel configuré en test
    if entry["dispatch_status"] == "awaiting_channel":
        assert "META_ABUSE_ESCALATION_EMAIL" in (entry["dispatch_error"] or "")


# ---------------------------------------------------------------------------
# Transmission réelle (passerelle) et honnêteté des statuts
# ---------------------------------------------------------------------------
def _verified_report_for(client, email: str, moderator_email: str, target: str, day: int) -> tuple[dict, int, int]:
    make_user(moderator_email, role="moderator")
    headers_modo = auth_headers(client, moderator_email)
    make_user(email)
    headers = auth_headers(client, email)
    device_id = link_device(client, headers)
    sync_contacts(client, headers)
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": target, "category": "harassment",
        "occurred_at": f"2026-06-{day:02d}T18:30:00Z",
        "description": "Menaces répétées et insistantes par messages privés.",
        "message_ids": [f"false_{target.strip('+')}@c.us_MENACES{day}"]})
    assert created.status_code == 201, created.text
    report_id = created.json()["id"]
    decided = client.post(f"/api/v1/moderation/reports/{report_id}/decision", headers=headers_modo,
                          json={"decision": "verify", "reason": "Preuves et identifiants de messages valides."})
    assert decided.status_code == 200, decided.text
    return headers, device_id, report_id


def test_submission_executes_through_gateway(client):
    headers, device_id, report_id = _verified_report_for(
        client, "appelant@example.com", "modo4@example.com", "+22361234567", 1)

    no_ack = client.post(f"/api/v1/reports/{report_id}/submit", headers=headers,
                         data={"adapter": "user_native", "device_id": device_id, "manual_ack": "false"})
    assert no_ack.status_code == 400
    assert "avertissement" in no_ack.json()["detail"].lower()

    queued = client.post(f"/api/v1/reports/{report_id}/submit", headers=headers,
                         data={"adapter": "user_native", "device_id": device_id, "manual_ack": "true"})
    assert queued.status_code == 200, queued.text
    assert queued.json()["queued"] is True and queued.json()["adapter"] == "user_native"

    import asyncio

    from app.services import reports as reports_svc

    with SessionLocal() as db:
        for _ in range(4):
            sub = db.query(ReportSubmission).filter(ReportSubmission.report_id == report_id).one()
            if sub.status != SubmissionStatus.QUEUED:
                break
            asyncio.run(reports_svc.execute_submission(db, sub))
            db.commit()

    with SessionLocal() as db:
        sub = db.query(ReportSubmission).filter(ReportSubmission.report_id == report_id).one()
        assert sub.status == SubmissionStatus.SUCCEEDED, sub.error
        assert sub.adapter == "user_native"
        assert "wa-report-1" in (sub.response_summary or "")
        assert db.get(Report, report_id).status == ReportStatus.SUBMITTED


def test_unverified_report_cannot_be_submitted(client):
    headers, device_id, report_id = _verified_report_for(
        client, "nonverifie@example.com", "modo11@example.com", "+22361234567", 2)
    with SessionLocal() as db:
        report = db.get(Report, report_id)
        report.status = ReportStatus.PENDING_VERIFICATION
        db.commit()
    resp = client.post(f"/api/v1/reports/{report_id}/submit", headers=headers,
                       data={"adapter": "user_native", "device_id": device_id, "manual_ack": "true"})
    assert resp.status_code == 409
    assert "vérifié" in resp.json()["detail"]


def test_gateway_failure_is_reported_and_not_faked(client, monkeypatch):
    headers, device_id, report_id = _verified_report_for(
        client, "echec@example.com", "modo5@example.com", "+22361234567", 3)
    queued = client.post(f"/api/v1/reports/{report_id}/submit", headers=headers,
                         data={"adapter": "user_native", "device_id": device_id, "manual_ack": "true"})
    assert queued.status_code == 200, queued.text

    from app.services.connector import get_connector
    from app.services import reports as reports_svc

    connector = get_connector()
    original = connector.base_url
    connector.base_url = "http://127.0.0.1:9"   # port fermé : la passerelle est injoignable
    try:
        import asyncio

        with SessionLocal() as db:
            sub = db.query(ReportSubmission).filter(ReportSubmission.report_id == report_id).one()
            asyncio.run(reports_svc.execute_submission(db, sub))
            db.commit()
    finally:
        connector.base_url = original

    with SessionLocal() as db:
        sub = db.query(ReportSubmission).filter(ReportSubmission.report_id == report_id).one()
        assert sub.status in (SubmissionStatus.QUEUED, SubmissionStatus.FAILED)
        assert sub.status != SubmissionStatus.SUCCEEDED
        assert sub.error and "injoignable" in sub.error.lower()
        assert db.get(Report, report_id).status != ReportStatus.SUBMITTED


def test_manual_guided_path_when_gateway_lacks_native_report(client):
    """Si la passerelle ne sait pas signaler, le serveur le dit au lieu de mentir."""
    from tests.fake_gateway import Handler

    headers, device_id, report_id = _verified_report_for(
        client, "guide@example.com", "modo6@example.com", "+22361234567", 4)

    original = Handler.capabilities
    Handler.capabilities = {"report_native": False, "block_contact": True, "contact_sync": True}
    try:
        resp = client.post(f"/api/v1/reports/{report_id}/submit", headers=headers,
                           data={"adapter": "user_native", "device_id": device_id, "manual_ack": "true"})
        assert resp.status_code == 200, resp.text
        body = resp.json()
        assert body["queued"] is False and body["fallback"] == "manual_guided"
        assert len(body["steps"]) == 5
    finally:
        Handler.capabilities = original

    done = client.post(f"/api/v1/reports/{report_id}/mark-manual-done", headers=headers,
                       data={"done": "true", "note": "Signalé dans WhatsApp"})
    assert done.status_code == 200
    with SessionLocal() as db:
        sub = db.query(ReportSubmission).filter(ReportSubmission.report_id == report_id).one()
        assert sub.adapter == "manual_guided"


def test_business_link_requires_real_meta_credentials(client):
    make_user("pro@example.com")
    headers = auth_headers(client, "pro@example.com")
    resp = client.post("/api/v1/devices/business/link", headers=headers,
                       json={"waba_id": "123", "phone_number_id": "456", "access_token": "faux-jeton"})
    assert resp.status_code == 400
    assert "meta a refusé" in resp.json()["detail"].lower()


# ---------------------------------------------------------------------------
# Import CSV / Excel
# ---------------------------------------------------------------------------
def test_csv_import_rejects_rows_without_evidence(client, prepared_user):
    headers, _ = prepared_user
    csv_content = (
        "numero,categorie,date,description,preuve\n"
        '+22361234567,arnaque financière,2026-03-12 14:22,"Demande d\'acompte Wave pour colis",capture1.png\n'
        '+22367788990,spam,2026-03-14 09:05,"Publicité non sollicitée sans preuve",\n'
        '+22369998877,,2026-03-15 10:00,"Sans catégorie",capture3.png\n'
        "invalide,spam,,Trop court,x.png\n"
    )
    preview = client.post("/api/v1/reports/import/preview", headers=headers,
                          files={"file": ("signalements.csv", csv_content.encode(), "text/csv")})
    assert preview.status_code == 200, preview.text
    body = preview.json()
    assert body["total_rows"] == 4 and body["valid_rows"] == 1 and body["rejected_rows"] == 3
    reasons = json.dumps(body["rows"], ensure_ascii=False)
    assert "Preuve manquante" in reasons
    assert "Catégorie d'infraction manquante" in reasons
    assert "Numéro invalide" in reasons

    commit = client.post("/api/v1/reports/import/commit", headers=headers,
                         files={"file": ("signalements.csv", csv_content.encode(), "text/csv")})
    assert commit.status_code == 200
    assert commit.json()["committed"] is True
    assert len(commit.json()["created_report_ids"]) == 1

    template = client.get("/api/v1/reports/import/template", headers=headers)
    assert "numero,categorie,date,description,preuve" in template.text


def test_csv_import_missing_columns_fails_loudly(client, prepared_user):
    headers, _ = prepared_user
    resp = client.post("/api/v1/reports/import/preview", headers=headers,
                       files={"file": ("mauvais.csv", b"a,b\n1,2\n", "text/csv")})
    assert resp.status_code == 422
    assert "introuvable" in resp.json()["detail"] or "refusé" in resp.json()["detail"]


def test_csv_import_accepts_french_headers_with_accents(client, prepared_user):
    """Un vrai tableur francophone utilise « Date de réception », « Numéro », « Catégorie »."""
    headers, _ = prepared_user
    csv_content = (
        "Numéro;Catégorie;Date de réception;Description;Pièce jointe\n"
        '+22361234567;arnaque financière;2026-03-12 14:22;"Faux vendeur exigeant un acompte Wave";capture_20260312.png\n'
    )
    resp = client.post("/api/v1/reports/import/preview", headers=headers,
                       files={"file": ("releve.csv", csv_content.encode(), "text/csv")})
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["valid_rows"] == 1, json.dumps(body["rows"], ensure_ascii=False)
    assert sorted(body["columns_detected"]) == [
        "category",
        "description",
        "evidence_ref",
        "occurred_at",
        "target_phone",
    ]


def test_excel_import_supported(client, prepared_user):
    import openpyxl

    headers, _ = prepared_user
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.append(["numero", "categorie", "date", "description", "preuve"])
    ws.append(["+22361234567", "arnaque financière", "2026-03-12 14:22",
               "Demande de paiement mobile money pour un colis fantôme", "capture.png"])
    ws.append(["+22367788990", "harcelement", "2026-03-13 08:00",
               "Menaces répétées sans preuve jointe", ""])
    buf = io.BytesIO()
    wb.save(buf)
    resp = client.post("/api/v1/reports/import/preview", headers=headers,
                       files={"file": ("signalements.xlsx", buf.getvalue(),
                                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")})
    assert resp.status_code == 200, resp.text
    assert resp.json()["valid_rows"] == 1 and resp.json()["rejected_rows"] == 1


# ---------------------------------------------------------------------------
# Détection
# ---------------------------------------------------------------------------
def test_spam_signatures_and_scan(client, prepared_user):
    headers, _ = prepared_user
    sigs = client.get("/api/v1/detect/signatures", headers=headers).json()
    assert sigs["count"] >= 30
    assert any("frais de douane" in s["value_display"] for s in sigs["signatures"])

    suspicious = client.post("/api/v1/detect/scan", headers=headers, json={
        "text": "Bonjour, votre colis est bloqué à la douane. Payez les frais de douane via Wave puis "
                "cliquez ici pour recevoir le colis. https://free-recharge.top/x",
        "peer_phone": "+22361234567"})
    assert suspicious.status_code == 200, suspicious.text
    assert suspicious.json()["is_suspicious"] is True
    assert suspicious.json()["score"] >= 4 and suspicious.json()["advice"]

    clean = client.post("/api/v1/detect/scan", headers=headers,
                        json={"text": "Salut, on se voit demain à 18h pour le match ?"})
    assert clean.json()["is_suspicious"] is False

    version = client.get("/api/v1/detect/signatures", headers=headers,
                         params={"since_version": sigs["version"]}).json()
    assert version["count"] == 0   # rien de nouveau : l'app mobile peut rester hors ligne


def test_detection_converts_to_report_without_shortcut(client, prepared_user):
    headers, _ = prepared_user
    hit = client.post("/api/v1/detect/scan", headers=headers,
                      json={"text": "Vous avez gagné 500 000 FCFA, envoyez votre numéro de carte.",
                            "peer_phone": "+22361234567"}).json()
    with SessionLocal() as db:
        from app.models import DetectionHit

        hit_id = db.query(DetectionHit).order_by(DetectionHit.id.desc()).first().id

    converted = client.post(f"/api/v1/detect/hits/{hit_id}/convert", headers=headers,
                            params={"target_phone": "+22361234567", "category": "financial_scam",
                                    "occurred_at": "2026-03-25 09:00", "description": "Arnaque au gain " * 2})
    assert converted.status_code == 200, converted.text
    report_id = converted.json()["report_id"]
    detail = client.get(f"/api/v1/reports/{report_id}", headers=headers).json()
    assert detail["status"] == ReportStatus.PENDING_EVIDENCE   # la détection ne remplace pas la preuve
    assert hit["is_suspicious"] is True


# ---------------------------------------------------------------------------
# Campagnes
# ---------------------------------------------------------------------------
def test_campaign_preview_is_honest_about_real_capacity(client, prepared_user):
    headers, _ = prepared_user
    preview = client.post("/api/v1/campaigns/preview", headers=headers, json={
        "target_phone": "+22369991122", "requested_count": 50, "category": "spam",
        "occurred_at": "2026-03-20T10:00:00Z", "description": "Numéro inconnu de tous les comptes liés.",
        "consent_ack": True})
    assert preview.status_code == 200
    assert preview.json()["eligible_accounts"] == 0
    assert preview.json()["executable_count"] == 0
    assert preview.json()["blocking_reason"]


def test_campaign_cannot_invent_reports(client, prepared_user):
    headers, _ = prepared_user
    created = client.post("/api/v1/campaigns", headers=headers, json={
        "target_phone": "+22369991122", "requested_count": 50, "category": "spam",
        "occurred_at": "2026-03-20T10:00:00Z", "description": "Tentative de campagne sans base réelle.",
        "consent_ack": True})
    assert created.status_code == 422
    with SessionLocal() as db:
        assert db.query(Campaign).count() == 0


def test_campaign_requires_consent_ack(client, prepared_user):
    headers, _ = prepared_user
    resp = client.post("/api/v1/campaigns", headers=headers, json={
        "target_phone": "+22361234567", "requested_count": 5, "category": "spam",
        "occurred_at": "2026-03-21T10:00:00Z", "description": "Campagne sans accusé de lecture.",
        "consent_ack": False})
    assert resp.status_code == 422
    assert "avertissement" in resp.json()["detail"].lower()


def test_campaign_runs_on_real_eligible_accounts(client):
    """Deux comptes réellement contactés : la campagne produit deux signalements distincts."""
    target = "+22365544336"
    add_gateway_contact(target)
    emails = []
    for i in range(2):
        email = f"camp{i}@example.com"
        make_user(email)
        emails.append(email)
        link_device(client, auth_headers(client, email))
        sync_contacts(client, auth_headers(client, email))
    headers = auth_headers(client, emails[0])

    preview = client.post("/api/v1/campaigns/preview", headers=headers, json={
        "target_phone": target, "requested_count": 20, "category": "financial_scam",
        "occurred_at": "2026-03-22T10:00:00Z", "description": "Arnaque récurrente ciblant plusieurs comptes.",
        "consent_ack": True})
    assert preview.json()["eligible_accounts"] == 2
    assert preview.json()["executable_count"] == 2

    created = client.post("/api/v1/campaigns", headers=headers, json={
        "target_phone": target, "requested_count": 20, "category": "financial_scam",
        "occurred_at": "2026-03-22T10:00:00Z", "description": "Arnaque récurrente ciblant plusieurs comptes.",
        "consent_ack": True})
    assert created.status_code == 201, created.text
    body = created.json()
    assert body["requested_count"] == 20
    assert body["eligible_count"] == 2
    assert body["executed_count"] == 2       # jamais 20 : aucune fabrication de signalements
    assert "ne crée jamais de compte fictif" in body["eligibility_explanation"]

    listing = client.get("/api/v1/campaigns", headers=headers).json()
    assert any(c["public_ref"] == body["public_ref"] for c in listing)

    cancelled = client.post(f"/api/v1/campaigns/{body['id']}/cancel", headers=headers)
    assert cancelled.status_code == 200
    assert cancelled.json()["status"] in ("aborted", "partial", "completed")


# ---------------------------------------------------------------------------
# Contestation et retrait
# ---------------------------------------------------------------------------
def test_appeal_and_clear_target(client):
    make_user("modo7@example.com", role="moderator")
    headers_modo = auth_headers(client, "modo7@example.com")
    target = "+22365544334"
    ids = _create_pending_reports(client, ["b1@example.com", "b2@example.com", "b3@example.com"], target)
    for rid in ids:
        client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                    json={"decision": "verify", "reason": "Preuves concordantes."})

    with SessionLocal() as db:
        tgt = db.query(Target).filter(Target.phone_fp == fingerprint(target)).one()
        assert tgt.status == "confirmed_malicious"
        target_id = tgt.id

    appeal = client.post("/api/v1/community/appeals", json={
        "target_phone": target, "claimant_contact": "proprietaire@example.com",
        "statement": "Je conteste : mon numéro a été usurpé, les captures ne viennent pas de ma ligne.",
        "evidence_note": "Relevé opérateur joint par email."})
    assert appeal.status_code == 201, appeal.text
    ref = appeal.json()["public_ref"]
    assert client.get(f"/api/v1/community/appeals/{ref}").status_code == 200

    duplicate = client.post("/api/v1/community/appeals", json={
        "target_phone": target, "claimant_contact": "proprietaire@example.com",
        "statement": "Deuxième contestation simultanée sur le même numéro."})
    assert duplicate.status_code == 409

    queue = client.get("/api/v1/moderation/appeals", headers=headers_modo).json()
    entry = next(a for a in queue if a["public_ref"] == ref)
    decision = client.post(f"/api/v1/moderation/appeals/{entry['id']}/decision", headers=headers_modo,
                           json={"decision": "clear_target", "reason": "Usurpation confirmée par relevé opérateur."})
    assert decision.status_code == 200, decision.text

    with SessionLocal() as db:
        tgt = db.get(Target, target_id)
        assert tgt.status == "cleared" and tgt.delisted_at is not None

    listing = client.get("/api/v1/community/blacklist", headers=headers_modo).json()
    assert all(i["status"] != "cleared" for i in listing["items"])


# ---------------------------------------------------------------------------
# Webhooks Meta
# ---------------------------------------------------------------------------
def test_webhook_signature_enforced(client):
    bad = client.post("/api/v1/webhooks/whatsapp", content=b'{"entry":[]}',
                      headers={"x-hub-signature-256": "sha256=deadbeef"})
    assert bad.status_code == 401
    assert client.get("/api/v1/webhooks/whatsapp",
                      params={"hub.mode": "subscribe", "hub.verify_token": "mauvais",
                              "hub.challenge": "12345"}).status_code == 403
    ok = client.get("/api/v1/webhooks/whatsapp",
                    params={"hub.mode": "subscribe", "hub.verify_token": "test-verify-token",
                            "hub.challenge": "12345"})
    assert ok.status_code == 200 and ok.text == "12345"


def test_webhook_account_update_is_recorded(client):
    import hashlib
    import hmac

    payload = json.dumps({"entry": [{"id": "waba1", "changes": [
        {"field": "account_update", "value": {"phone_number_id": "PN1", "event": "ACCOUNT_RESTRICTION"}}]}]}).encode()
    sig = "sha256=" + hmac.new(b"test-app-secret", payload, hashlib.sha256).hexdigest()
    resp = client.post("/api/v1/webhooks/whatsapp/simulate", content=payload,
                       headers={"x-hub-signature-256": sig, "content-type": "application/json"})
    assert resp.status_code == 200, resp.text
    assert resp.json()["handled"] == 1
    with SessionLocal() as db:
        assert any(a.action == "webhook.account_update" for a in db.query(AuditLog).all())


# ---------------------------------------------------------------------------
# Communauté : blocage réel via la passerelle
# ---------------------------------------------------------------------------
def test_block_all_executes_real_actions(client, prepared_user):
    headers, device_id = prepared_user
    resp = client.post("/api/v1/community/block-all", headers=headers,
                       json={"device_id": device_id, "max_numbers": 10, "consent_ack": True})
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["device_status"] == "connected"
    if body["requested"] > 0:
        assert body["blocked"] == body["requested"] and all(d["ok"] for d in body["details"])

    no_ack = client.post("/api/v1/community/block-all", headers=headers,
                         json={"device_id": device_id, "max_numbers": 10, "consent_ack": False})
    assert no_ack.status_code == 400


def test_block_all_requires_existing_device(client):
    make_user("blocage@example.com")
    headers = auth_headers(client, "blocage@example.com")
    resp = client.post("/api/v1/community/block-all", headers=headers,
                       json={"device_id": 999999, "max_numbers": 5, "consent_ack": True})
    assert resp.status_code == 404


# ---------------------------------------------------------------------------
# Tableau de bord, export, notifications, alertes
# ---------------------------------------------------------------------------
def test_dashboard_and_export(client, prepared_user):
    headers, _ = prepared_user
    overview = client.get("/api/v1/dashboard/overview", headers=headers)
    assert overview.status_code == 200
    body = overview.json()
    assert "usage" in body and "devices" in body and "reports" in body
    assert "Meta" in body["suspensions"]["note"]

    export = client.get("/api/v1/dashboard/export/reports", headers=headers)
    assert export.status_code == 200
    assert "reference,numero_cible" in export.text

    notifications = client.get("/api/v1/dashboard/notifications", headers=headers).json()
    assert isinstance(notifications, list)
    if notifications:
        marked = client.post("/api/v1/dashboard/notifications/read", headers=headers)
        assert marked.status_code == 200


def test_alerts_detect_malicious_contact(client):
    make_user("modo8@example.com", role="moderator")
    headers_modo = auth_headers(client, "modo8@example.com")
    target = "+22367788990"   # présent dans les conversations synchronisées de la passerelle
    ids = _create_pending_reports(client, ["c1@example.com", "c2@example.com", "c3@example.com"], target)
    for rid in ids:
        client.post(f"/api/v1/moderation/reports/{rid}/decision", headers=headers_modo,
                    json={"decision": "verify", "reason": "Preuves solides."})

    email = "alerte@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    link_device(client, headers, "Alerte")
    sync_contacts(client, headers)

    alerts = client.get("/api/v1/dashboard/alerts", headers=headers).json()
    assert any(a["verified_reports"] >= 3 for a in alerts), alerts
    scanned = client.post("/api/v1/dashboard/alerts/scan", headers=headers).json()
    assert scanned["matches"] >= 1


def test_community_stats_are_honest(client, prepared_user):
    headers, _ = prepared_user
    stats = client.get("/api/v1/community/stats", headers=headers).json()
    assert "confirmed" in json.dumps(stats).lower()
    assert "ne compte que les cas confirmés" in stats["honesty_note"].lower()


# ---------------------------------------------------------------------------
# Suppression de compte (RGPD)
# ---------------------------------------------------------------------------
def test_account_deletion_removes_evidence(client):
    email = "suppression@example.com"
    make_user(email)
    headers = auth_headers(client, email)
    link_device(client, headers, "Suppr")
    report = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-07-01T09:00:00Z",
        "description": "Messages non sollicités à répétition.",
        "message_ids": ["false_22361234567@c.us_SUPPR"]}).json()
    client.post(f"/api/v1/reports/{report['id']}/evidence", headers=headers, data={"kind": "screenshot"},
                files={"file": ("c.png", make_png(seed=99), "image/png")})

    with SessionLocal() as db:
        user_id = db.query(User).filter(User.email_fp == fingerprint(email)).one().id
        assert db.query(Evidence).join(Report).filter(Report.reporter_id == user_id).count() == 2

    assert client.request("DELETE", "/api/v1/auth/account", headers=headers,
                          json={"password": "MotDePasse123", "confirm": "NON"}).status_code == 400

    deleted = client.request("DELETE", "/api/v1/auth/account", headers=headers,
                             json={"password": "MotDePasse123", "confirm": "SUPPRIMER"})
    assert deleted.status_code == 200, deleted.text
    assert deleted.json()["evidence_deleted"] >= 1

    with SessionLocal() as db:
        assert db.query(User).filter(User.id == user_id).one_or_none() is None
        assert db.query(Report).filter(Report.reporter_id == user_id).count() == 0
        assert db.query(Evidence).join(Report, isouter=True).filter(Report.reporter_id == user_id).count() == 0
        assert db.query(AuditLog).filter(AuditLog.action == "user.account_deleted").count() >= 1


# ---------------------------------------------------------------------------
# Sécurité : rôles, jetons, accès croisés, chiffrement
# ---------------------------------------------------------------------------
def test_rbac_and_token_enforcement(client, prepared_user):
    headers, _ = prepared_user
    assert client.get("/api/v1/moderation/queue", headers=headers).status_code == 403
    assert client.get("/api/v1/reports/usage").status_code == 401
    assert client.get("/api/v1/reports/usage",
                      headers={"Authorization": "Bearer faux.jeton.xyz"}).status_code == 401
    assert client.get("/api/v1/moderation/queue").status_code == 401


def test_cross_user_access_denied(client):
    headers = _fresh_linked_account(client, "proprietaire@example.com")
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-08-01T09:00:00Z",
        "description": "Messages non sollicités répétés."})
    assert created.status_code == 201, created.text
    mine = created.json()
    email = "autre@example.com"
    make_user(email)
    other = auth_headers(client, email)
    assert client.get(f"/api/v1/reports/{mine['id']}", headers=other).status_code == 404
    assert client.get(f"/api/v1/reports/{mine['id']}/export", headers=other).status_code == 404


def test_phone_numbers_encrypted_at_rest(client):
    make_user("chiffre@example.com", phone="+22366999888")
    with SessionLocal() as db:
        user = db.query(User).filter(User.email_fp == fingerprint("chiffre@example.com")).one()
        assert user.phone_enc.startswith("v1:") and "+22366999888" not in user.phone_enc
        assert user.email_enc.startswith("v1:") and "chiffre@example.com" not in user.email_enc
        assert user.phone_fp and user.phone_fp != user.phone_enc


def _fresh_linked_account(client, email: str) -> dict:
    """Compte neuf avec WhatsApp lié : chaque compte a son propre quota."""
    make_user(email)
    headers = auth_headers(client, email)
    link_device(client, headers)
    sync_contacts(client, headers)
    return headers


def test_message_content_not_stored_without_consent(client):
    headers = _fresh_linked_account(client, "sansconsent@example.com")
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-08-03T09:00:00Z",
        "description": "Contrôle de non-stockage du contenu sans consentement.",
        "store_messages": False, "message_excerpt": "Texte de message qui ne doit PAS être conservé."})
    assert created.status_code == 201
    with SessionLocal() as db:
        from app.models import MessageSnapshot

        assert db.query(MessageSnapshot).filter(MessageSnapshot.report_id == created.json()["id"]).count() == 0
        # Les extraits de détection ne sont jamais stockés non plus
        from app.models import DetectionHit

        assert all(h.matched_sample is None for h in db.query(DetectionHit).all())


def test_message_stored_only_with_explicit_consent(client):
    headers = _fresh_linked_account(client, "avecconsent@example.com")
    created = client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-08-04T09:00:00Z",
        "description": "Contrôle du stockage avec consentement explicite.",
        "store_messages": True, "message_excerpt": "Message conservé car consentement donné."})
    assert created.status_code == 201
    with SessionLocal() as db:
        from app.models import MessageSnapshot

        rows = db.query(MessageSnapshot).filter(MessageSnapshot.report_id == created.json()["id"]).all()
        assert len(rows) == 1
        assert rows[0].consent_granted is True
        assert rows[0].body_enc.startswith("v1:")   # chiffré, jamais en clair


def test_audit_trail_written_for_critical_actions(client, prepared_user):
    headers, _ = prepared_user
    client.post("/api/v1/reports", headers=headers, json={
        "target_phone": "+22361234567", "category": "spam", "occurred_at": "2026-08-02T09:00:00Z",
        "description": "Contrôle de la journalisation d'audit."})
    with SessionLocal() as db:
        actions = [a.action for a in db.query(AuditLog).order_by(AuditLog.id.desc()).limit(80)]
        assert "report.created" in actions and "report.requested" in actions
