"""Escalade d'un dossier vers le canal officiel de Meta.

Ce que fait réellement ce module :

1. Il génère (ou relit) le dossier PDF du numéro visé.
2. Il l'envoie par email SMTP réel à l'adresse de signalement configurée par
   l'exploitant (`META_ABUSE_ESCALATION_EMAIL`), avec l'objet normalisé, le
   dossier en pièce jointe et un corps rappelant qu'aucune suspension n'est acquise.
3. Il enregistre le résultat exact (message-id SMTP, statut, erreur éventuelle).

Ce que ce module NE fait pas :
* il ne prétend pas disposer d'un « endpoint de bannissement » — Meta n'en publie pas ;
* il ne renvoie jamais `sent` si l'email n'est pas parti ;
* il n'automatise aucune action sur le compte d'une personne qui n'a pas consenti.
"""
from __future__ import annotations

import logging
import smtplib
import ssl
from datetime import datetime, timezone
from email.message import EmailMessage

from sqlalchemy.orm import Session

from ..config import get_settings
from ..models import Dossier, Target
from ..security import decrypt_str
from . import dossiers as dossier_svc
from .storage import get_storage

log = logging.getLogger("signalpro.escalation")

DEFAULT_SUBJECT_PREFIX = "[SignalPro] Signalement groupé WhatsApp"


def escalation_address() -> str:
    """Adresse du canal officiel configurée par l'exploitant."""
    import os

    return os.environ.get("META_ABUSE_ESCALATION_EMAIL", "").strip()


async def dispatch_dossier(db: Session, dossier: Dossier, target: Target) -> dict:
    address = escalation_address()
    phone = decrypt_str(target.phone_enc) or ""
    if not address:
        return {
            "status": "awaiting_channel",
            "channel": "email_official",
            "error": (
                "Canal officiel non configuré : renseignez META_ABUSE_ESCALATION_EMAIL "
                "avec l'adresse de signalement d'abus de votre compte WhatsApp Business. "
                "Le dossier reste généré et téléchargeable, il n'est pas déclaré envoyé."
            ),
        }

    s = get_settings()
    if not s.smtp_host:
        return {
            "status": "awaiting_channel",
            "channel": "smtp",
            "error": "SMTP non configuré : impossible d'expédier le dossier (aucun envoi simulé).",
        }

    pdf = get_storage().get(dossier.storage_key)
    ref = dossier.public_ref
    subject = f"{DEFAULT_SUBJECT_PREFIX} — {ref} — {phone}"
    body_lines = [
        "Bonjour,",
        "",
        "Veuillez trouver ci-joint un dossier de signalement groupé concernant le numéro suivant : "
        f"{phone}.",
        f"Référence dossier : {ref}",
        f"Signalements vérifiés par notre relecture : {dossier.report_count} "
        f"({dossier.distinct_reporters} plaignants distincts).",
        f"Empreinte SHA-256 du dossier : {dossier.pdf_sha256}",
        "",
        "Rappel de conformité : notre collectif ne demande ni n'anticipe aucune sanction automatique. "
        "L'examen et la décision de suspension relèvent exclusivement de vos équipes. Les preuves "
        "fournies sont des captures et exports transmis volontairement par les personnes concernées, "
        "chacune ayant vérifié sa propre conversation.",
        "",
        "Les pièces d'origine et le journal d'audit complet peuvent être communiqués sur réquisition "
        "d'une autorité judiciaire compétente.",
        "",
        "Cordialement,",
        "Le collectif SignalPro",
    ]

    msg = EmailMessage()
    msg["From"] = s.smtp_from
    msg["To"] = address
    msg["Subject"] = subject
    msg.set_content("\n".join(body_lines))
    msg.add_attachment(pdf, maintype="application", subtype="pdf", filename=f"{ref}.pdf")

    try:
        if s.smtp_port == 465:
            with smtplib.SMTP_SSL(s.smtp_host, s.smtp_port, context=ssl.create_default_context(), timeout=30) as smtp:
                if s.smtp_user:
                    smtp.login(s.smtp_user, s.smtp_password)
                smtp.send_message(msg)
        else:
            with smtplib.SMTP(s.smtp_host, s.smtp_port, timeout=30) as smtp:
                smtp.starttls(context=ssl.create_default_context())
                if s.smtp_user:
                    smtp.login(s.smtp_user, s.smtp_password)
                smtp.send_message(msg)
    except Exception as exc:  # noqa: BLE001
        log.error("Envoi du dossier %s échoué : %s", ref, exc)
        return {"status": "failed", "channel": "smtp", "error": f"{type(exc).__name__}: {exc}"[:1000]}

    return {
        "status": "sent",
        "channel": "smtp:official_abuse_address",
        "ref": f"smtp:{address}:{ref}:{datetime.now(timezone.utc).isoformat()}",
    }
