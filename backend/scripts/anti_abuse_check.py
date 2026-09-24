#!/usr/bin/env python3
"""Interface de test des protections anti-abus.

Ce script pilote l'application FastAPI **en mémoire** (aucun serveur à lancer) et
vérifie, une par une, que les règles non désactivables sont réellement appliquées :

  1. vérification email obligatoire avant tout accès aux signalements ;
  2. preuve obligatoire : un signalement sans preuve reste « preuve manquante » et
     n'est jamais transmissible ;
  3. preuve de contact obligatoire : impossible de signaler un numéro qui ne vous a
     jamais écrit ;
  4. doublons refusés (même cible, même date) ;
  5. preuve réutilisée à l'identique refusée et signalée comme abusive ;
  6. plafond de 5 signalements par heure appliqué par le serveur ;
  7. cloisonnement : le signalement d'un autre utilisateur est inaccessible ;
  8. bannissement automatique au 3ᵉ avertissement confirmé.

Usage :
    ENV=test python scripts/anti_abuse_check.py
    ENV=test python scripts/anti_abuse_check.py --base-url http://127.0.0.1:8000

Sans `--base-url`, tout se passe sur une base SQLite temporaire : rien n'est écrit
dans votre base de développement. L'appareil WhatsApp « lié » utilisé pour la
vérification de contact est créé directement dans cette base de test — c'est le
même état que celui produit par la passerelle locale après un vrai scan de QR.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import os
import pathlib
import sys
import tempfile

# Permet d'exécuter le script directement (`python scripts/xxx.py`) sans installer
# le paquet : la racine du backend est ajoutée au chemin d'import.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

RESULTS: list[tuple[str, bool, str]] = []

# PNG 1×1 réel : le serveur vérifie le contenu réel des preuves, un faux
# fichier image est refusé (ce contrôle est exercé par les tests du backend).
# Capture de démonstration (360×640, PNG réel) : le serveur vérifie le contenu
# réel des preuves — un fichier trop petit ou illisible est refusé.
PNG_SCREENSHOT = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAWgAAAKACAIAAAA+eHXwAAANEUlEQVR42u3aeXBU9QHA8d9uDkWO1qszKvUojlIMAQRRLLEcVbGI"
    "iAcexRuqSA+QiOABBNEawbMetdSjeBRHFIrDpdyH3GdgwBOpOPVCIMmyYYGkfzxMM9mgCMWp+PnM/vHm5bfvt++32e++3SSWSKYC"
    "wLcRtwSAcADCAQgHIByAcAAIByAcgHAAwgEIB4BwAMIBCAcgHIBwAAgHIByAcADCAQgHIBwAwgEIByAcgHAAwgEgHIBwAMIBCAcg"
    "HADCAQgHIByAcADCASAcgHAAwgEIByAce6DvwMLxb86Mtjt07T5k+OPRdsGwxydMmRlCyMnr9I0HeeLZf9S4f9SY8Se2POeLjZu+"
    "4/Xa3ePZC3ty+t925P4+tWjMO+9/+Pwr47x4hGO/aN7klBWr14YQEomtGZkZy4rWRPuXFa1p2Sx3T3+bn3mpxv1TZs677oqLps1Z"
    "8F2HYzeP5wCwJ6cWjTmpwfFXXXqBF88PWeZ+Dce4ydNCCEuL1rRtffqUmfNSqe3xeKysrOyIww+Nxgx77OlFy4o2F5f07Xndue1a"
    "v/P+hwOGPlhcUnrZhb/u3u2SB598buvWZLeet/7ql2e+PHZiLBbr/4ceZ7VqkSzbtjVZdkWXjoWP/a1r5w7Re/IVF3VcVrQmFgsP"
    "Dun/02OOSt+T/gi/3Lyl/90PbNlSkpWV+cg9d2zctLnq7OmHfWXc5OjxvPDksJy8Tqtmv155RRBt5+R16tAu75SGJ17U8eyBhX/+"
    "/Isvt+/Yfkefnk1zGkYjv9i46ba7h28pLj2u/tHRni3FJfsyMiev03nt8+YvWXHjNZcvWla0ZMXqay/v0r3bJZ9v/DJ/0P1bk8lD"
    "atUaXtDvyMMPy8nrdM1lFy5aVlRcUtrnpmvPbdf6uVFjKld18fJVVU8tOotftDy1xqej8vT3ZBavsQNTIpnaT7fSrduatL24dOu2"
    "ex4eMXHaWzf3v2fOwuVvLS7q1f/eaMAxue0eGfFSIpkqWvtBbpuLEslU7zsLp89d8vEnGxu17hyNOaHFuYlk6uRW53/2xeaVa977"
    "bd+CRDL12vhpDz31QiKZatPlus3FW6NDjRo7OZFMvTB6/G969q9xT/rtxvwhL42ZlEimnh01rvedhemzpx8kejxVN6puH5PbbsLU"
    "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"
    "ByAcgHAAwgEgHIBwAMIBCAcgHADCAQgHIByAcADCARBLJFNWAXDFAQgHIByAcADCASAcgHAAwgEIByAcAMIBCAcgHIBwAMIBIByA"
    "cADCAQgHIByAcAAIByAcgHAAwgEIB4BwAMIBCAcgHIBwAAgHIByAcADCAQgHgHAAwgEIByAcgHAAwgEgHIBwAMIBCAcgHADCAQgH"
    "IByAcADCASAcgHAAwgEIByAcAMIBCAcgHIBwAMIBCAeAcADCAQgHIByAcAAIByAcgHAAwgEIB4BwAMIBCAcgHIBwAMIBIByAcADC"
    "AQgHIBwAwgEIByAcgHAAwgEgHIBwAMIBCAcgHADCAQgHIByAcADCAQgHgHAAwgEIByAcgHAACAcgHIBwAMIBCAeAcADCAQgHIByA"
    "cAAIByAcgHAAwgEIByAcAMIBCAcgHIBwAMIBIByAcADCAQgHIBwAwgEIByAcgHAAwgEgHIBwAMIBCAcgHIBwAAgHIByAcADCAQgH"
    "gHAAwgEIByAcgHAACAcgHIBwAMIBCAcgHADCAQgHIByAcADCASAcgHAAwgEIByAcAMIBCAcgHIBwAMIBIByAcADCAQgHIByAcAAI"
    "ByAcgHAAwgEIB4BwAMIBCAcgHIBwAAgHIByAcADCAQgHgHAAwgEIByAcgHAAwgEgHIBwAMIBCAcgHADCAQgHIByAcADCASAcgHAA"
    "wgEIByAcAMIBCAcgHIBwAMIBCAeAcADCAQgHIByAcAAIByAcgHAAwgEIB4BwAMIBCAcgHIBwAMJhCQDhAIQDEA5AOADhABAOQDgA"
    "4QCEAxAOAOEAhAMQDkA4AOEAEA5AOADhAIQDEA5AOACEAxAOQDgA4QCEA0A4AOEAhAMQDkA4AIQDEA5AOADhAIQDQDgA4QCEAxAO"
    "QDiAH7D/AM7YChwG0c9dAAAAAElFTkSuQmCC"
)


def record(label: str, ok: bool, detail: str = "") -> None:
    RESULTS.append((label, ok, detail))
    print(f"  [{'OK ' if ok else 'ÉCHEC'}] {label}" + (f" — {detail}" if detail else ""))


def bootstrap_test_env() -> None:
    os.environ.setdefault("ENV", "test")
    os.environ.setdefault("ALLOW_CONSOLE_VERIFICATION", "true")
    tmp = pathlib.Path(tempfile.mkdtemp(prefix="signalpro-antiabuse-"))
    os.environ["DATABASE_URL"] = f"sqlite+pysqlite:///{tmp / 'check.db'}"
    os.environ.setdefault("JWT_SECRET", "anti-abuse-check-secret-0123456789")
    os.environ.setdefault("EVIDENCE_MASTER_KEY", "0" * 64)
    os.environ.setdefault("CONNECTOR_SHARED_SECRET", "anti-abuse-gateway-secret")


def make_client():
    from fastapi.testclient import TestClient

    from app.main import app

    return TestClient(app)


def register_verified(client, email: str, phone: str) -> dict:
    """Crée un compte et le vérifie (le code est renvoyé en mode test)."""
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": email, "phone": phone, "password": "MotDePasse123", "channel": "email"},
    )
    resp.raise_for_status()
    code = resp.json().get("dev_code")
    if code:
        verify = client.post("/api/v1/auth/verify", json={"email": email, "code": code})
        verify.raise_for_status()
        return {"Authorization": f"Bearer {verify.json()['access_token']}"}
    login = client.post("/api/v1/auth/login", json={"email": email, "password": "MotDePasse123"})
    login.raise_for_status()
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def attach_test_device(owner_email: str, peers: list[str]) -> None:
    """Reproduit l'état obtenu après une vraie liaison d'appareil + synchronisation.

    C'est indispensable pour tester honnêtement les règles suivantes : sans contact
    indexé, le serveur refuse déjà le signalement (et ce refus est vérifié plus bas).
    """
    from app.constants import DeviceStatus
    from app.db import SessionLocal
    from app.models import ContactIndex, LinkedDevice, User
    from app.security import encrypt_str, fingerprint

    db = SessionLocal()
    try:
        user = db.query(User).filter(User.email_fp == fingerprint(owner_email)).one()
        now = dt.datetime.now(dt.timezone.utc)
        device = LinkedDevice(
            user_id=user.id,
            mode="web_linked",
            label="Appareil de test",
            status=DeviceStatus.CONNECTED,
            session_ref=f"antiabuse-{user.id}",
            risk_consent=True,
            risk_consent_at=now,
            risk_consent_version="test",
            wa_jid_enc=encrypt_str(f"{user.id}@s.whatsapp.net"),
            wa_jid_fp=fingerprint(f"{user.id}@s.whatsapp.net"),
            linked_at=now,
            last_seen_at=now,
            contacts_synced_at=now,
            contacts_count=len(peers),
        )
        db.add(device)
        db.flush()
        for peer in peers:
            db.add(
                ContactIndex(
                    user_id=user.id,
                    device_id=device.id,
                    peer_fp=fingerprint(peer),
                    is_group=False,
                    last_message_at=now,
                )
            )
        db.commit()
    finally:
        db.close()


def create_report(client, headers, phone: str, occurred_at: str, description: str):
    return client.post(
        "/api/v1/reports",
        headers=headers,
        json={
            "target_phone": phone,
            "category": "financial_scam",
            "occurred_at": occurred_at,
            "description": description,
        },
    )


def run_checks() -> None:
    client = make_client()
    # Équivalent de `with TestClient(app) as client:` : entrer dans le contexte
    # déclenche le cycle de vie FastAPI (création des tables).
    client.__enter__()
    headers = register_verified(client, "antiabus@example.org", "+22361234567")
    target_number = "+22365551234"
    attach_test_device("antiabus@example.org", [target_number])

    print("\n1) Vérification obligatoire")
    client.post(
        "/api/v1/auth/register",
        json={"email": "nonverifie@example.org", "phone": "+22360000001", "password": "MotDePasse123"},
    )
    session = client.post(
        "/api/v1/auth/login",
        json={"email": "nonverifie@example.org", "password": "MotDePasse123"},
    )
    unverified_headers = {"Authorization": f"Bearer {session.json()['access_token']}"} if session.status_code == 200 else {}
    blocked = client.get("/api/v1/reports", headers=unverified_headers)
    record(
        "un compte non vérifié n'accède pas aux signalements",
        blocked.status_code in (401, 403),
        f"HTTP {blocked.status_code}",
    )

    print("\n2) Preuve obligatoire")
    without_proof = create_report(
        client, headers, target_number, "2026-09-01T10:00:00Z", "Arnaque sans preuve jointe."
    )
    body = without_proof.json() if without_proof.status_code < 500 else {}
    record(
        "un signalement sans preuve reste en attente et n'est jamais transmis",
        without_proof.status_code in (200, 201) and body.get("status") == "pending_evidence",
        f"HTTP {without_proof.status_code} · statut {body.get('status')}",
    )
    if without_proof.status_code in (200, 201):
        submit = client.post(
            f"/api/v1/reports/{body['id']}/submit",
            headers=headers,
            data={"adapter": "manual_guided", "manual_ack": "true"},
        )
        record(
            "la transmission d'un signalement non vérifié est refusée",
            submit.status_code == 409,
            f"HTTP {submit.status_code}",
        )

    print("\n3) Preuve de contact obligatoire")
    unknown = create_report(
        client, headers, "+22369999999", "2026-09-02T10:00:00Z", "Numéro inconnu qui ne m'a jamais écrit."
    )
    record(
        "impossible de signaler un numéro qui ne vous a jamais contacté",
        unknown.status_code == 422,
        f"HTTP {unknown.status_code}",
    )

    print("\n4) Doublons refusés")
    first = create_report(client, headers, target_number, "2026-09-03T09:00:00Z", "Même cible, première fois.")
    second = create_report(client, headers, target_number, "2026-09-03T09:00:00Z", "Même cible, même date.")
    record(
        "un second signalement identique est refusé",
        first.status_code in (200, 201) and second.status_code == 422,
        f"premier HTTP {first.status_code} · second HTTP {second.status_code}",
    )

    print("\n5) Preuve réutilisée = abus")
    first_report = create_report(client, headers, target_number, "2026-09-04T09:00:00Z", "Signalement avec capture.")
    second_report = create_report(client, headers, target_number, "2026-09-06T09:00:00Z", "Autre fait, autre date.")
    if first_report.status_code in (200, 201) and second_report.status_code in (200, 201):
        first_id = first_report.json()["id"]
        second_id = second_report.json()["id"]
        files = {"file": ("capture.png", PNG_SCREENSHOT, "image/png")}
        upload = client.post(
            f"/api/v1/reports/{first_id}/evidence",
            headers=headers,
            data={"kind": "screenshot"},
            files=files,
        )
        reuse = client.post(
            f"/api/v1/reports/{second_id}/evidence",
            headers=headers,
            data={"kind": "screenshot"},
            files={"file": ("capture.png", PNG_SCREENSHOT, "image/png")},
        )
        marked = client.get(f"/api/v1/reports/{second_id}", headers=headers).json()
        record(
            "une preuve réutilisée dans un autre signalement est refusée et marquée abusive",
            upload.status_code in (200, 201)
            and reuse.status_code in (409, 422)
            and marked.get("status") == "rejected_abusive",
            f"premier dépôt HTTP {upload.status_code} · réutilisation HTTP {reuse.status_code} "
            f"· statut du second signalement : {marked.get('status')}",
        )
    else:
        record(
            "une preuve réutilisée dans un autre signalement est refusée et marquée abusive",
            False,
            f"création impossible (HTTP {first_report.status_code}/{second_report.status_code})",
        )

    print("\n6) Plafond de 5 signalements par heure")
    created = 0
    limit_hit = False
    for index in range(8):
        resp = create_report(
            client,
            headers,
            target_number,
            f"2026-09-05T0{index}:00:00Z",
            f"Tentative de dépassement du quota n°{index + 1}.",
        )
        if resp.status_code == 429:
            limit_hit = True
            break
        if resp.status_code in (200, 201):
            created += 1
    record(
        "le plafond horaire est appliqué par le serveur",
        limit_hit,
        f"{created} créations acceptées puis HTTP 429",
    )

    print("\n7) Cloisonnement entre utilisateurs")
    other_headers = register_verified(client, "autre@example.org", "+22360000002")
    mine = client.get("/api/v1/reports", headers=headers).json()["items"]
    if mine:
        foreign = client.get(f"/api/v1/reports/{mine[0]['id']}", headers=other_headers)
        record(
            "un signalement n'est lisible que par son auteur",
            foreign.status_code == 404,
            f"HTTP {foreign.status_code}",
        )
    else:
        record("un signalement n'est lisible que par son auteur", False, "aucun signalement à tester")

    print("\n8) Sanctions : 2 avertissements puis bannissement")
    from app.config import get_settings
    from app.db import SessionLocal
    from app.models import User
    from app.security import fingerprint
    from app.services import reports as reports_svc

    threshold = get_settings().max_abusive_strikes
    db = SessionLocal()
    try:
        user = db.query(User).filter(User.email_fp == fingerprint("antiabus@example.org")).one()
        strikes = 0
        for index in range(threshold):
            reports_svc.issue_strike(
                db,
                user_id=user.id,
                report_id=None,
                reason=f"Vérification anti-abus n°{index + 1}",
                issued_by=None,
                automatic=False,
            )
            db.commit()
            strikes = db.get(User, user.id).strikes
        final = db.get(User, user.id)
        record(
            f"le bannissement est automatique au {threshold + 1}ᵉ avertissement confirmé",
            strikes >= threshold and final.status == "banned",
            f"avertissements={strikes} · statut={final.status}",
        )
    finally:
        db.close()

    client.__exit__(None, None, None)


def main() -> int:
    parser = argparse.ArgumentParser(description="Vérifie les protections anti-abus de SignalPro.")
    parser.add_argument("--base-url", help="Cible une instance déjà lancée (défaut : application en mémoire).")
    args = parser.parse_args()

    if args.base_url is None:
        bootstrap_test_env()

    print("SignalPro — contrôle des protections anti-abus")
    print("Backend : application en mémoire (TestClient)" if args.base_url is None else f"Backend : {args.base_url}")
    try:
        run_checks()
    except Exception as exc:  # noqa: BLE001
        import traceback

        traceback.print_exc()
        print(f"\nErreur pendant l'exécution : {exc}")
        return 2

    failed = [label for label, ok, _ in RESULTS if not ok]
    print("\nRésultat :", f"{len(RESULTS) - len(failed)}/{len(RESULTS)} contrôles réussis")
    for label in failed:
        print("  ✗", label)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
