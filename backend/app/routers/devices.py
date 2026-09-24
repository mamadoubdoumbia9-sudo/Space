"""Connexion WhatsApp : appareil lié (particuliers) et WhatsApp Business Platform (pros).

Point clé de conformité : aucune fonctionnalité de l'application n'est accessible
avant qu'un appareil soit réellement lié ET confirmé connecté. « Confirmé connecté »
signifie que la passerelle, qui parle le protocole multi-appareils de WhatsApp,
remonte un identifiant de compte réel (`wa_jid`/`wa_number`) — pas un simple clic.
"""
from __future__ import annotations

import datetime as dt
import logging

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..api import active_user, client_ip
from ..config import get_settings
from ..constants import (
    CONSENT_VERSION,
    WA_WEB_RISK_NOTICE_FR,
    DeviceStatus,
    SuspensionSource,
    UserStatus,
)
from ..db import get_db
from ..models import ConsentRecord, ContactIndex, LinkedDevice, Target, User
from ..schemas import BusinessLinkIn, DeviceOut, LinkConfirmIn, LinkStartIn, LinkStartOut
from ..security import decrypt_str, encrypt_str, fingerprint, normalize_phone
from ..services import audit, notify, reports as reports_svc
from ..services import limits
from ..services.connector import ConnectorError, ConnectorUnavailable, get_connector

log = logging.getLogger("signalpro.devices")
router = APIRouter(prefix="/devices", tags=["Connexion WhatsApp"])


def _device_out(device: LinkedDevice, reveal: bool = True) -> DeviceOut:
    number = decrypt_str(device.wa_number_enc)
    return DeviceOut(
        id=device.id,
        mode=device.mode,
        label=device.label,
        status=device.status,
        wa_number=number if reveal else (reports_svc.mask_phone(number) if number else None),
        risk_consent=device.risk_consent,
        linked_at=device.linked_at,
        last_seen_at=device.last_seen_at,
        contacts_synced_at=device.contacts_synced_at,
        contacts_count=device.contacts_count,
    )


@router.get("/gateway/status", response_model=dict)
def gateway_status():
    """Diagnostic honnête : dit si la passerelle est joignable et ce qu'elle sait faire."""
    s = get_settings()
    connector = get_connector()
    payload = {
        "configured": connector.configured,
        "base_url": s.connector_base_url or None,
        "remote_mode": s.connector_remote_mode,
        "notice": WA_WEB_RISK_NOTICE_FR,
        "install_hint": (
            "La passerelle tourne chez vous (dossier gateway/ du dépôt) : "
            "npm install && GATEWAY_SHARED_SECRET=... npm start, puis renseignez CONNECTOR_BASE_URL "
            "et CONNECTOR_SHARED_SECRET côté serveur."
        ),
    }
    if not connector.configured:
        payload["reachable"] = False
        payload["reason"] = "CONNECTOR_BASE_URL / CONNECTOR_SHARED_SECRET non configurés."
        return payload
    try:
        caps = connector.capabilities()
        payload.update(
            {
                "reachable": True,
                "version": caps.version,
                "capabilities": {
                    "report_native": caps.report_native,
                    "block_contact": caps.block_contact,
                    "contact_sync": caps.contact_sync,
                },
            }
        )
    except (ConnectorError, ConnectorUnavailable) as exc:
        payload.update({"reachable": False, "reason": str(exc)})
    return payload


@router.post("/link/start", response_model=LinkStartOut)
def start_link(
    payload: LinkStartIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Démarre une liaison d'appareil. Le consentement au risque est exigé (non désactivable)."""
    s = get_settings()
    if not payload.risk_consent:
        raise HTTPException(
            status_code=400,
            detail=(
                "Consentement explicite requis : "
                + WA_WEB_RISK_NOTICE_FR
                + " Cochez la case de consentement pour continuer."
            ),
        )
    if payload.consent_version != CONSENT_VERSION:
        raise HTTPException(
            status_code=400,
            detail=f"Version de consentement obsolète : lisez et acceptez la version {CONSENT_VERSION}.",
        )
    if s.connector_remote_mode:
        raise HTTPException(
            status_code=400,
            detail=(
                "Mode passerelle distante désactivé : faire tourner la session WhatsApp d'un utilisateur "
                "sur un serveur tiers est contraire à notre politique de sécurité. Utilisez la passerelle locale."
            ),
        )
    try:
        limits.consume_pairing(db, user.id)
    except limits.RateLimitExceeded as exc:
        db.rollback()
        raise HTTPException(status_code=429, detail=str(exc), headers={"Retry-After": str(exc.retry_after_s)}) from exc

    connector = get_connector()
    if not connector.configured:
        raise HTTPException(
            status_code=503,
            detail=(
                "Passerelle WhatsApp non configurée sur ce serveur : aucune liaison ne peut être créée "
                "(aucune simulation n'est effectuée). Démarrez la passerelle du dossier gateway/ et "
                "renseignez CONNECTOR_BASE_URL + CONNECTOR_SHARED_SECRET."
            ),
        )

    db.add(
        ConsentRecord(
            user_id=user.id,
            kind="wa_web_risk",
            version=CONSENT_VERSION,
            accepted=True,
            ip=client_ip(request),
            user_agent=request.headers.get("user-agent"),
        )
    )
    try:
        session = connector.start_session(user_ref=f"u{user.id}", label=payload.label)
    except (ConnectorError, ConnectorUnavailable) as exc:
        db.rollback()
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    device = LinkedDevice(
        user_id=user.id,
        mode="web_linked",
        label=payload.label,
        status=session.get("status", DeviceStatus.PENDING),
        session_ref=session.get("session_ref"),
        risk_consent=True,
        risk_consent_at=dt.datetime.now(dt.timezone.utc),
        risk_consent_version=CONSENT_VERSION,
    )
    db.add(device)
    db.flush()
    audit.log(
        db,
        action="device.link_started",
        actor_user_id=user.id,
        entity_type="linked_device",
        entity_id=device.id,
        ip=client_ip(request),
        detail={"session_ref": device.session_ref, "status": device.status},
    )
    db.commit()
    return LinkStartOut(
        device_id=device.id,
        session_ref=device.session_ref or "",
        pairing_payload=session.get("pairing_payload"),
        expires_in=int(session.get("expires_in", 120)),
        gateway_configured=True,
        notice=WA_WEB_RISK_NOTICE_FR,
    )


@router.post("/link/confirm", response_model=DeviceOut)
def confirm_link(
    payload: LinkConfirmIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Vérifie auprès de la passerelle que le téléphone a RÉELLEMENT scanné le QR.

    Tant que WhatsApp n'a pas confirmé la session, l'appareil reste `pending` et
    l'utilisateur ne peut pas accéder aux fonctionnalités d'action.
    """
    device = db.get(LinkedDevice, payload.device_id)
    if device is None or device.user_id != user.id:
        raise HTTPException(status_code=404, detail="Appareil introuvable.")
    connector = get_connector()
    try:
        state = connector.session_state(device.session_ref or "")
    except (ConnectorError, ConnectorUnavailable) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    status = state.get("status", DeviceStatus.PENDING)
    device.status = status
    device.last_seen_at = dt.datetime.now(dt.timezone.utc)
    if status == DeviceStatus.CONNECTED:
        jid = state.get("jid") or ""
        number = normalize_phone(state.get("number") or "")
        if not jid or not number:
            db.rollback()
            raise HTTPException(
                status_code=502,
                detail="La passerelle prétend la session connectée sans identifiant WhatsApp : liaison refusée.",
            )
        device.wa_jid_enc = encrypt_str(jid)
        device.wa_jid_fp = fingerprint(jid)
        device.wa_number_enc = encrypt_str("+" + number)
        device.wa_number_fp = fingerprint(number)
        device.linked_at = dt.datetime.now(dt.timezone.utc)
        audit.log(
            db,
            action="device.linked",
            actor_user_id=user.id,
            entity_type="linked_device",
            entity_id=device.id,
            ip=client_ip(request),
            detail={"jid_suffix": jid[-12:], "platform": state.get("platform")},
        )
        notify.push(
            db,
            user.id,
            "device_linked",
            "WhatsApp connecté",
            f"Votre appareil « {device.label} » est lié. SignalPro utilise désormais vos conversations "
            "réelles pour vérifier vos signalements.",
            {"device_id": device.id},
        )
    db.flush()
    synced = _try_sync_contacts(db, device, user.id)
    db.commit()
    out = _device_out(device)
    return out


def _try_sync_contacts(db: Session, device: LinkedDevice, user_id: int) -> dict:
    """Synchronise les empreintes de conversations (sans aucun contenu de message)."""
    connector = get_connector()
    if device.status != DeviceStatus.CONNECTED or not device.session_ref:
        return {"synced": 0, "reason": "appareil non connecté"}
    try:
        payload = connector.sync_contacts(device.session_ref)
    except (ConnectorError, ConnectorUnavailable) as exc:
        log.warning("Synchronisation des conversations impossible : %s", exc)
        return {"synced": 0, "reason": str(exc)}

    chats = payload.get("chats") or []
    existing = {
        row for row in db.execute(select(ContactIndex.peer_fp).where(ContactIndex.device_id == device.id)).scalars()
    }
    added = 0
    for chat in chats:
        jid = str(chat.get("jid") or "")
        if not jid:
            continue
        digits = normalize_phone(jid.split("@")[0])
        if not digits:
            continue
        fp = fingerprint(digits)
        if fp in existing:
            continue
        last = chat.get("last_message_at")
        when = None
        if last:
            try:
                when = dt.datetime.fromtimestamp(int(last), tz=dt.timezone.utc)
            except (TypeError, ValueError, OSError):
                when = None
        db.add(
            ContactIndex(
                device_id=device.id,
                user_id=user_id,
                peer_fp=fp,
                is_group=bool(chat.get("is_group")),
                last_message_at=when,
            )
        )
        added += 1
    device.contacts_synced_at = dt.datetime.now(dt.timezone.utc)
    device.contacts_count = len(existing) + added
    db.flush()
    audit.log(
        db,
        action="contacts.synced",
        actor_user_id=user_id,
        entity_type="linked_device",
        entity_id=device.id,
        detail={"received": len(chats), "added": added, "content_stored": False},
    )
    return {"synced": added, "total": device.contacts_count}


@router.post("/{device_id}/sync", response_model=dict)
def sync_device(
    device_id: int,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    device = db.get(LinkedDevice, device_id)
    if device is None or device.user_id != user.id:
        raise HTTPException(status_code=404, detail="Appareil introuvable.")
    result = _try_sync_contacts(db, device, user.id)
    db.commit()
    return {"device_id": device.id, "status": device.status, **result}


@router.get("", response_model=list[DeviceOut])
def list_devices(user: User = Depends(active_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(LinkedDevice).where(LinkedDevice.user_id == user.id).order_by(LinkedDevice.id.desc())
    ).scalars()
    return [_device_out(d) for d in rows]


@router.post("/{device_id}/refresh", response_model=DeviceOut)
def refresh_device(
    device_id: int,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Réinterroge l'état réel de la session (utile si l'utilisateur a délié côté WhatsApp)."""
    device = db.get(LinkedDevice, device_id)
    if device is None or device.user_id != user.id:
        raise HTTPException(status_code=404, detail="Appareil introuvable.")
    if not device.session_ref:
        return _device_out(device)
    connector = get_connector()
    try:
        state = connector.session_state(device.session_ref)
    except (ConnectorError, ConnectorUnavailable) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    device.status = state.get("status", device.status)
    device.last_seen_at = dt.datetime.now(dt.timezone.utc)
    if device.status != DeviceStatus.CONNECTED and device.revoked_at is None and state.get("disconnected_at"):
        device.revoked_at = dt.datetime.now(dt.timezone.utc)
        device.revoked_reason = "Appareil déconnecté côté WhatsApp."
    db.commit()
    return _device_out(device)


@router.delete("/{device_id}", response_model=dict)
def revoke_device(
    device_id: int,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Révoque l'appareil lié : la passerelle supprime la session, côté WhatsApp aussi."""
    device = db.get(LinkedDevice, device_id)
    if device is None or device.user_id != user.id:
        raise HTTPException(status_code=404, detail="Appareil introuvable.")
    connector = get_connector()
    detail = "session locale inconnue"
    if device.session_ref and connector.configured:
        try:
            connector.revoke_session(device.session_ref)
            detail = "session révoquée côté passerelle"
        except (ConnectorError, ConnectorUnavailable) as exc:
            detail = f"passerelle injoignable : {exc}"
    device.status = DeviceStatus.REVOKED
    device.revoked_at = dt.datetime.now(dt.timezone.utc)
    device.revoked_reason = detail
    device.session_ref = None
    audit.log(db, action="device.revoked", actor_user_id=user.id, entity_type="linked_device", entity_id=device.id,
              ip=client_ip(request), detail={"detail": detail})
    db.commit()
    return {"ok": True, "detail": detail}


# --- Comptes professionnels (WhatsApp Business Platform) --------------------
@router.post("/business/link", response_model=dict)
def link_business(
    payload: BusinessLinkIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Rattache un compte professionnel : le jeton est VÉRIFIÉ auprès de Meta puis chiffré."""
    from ..services.cloud_api import CloudApiError, WhatsAppCloudClient

    client = WhatsAppCloudClient(token=payload.access_token, phone_number_id=payload.phone_number_id)
    if not client.configured:
        raise HTTPException(status_code=400, detail="Jetons ou identifiants manquants.")
    try:
        info = client.verify_configuration()
    except CloudApiError as exc:
        audit.log(db, action="business.link_failed", actor_user_id=user.id, entity_type="user", entity_id=user.id,
                  detail={"error": str(exc)[:300]})
        db.commit()
        raise HTTPException(status_code=400, detail=f"Meta a refusé ces identifiants : {exc}") from exc

    user.is_business = True
    user.business_waba_id = payload.waba_id[:64]
    user.business_phone_number_id = payload.phone_number_id[:64]
    user.business_verified_at = dt.datetime.now(dt.timezone.utc)
    device = LinkedDevice(
        user_id=user.id,
        mode="cloud_api",
        label=str(info.get("verified_name") or "Compte professionnel")[:80],
        status=DeviceStatus.CONNECTED,
        phone_number_id=payload.phone_number_id,
        wa_number_enc=encrypt_str("+" + normalize_phone(str(info.get("display_phone_number") or ""))),
        wa_number_fp=fingerprint(str(info.get("display_phone_number") or "")),
        linked_at=dt.datetime.now(dt.timezone.utc),
    )
    db.add(device)
    db.flush()
    # Le jeton système est stocké chiffré dans un consentement technique dédié.
    from ..models import ConsentRecord as CR

    db.add(CR(user_id=user.id, kind="business_credentials", version="1", accepted=True, accepted_at=dt.datetime.now(dt.timezone.utc)))
    # Le jeton lui-même vit dans le stockage chiffré, jamais en clair dans la base.
    from ..services.storage import get_storage

    get_storage().put(f"credentials/{user.id}/cloud_api_token", payload.access_token.encode())

    audit.log(db, action="business.linked", actor_user_id=user.id, entity_type="linked_device", entity_id=device.id,
              ip=client_ip(request),
              detail={"phone_number_id": payload.phone_number_id, "verified_name": info.get("verified_name"),
                      "quality_rating": info.get("quality_rating")})
    db.commit()
    return {
        "ok": True,
        "device_id": device.id,
        "verified_name": info.get("verified_name"),
        "display_phone_number": info.get("display_phone_number"),
        "quality_rating": info.get("quality_rating"),
        "token_stored": "chiffré (AES-256-GCM), jamais renvoyé à l'application",
        "capabilities": {
            "report_native": False,
            "note": (
                "WhatsApp Business Platform ne propose aucun endpoint de signalement d'un tiers. "
                "Les comptes professionnels reçoivent les alertes, les webhooks de compte et l'escalade "
                "par dossier vers le canal officiel d'abus."
            ),
        },
    }


@router.get("/business/status", response_model=dict)
def business_status(user: User = Depends(active_user), db: Session = Depends(get_db)):
    from ..services.cloud_api import CloudApiError, get_cloud_client

    if not user.is_business:
        return {"linked": False}
    client = get_cloud_client()
    if not client.configured:
        return {
            "linked": True,
            "live_check": False,
            "reason": "CLOUD_API_TOKEN / CLOUD_API_PHONE_NUMBER_ID absents de l'environnement serveur.",
        }
    try:
        info = client.verify_configuration()
        return {"linked": True, "live_check": True, "account": info}
    except CloudApiError as exc:
        return {"linked": True, "live_check": False, "reason": str(exc)}


@router.post("/report-suspension", response_model=dict)
def report_suspension_declared(
    target_phone: str,
    note: str = "",
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Déclaration par un utilisateur qu'un numéro n'est plus joignable.

    Enregistrée comme DÉCLARATIF : elle ne devient jamais « suspendu confirmé »
    sans vérification d'un modérateur ou un signal de Meta.
    """
    target = reports_svc.get_target(db, target_phone, create=False)
    if target is None:
        raise HTTPException(status_code=404, detail="Ce numéro n'est pas dans la base.")
    reports_svc.record_suspension(
        db,
        target=target,
        source=SuspensionSource.USER_DECLARED,
        evidence=note or "Déclaré par un utilisateur (non vérifié).",
        actor=user,
    )
    db.commit()
    return {
        "ok": True,
        "suspension_status": target.suspension_status,
        "note": "Statut déclaratif : il n'est pas affiché comme une suspension confirmée.",
    }
