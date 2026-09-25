#!/usr/bin/env python3
"""Jeu de données de démonstration — pour tester réellement l'application.

Crée : un administrateur, un modérateur, deux utilisateurs vérifiés, un appareil
WhatsApp « connecté » pour l'un d'eux, les signatures de détection, plusieurs
signalements (dont trois vérifiés avec preuve, qui atteignent le seuil
communautaire de publication).

Refus en production sauf `--force`. Les mots de passe créés sont affichés à la fin.

Usage :
    python scripts/seed_demo.py                  # base de développement
    python scripts/seed_demo.py --reset          # repart d'une base vide
    python scripts/seed_demo.py --force          # insiste (déconseillé en prod)
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import sys

# Permet d'exécuter le script directement (`python scripts/xxx.py`) sans installer
# le paquet : la racine du backend est ajoutée au chemin d'import.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from app.constants import (
    CONSENT_VERSION,
    DeviceStatus,
    InfractionCategory,
    ReportStatus,
    ReportSource,
    Role,
    UserStatus,
    VerificationChannel,
)
from app.db import Base, SessionLocal, engine
from app.models import ConsentRecord, ContactIndex, Evidence, LinkedDevice, Report, Target, User
from app.security import encrypt_str, fingerprint, hash_password, normalize_phone
from app.services import reports as reports_svc
from app.services.spam import seed_signatures

DEMO_BASE_DATE = dt.datetime(2026, 3, 12, 14, 22, tzinfo=dt.timezone.utc)
DEMO_SCAM_NUMBER = "+22365551234"


def upsert_user(db, email: str, phone: str, password: str, role: str, display_name: str) -> User:
    number = normalize_phone(phone)
    fp = fingerprint(number)
    existing = db.query(User).filter(User.phone_fp == fp).one_or_none()
    if existing:
        return existing
    now = dt.datetime.now(dt.timezone.utc)
    user = User(
        email_enc=encrypt_str(email),
        email_fp=fingerprint(email),
        phone_enc=encrypt_str(number),
        phone_fp=fp,
        display_name=display_name,
        password_hash=hash_password(password),
        role=role,
        status=UserStatus.ACTIVE,
        is_verified=True,
        verification_channel=VerificationChannel.EMAIL,
        verified_at=now,
    )
    db.add(user)
    db.flush()
    db.add(
        ConsentRecord(
            user_id=user.id,
            kind="wa_web_risk",
            version=CONSENT_VERSION,
            accepted=True,
            ip="127.0.0.1",
            user_agent="seed-demo",
        )
    )
    return user


def ensure_demo_device(db, user: User) -> LinkedDevice:
    device = db.query(LinkedDevice).filter(LinkedDevice.user_id == user.id).one_or_none()
    if device is not None:
        return device
    number = "+22361234567"
    now = dt.datetime.now(dt.timezone.utc)
    device = LinkedDevice(
        user_id=user.id,
        mode="web_linked",
        label="Téléphone d'Alice",
        status=DeviceStatus.CONNECTED,
        session_ref="demo-session-alice",
        risk_consent=True,
        risk_consent_at=now,
        risk_consent_version=CONSENT_VERSION,
        wa_jid_enc=encrypt_str("22361234567@s.whatsapp.net"),
        wa_jid_fp=fingerprint("22361234567@s.whatsapp.net"),
        wa_number_enc=encrypt_str(number),
        wa_number_fp=fingerprint(number),
        linked_at=now,
        last_seen_at=now,
        contacts_synced_at=now,
        contacts_count=2,
    )
    db.add(device)
    db.flush()
    for peer, is_group in ((DEMO_SCAM_NUMBER, False), ("+22364449876", False)):
        db.add(
            ContactIndex(
                user_id=user.id,
                device_id=device.id,
                peer_fp=fingerprint(peer),
                is_group=is_group,
                last_message_at=now,
            )
        )
    db.flush()
    return device


def seed_verified_reports(db, reporter: User, target: Target, day_offset: int, description: str) -> Report:
    """Crée un signalement réel via le service, puis le passe « vérifié » comme le ferait un modérateur."""
    report = reports_svc.create_report(
        db,
        user=reporter,
        target_phone=DEMO_SCAM_NUMBER,
        category=InfractionCategory.FINANCIAL_SCAM,
        occurred_at=DEMO_BASE_DATE - dt.timedelta(days=day_offset),
        description=description,
        source=ReportSource.DIRECT,
        # Seed de démonstration uniquement : la vérification de contact est
        # explicitement contournée ici et JAMAIS en production (le code de
        # production refuse un signalement sans contact prouvé).
        declared_contact_method="manual_declaration",
        require_contact_proof=False,
    )
    report.contact_proof_method = "manual_declaration"
    report.contact_proof_at = dt.datetime.now(dt.timezone.utc)
    report.contact_proof_detail = "Capture fournie et contrôlée (jeu de démonstration)."
    report.status = ReportStatus.VERIFIED
    report.decided_at = dt.datetime.now(dt.timezone.utc)
    report.decision_reason = "Jeu de démonstration : preuve lue et validée."

    message_ids = [f"DEMO{report.id:04d}ABCDEFGH"]
    db.add(
        Evidence(
            report_id=report.id,
            kind="message_id",
            filename="message_ids.json",
            mime="application/json",
            size_bytes=len(json.dumps(message_ids)),
            sha256="",
            storage_key="",
            encrypted=False,
            integrity_ok=True,
            validation_detail=json.dumps({"message_ids": message_ids, "source": "seed_demo"}),
            message_ids=json.dumps(message_ids),
        )
    )
    db.flush()
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="Autorise l'exécution en production.")
    parser.add_argument("--reset", action="store_true", help="Supprime les tables avant le seed.")
    args = parser.parse_args()

    if os.getenv("ENV", "dev") == "prod" and not args.force:
        print("Refusé : ENV=prod. Ces comptes sont des comptes de test ; utilisez --force si c'est voulu.")
        return 2

    if args.reset:
        print("Suppression des tables…")
        Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

    db = SessionLocal()
    try:
        admin = upsert_user(db, "admin@signalpro-demo.com", "+22360000000", "AdminSignalPro123", Role.ADMIN, "Admin")
        moderator = upsert_user(
            db, "moderateur@signalpro-demo.com", "+22361111111", "ModerateurPro123", Role.MODERATOR, "Modératrice"
        )
        alice = upsert_user(db, "alice@example.org", "+22361234567", "AliceSignalPro123", Role.USER, "Alice")
        bruno = upsert_user(db, "bruno@example.org", "+22362345678", "BrunoSignalPro123", Role.USER, "Bruno")
        db.flush()

        seed_signatures(db)
        ensure_demo_device(db, alice)
        target = reports_svc.get_target(db, DEMO_SCAM_NUMBER, create=True)
        assert target is not None

        if db.query(Report).filter(Report.target_id == target.id).count() == 0:
            seed_verified_reports(db, alice, target, 0, "Demande de transfert Wave pour un colis inexistant.")
            seed_verified_reports(db, bruno, target, 1, "Faux agent de livraison exigeant un paiement immédiat.")
            seed_verified_reports(db, moderator, target, 2, "Même procédé : mobile money exigé avant livraison.")
        db.flush()
        reports_svc.recompute_target(db, target)
        db.commit()

        print("Jeu de démonstration prêt :")
        print("  admin       : admin@signalpro-demo.com       / AdminSignalPro123")
        print("  modérateur  : moderateur@signalpro-demo.com  / ModerateurPro123")
        print(f"  utilisateur : alice@example.org          / AliceSignalPro123   (appareil WhatsApp « connecté »)")
        print(f"  utilisateur : bruno@example.org          / BrunoSignalPro123")
        print(f"  cible       : {DEMO_SCAM_NUMBER} → {target.verified_reports} signalements vérifiés, "
              f"{target.distinct_reporters} personnes distinctes, statut {target.status}")
        print("\nComptes de TEST uniquement : à ne jamais utiliser en production.")
        return 0
    finally:
        db.close()


if __name__ == "__main__":
    sys.exit(main())
