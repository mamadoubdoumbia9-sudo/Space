#!/usr/bin/env python3
"""Remise à zéro contrôlée de la base (développement uniquement).

Trois niveaux, du plus doux au plus destructeur :

    python scripts/reset_db.py --check           # rien n'est modifié : compte les lignes
    python scripts/reset_db.py --soft            # supprime les signalements/preuves/audit,
                                                 # conserve les comptes et les signatures
    python scripts/reset_db.py --hard --yes      # supprime TOUTES les tables puis les recrée

Refus en production sauf `--force`. `--hard` exige `--yes` pour éviter un effacement
accidentel : il n'existe aucune corbeille.
"""
from __future__ import annotations

import argparse
import os
import pathlib
import sys

# Permet d'exécuter le script directement (`python scripts/xxx.py`) sans installer
# le paquet : la racine du backend est ajoutée au chemin d'import.
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

from sqlalchemy import func, select

from app.db import Base, SessionLocal, engine
from app.models import (
    Appeal,
    AuditLog,
    ContactIndex,
    DetectionHit,
    Dossier,
    Evidence,
    LinkedDevice,
    Notification,
    Report,
    ReportSubmission,
    SpamSignature,
    Target,
    User,
)

SOFT_CLEARED = [
    ReportSubmission,
    Evidence,
    Report,
    Appeal,
    Dossier,
    DetectionHit,
    ContactIndex,
    Notification,
    Target,
    AuditLog,
]


def describe(db) -> None:
    print("Contenu actuel de la base :")
    for model in [User, LinkedDevice, Target, Report, Evidence, Appeal, Dossier, DetectionHit, AuditLog, SpamSignature]:
        total = db.execute(select(func.count()).select_from(model)).scalar_one()
        print(f"  {model.__name__:20} {total}")
    print("\n(--check ne modifie rien)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Affiche simplement le contenu.")
    parser.add_argument("--soft", action="store_true", help="Purge les données métier, garde les comptes.")
    parser.add_argument("--hard", action="store_true", help="Supprime toutes les tables puis les recrée.")
    parser.add_argument("--yes", action="store_true", help="Confirme une opération destructive.")
    parser.add_argument("--force", action="store_true", help="Autorise l'exécution en production.")
    parser.add_argument("--reseed", action="store_true", help="Recharge les signatures de détection après coup.")
    args = parser.parse_args()

    if os.getenv("ENV", "dev") == "prod" and not args.force:
        print("Refusé : ENV=prod. Utilisez --force si la remise à zéro est réellement voulue.")
        return 2

    db = SessionLocal()
    try:
        if args.check or not (args.soft or args.hard):
            describe(db)
            return 0

        if args.soft:
            if not args.yes:
                print("--soft supprime signalements, preuves, cibles, appels, audit.")
                print("Confirmez avec --yes (aucune corbeille : l'opération est définitive).")
                return 1
            for model in SOFT_CLEARED:
                deleted = db.query(model).delete()
                print(f"  {model.__name__:20} supprimé(s) : {deleted}")
            db.commit()
            print("Purge métier terminée. Les comptes utilisateurs et les signatures sont conservés.")
            return 0

        if args.hard:
            if not args.yes:
                print("--hard supprime toutes les tables. Confirmez avec --yes.")
                return 1
            Base.metadata.drop_all(bind=engine)
            Base.metadata.create_all(bind=engine)
            print("Base réinitialisée (toutes les tables ont été recréées).")
            if args.reseed:
                from app.services.spam import seed_signatures

                count = seed_signatures(db)
                db.commit()
                print(f"Signatures de détection rechargées : {count}")
            return 0
    finally:
        db.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
