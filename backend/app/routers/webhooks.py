"""Webhooks officiels WhatsApp Business Platform (Cloud API).

Deux usages concrets et conformes :
1. `account_update` : lorsque Meta restreint le compte professionnel d'un
   utilisateur, l'Information vient de Meta — c'est la seule source légitime pour
   marquer une suspension comme confirmée côté entreprise ;
2. `messages` : un message entrant vers un compte professionnel peut être analysé
   (le compte est celui de l'utilisateur, la conversation le concerne) et, s'il
   correspond à une signature d'arnaque, l'utilisateur reçoit une alerte.

Toute requête dont la signature `X-Hub-Signature-256` est absente ou invalide est
rejetée : aucun webhook non authentifié n'écrit en base.
"""
from __future__ import annotations

import json
import logging

from fastapi import APIRouter, HTTPException, Query, Request
from fastapi.responses import PlainTextResponse
from sqlalchemy import select
from sqlalchemy.orm import Session
from fastapi import Depends

from ..constants import SuspensionSource
from ..db import get_db
from ..models import ConnectorEvent, LinkedDevice, Target, User
from ..security import fingerprint
from ..services import audit, notify
from ..services.cloud_api import get_cloud_client
from ..services import reports as reports_svc
from ..services.spam import scan_text

log = logging.getLogger("signalpro.webhooks")
router = APIRouter(prefix="/webhooks", tags=["Webhooks Meta"])


@router.get("/whatsapp", response_class=PlainTextResponse)
def verify(
    hub_mode: str | None = Query(None, alias="hub.mode"),
    hub_verify_token: str | None = Query(None, alias="hub.verify_token"),
    hub_challenge: str | None = Query(None, alias="hub.challenge"),
    mode: str | None = None,
    token: str | None = None,
    challenge: str | None = None,
):
    """Vérification officielle du webhook par Meta (GET hub.challenge)."""
    mode_v = hub_mode or mode
    token_v = hub_verify_token or token
    challenge_v = hub_challenge or challenge
    result = get_cloud_client().verify_webhook_challenge(mode_v, token_v, challenge_v)
    if result is None:
        raise HTTPException(status_code=403, detail="Vérification de webhook refusée (jeton invalide).")
    return PlainTextResponse(result)


@router.post("/whatsapp", response_model=dict)
async def receive(request: Request, db: Session = Depends(get_db)):
    raw = await request.body()
    signature = request.headers.get("x-hub-signature-256")
    client = get_cloud_client()
    if not client.verify_signature(raw, signature):
        audit.log(db, action="webhook.rejected", ip=request.client.host if request.client else None,
                  detail={"reason": "signature invalide ou absente", "bytes": len(raw)})
        db.commit()
        raise HTTPException(status_code=401, detail="Signature du webhook invalide.")

    events = client.parse_webhook(raw)
    handled = 0
    for event in events:
        value = event.payload
        if event.field == "account_update":
            handled += _handle_account_update(db, value)
        elif event.field == "messages":
            handled += _handle_inbound_message(db, value)
        else:
            db.add(
                ConnectorEvent(
                    kind=f"webhook:{event.field}",
                    payload_summary=json.dumps(event.payload)[:2000],
                )
            )
            db.flush()
    db.commit()
    return {"ok": True, "events": len(events), "handled": handled}


def _org_user(db: Session, phone_number_id: str | None) -> User | None:
    if not phone_number_id:
        return None
    device = db.execute(
        select(LinkedDevice).where(LinkedDevice.phone_number_id == phone_number_id)
    ).scalar_one_or_none()
    return db.get(User, device.user_id) if device else None


def _handle_account_update(db: Session, value: dict) -> int:
    """Meta signale une restriction sur le compte professionnel de l'utilisateur."""
    phone_number_id = value.get("phone_number_id") or (value.get("metadata") or {}).get("phone_number_id")
    user = _org_user(db, phone_number_id)
    event = value.get("event") or value.get("account_update", {}).get("event") or "unknown"
    restriction = json.dumps(value, ensure_ascii=False)[:1500]
    if user:
        notify.push(
            db,
            user.id,
            "account_update",
            f"Mise à jour du compte professionnel : {event}",
            restriction,
            {"field": "account_update"},
        )
    db.add(ConnectorEvent(kind="webhook:account_update", payload_summary=restriction, device_id=None))
    db.flush()
    audit.log(db, action="webhook.account_update", entity_type="user", entity_id=user.id if user else None,
              detail={"event": event, "phone_number_id": phone_number_id})
    return 1


def _handle_inbound_message(db: Session, value: dict) -> int:
    """Analyse un message reçu par un compte professionnel et alerte son propriétaire."""
    metadata = value.get("metadata") or {}
    phone_number_id = metadata.get("phone_number_id")
    owner = _org_user(db, phone_number_id)
    handled = 0
    for msg in value.get("messages") or []:
        sender = str(msg.get("from") or "")
        text = ((msg.get("text") or {}).get("body") or "") if msg.get("type") == "text" else ""
        if not sender:
            continue
        result = scan_text(db, text, sender)
        if result["is_suspicious"]:
            target_fp = fingerprint(sender)
            target = db.execute(select(Target).where(Target.phone_fp == target_fp)).scalar_one_or_none()
            if owner:
                notify.push(
                    db,
                    owner.id,
                    "scam_message_detected",
                    "Message suspect détecté sur votre compte professionnel",
                    f"Expéditeur {sender[:3]}…{sender[-2:]} · score {result['score']} · "
                    + " ".join(result["advice"][:2]),
                    {"sender_masked": sender[:3] + "…" + sender[-2:], "target_id": target.id if target else None},
                )
            db.add(
                ConnectorEvent(
                    kind="webhook:scam_detected",
                    payload_summary=json.dumps(
                        {"sender_masked": sender[:3] + "…" + sender[-2:], "score": result["score"],
                         "matches": len(result["matches"]), "content_stored": False}
                    ),
                )
            )
            db.flush()
            handled += 1
        else:
            db.add(ConnectorEvent(kind="webhook:message", payload_summary=json.dumps({"analyzed": True})))
            db.flush()
    return handled


@router.post("/whatsapp/simulate", response_model=dict)
async def simulate(request: Request, db: Session = Depends(get_db)):
    """Endpoint de TEST de la chaîne webhook (signature exigée), réservé au développement.

    Il n'est activé qu'en environnement `dev`/`test` : en production il renvoie 404.
    Il sert à vérifier que la vérification de signature et le routage fonctionnent.
    """
    from ..config import get_settings

    s = get_settings()
    if s.env == "production":
        raise HTTPException(status_code=404, detail="Indisponible en production.")
    raw = await request.body()
    signature = request.headers.get("x-hub-signature-256")
    client = get_cloud_client()
    if not client.app_secret:
        raise HTTPException(status_code=400, detail="CLOUD_API_APP_SECRET non configuré : test impossible.")
    if not client.verify_signature(raw, signature):
        raise HTTPException(status_code=401, detail="Signature invalide (le test vérifie bien la signature).")
    events = client.parse_webhook(raw)
    handled = sum(
        1
        for e in events
        if (e.field == "account_update" and _handle_account_update(db, e.payload))
        or (e.field == "messages" and _handle_inbound_message(db, e.payload))
    )
    db.commit()
    return {"ok": True, "events": len(events), "handled": handled}
