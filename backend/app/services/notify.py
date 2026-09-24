"""Notifications internes + diffusion email/SMS réelle (SMTP / passerelle SMS).

Si aucun fournisseur n'est configuré, l'envoi échoue proprement et le message est
journalisé : on ne prétend jamais qu'un email est parti s'il ne l'est pas.
"""
from __future__ import annotations

import json
import logging
import smtplib
import ssl
from email.message import EmailMessage
from typing import Any

import httpx
from sqlalchemy.orm import Session

from ..config import get_settings
from ..models import Notification

log = logging.getLogger("signalpro.notify")


# --- Journal interne (in-app) ----------------------------------------------
def push(db: Session, user_id: int, kind: str, title: str, body: str = "", payload: dict[str, Any] | None = None) -> Notification:
    n = Notification(
        user_id=user_id, kind=kind, title=title[:160], body=body, payload=json.dumps(payload or {}, default=str)
    )
    db.add(n)
    db.flush()
    return n


# --- Envoi réel -------------------------------------------------------------
def delivery_mode() -> str:
    s = get_settings()
    if s.smtp_host:
        return "smtp"
    if s.sms_provider_url:
        return "sms"
    return "console"


def send_email(to: str, subject: str, body: str) -> tuple[bool, str]:
    s = get_settings()
    if not s.smtp_host:
        log.warning("[CONSOLE-EMAIL] to=%s subject=%s\n%s", to, subject, body)
        return (False, "SMTP non configuré : email écrit dans le journal du serveur.")
    msg = EmailMessage()
    msg["From"] = s.smtp_from
    msg["To"] = to
    msg["Subject"] = subject
    msg.set_content(body)
    try:
        if s.smtp_port == 465:
            with smtplib.SMTP_SSL(s.smtp_host, s.smtp_port, context=ssl.create_default_context(), timeout=15) as smtp:
                if s.smtp_user:
                    smtp.login(s.smtp_user, s.smtp_password)
                smtp.send_message(msg)
        else:
            with smtplib.SMTP(s.smtp_host, s.smtp_port, timeout=15) as smtp:
                smtp.starttls(context=ssl.create_default_context())
                if s.smtp_user:
                    smtp.login(s.smtp_user, s.smtp_password)
                smtp.send_message(msg)
        return (True, "Envoyé")
    except Exception as exc:  # noqa: BLE001
        log.error("Échec envoi email vers %s : %s", to, exc)
        return (False, f"Échec SMTP : {exc}")


def send_sms(to: str, body: str) -> tuple[bool, str]:
    s = get_settings()
    if not s.sms_provider_url:
        log.warning("[CONSOLE-SMS] to=%s body=%s", to, body)
        return (False, "Fournisseur SMS non configuré : message écrit dans le journal du serveur.")
    try:
        resp = httpx.post(
            s.sms_provider_url,
            json={"to": to, "message": body},
            headers={"Authorization": f"Bearer {s.sms_provider_token}"} if s.sms_provider_token else {},
            timeout=15,
        )
        resp.raise_for_status()
        return (True, f"Envoyé ({resp.status_code})")
    except Exception as exc:  # noqa: BLE001
        log.error("Échec envoi SMS vers %s : %s", to, exc)
        return (False, f"Échec fournisseur SMS : {exc}")


def send_verification(channel: str, address: str, code: str) -> tuple[bool, str]:
    if channel == "sms":
        return send_sms(address, f"Votre code de vérification est {code}. Il expire dans 15 minutes.")
    return send_email(
        address,
        "Votre code de vérification",
        f"Bonjour,\n\nVotre code de vérification est : {code}\n\n"
        "Il expire dans 15 minutes. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n",
    )
