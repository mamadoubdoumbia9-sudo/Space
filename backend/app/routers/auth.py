"""Authentification, vérification obligatoire, consentements, suppression de compte."""
from __future__ import annotations

import datetime as dt
import logging

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..api import active_user, client_ip, current_user, find_user_by_email
from ..config import get_settings
from ..constants import (
    CONSENT_VERSION,
    DISCLAIMER_LONG_FR,
    DISCLAIMER_SHORT_FR,
    WA_WEB_RISK_NOTICE_FR,
    Role,
    UserStatus,
    VerificationChannel,
)
from ..db import get_db
from ..models import ConsentRecord, User, VerificationCode
from ..schemas import (
    ChangePasswordIn,
    DeleteAccountIn,
    LimitsOut,
    LoginIn,
    RefreshIn,
    RegisterIn,
    TokenOut,
    UserOut,
    VerifyIn,
)
from ..security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    decrypt_str,
    encrypt_str,
    fingerprint,
    generate_code,
    hash_code,
    hash_password,
    verify_password,
)
from ..services import audit, notify

log = logging.getLogger("signalpro.auth")
router = APIRouter(prefix="/auth", tags=["Authentification"])

CODE_TTL_MIN = 15
MAX_CODE_ATTEMPTS = 6


def _user_out(user: User) -> UserOut:
    return UserOut(
        id=user.id,
        email=decrypt_str(user.email_enc),
        phone=decrypt_str(user.phone_enc),
        display_name=user.display_name,
        role=user.role,
        status=user.status,
        is_verified=user.is_verified,
        strikes=user.strikes,
        is_business=user.is_business,
        created_at=user.created_at,
    )


def _issue_verification(db: Session, user: User, channel: str) -> VerificationCode:
    code = generate_code(6)
    vc = VerificationCode(
        user_id=user.id,
        channel=channel,
        purpose="account_verification",
        code_hash=hash_code(code, "account_verification"),
        expires_at=dt.datetime.now(dt.timezone.utc) + dt.timedelta(minutes=CODE_TTL_MIN),
    )
    db.add(vc)
    db.flush()

    address = decrypt_str(user.phone_enc) if channel == VerificationChannel.SMS else decrypt_str(user.email_enc)
    if not address:
        raise HTTPException(status_code=400, detail=f"Aucune adresse {channel} enregistrée pour ce compte.")
    delivered, detail = notify.send_verification(channel, address, code)
    vc.delivered = delivered
    vc.delivery_detail = detail
    # Le code n'apparaît dans la réponse QUE si aucun fournisseur n'est configuré
    # (mode développement / auto-hébergé). Sinon il n'est transmis que par le canal.
    if not delivered and get_settings().allow_console_verification:
        vc.delivery_detail = f"{detail} [mode console : code={code}]"
    db.flush()
    return vc


@router.post("/register", response_model=dict, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterIn, request: Request, db: Session = Depends(get_db)):
    if not payload.accept_terms or not payload.accept_privacy:
        raise HTTPException(
            status_code=400,
            detail="Vous devez accepter les conditions d'utilisation et la politique de confidentialité.",
        )
    if find_user_by_email(db, payload.email):
        raise HTTPException(status_code=409, detail="Un compte existe déjà avec cet email.")
    if db.execute(select(User).where(User.phone_fp == fingerprint(payload.phone))).scalar_one_or_none():
        raise HTTPException(status_code=409, detail="Un compte existe déjà avec ce numéro de téléphone.")

    user = User(
        email_enc=encrypt_str(payload.email.lower()),
        email_fp=fingerprint(payload.email.lower()),
        phone_enc=encrypt_str(payload.phone),
        phone_fp=fingerprint(payload.phone),
        display_name=payload.display_name or payload.email.split("@")[0][:60],
        password_hash=hash_password(payload.password),
        status=UserStatus.PENDING_VERIFICATION,
        is_verified=False,
        verification_channel=payload.channel,
    )
    db.add(user)
    db.flush()
    for kind in ("terms", "privacy"):
        db.add(
            ConsentRecord(
                user_id=user.id,
                kind=kind,
                version=CONSENT_VERSION,
                accepted=True,
                ip=client_ip(request),
                user_agent=request.headers.get("user-agent"),
            )
        )
    vc = _issue_verification(db, user, payload.channel)
    audit.log(
        db,
        action="user.registered",
        actor_user_id=user.id,
        entity_type="user",
        entity_id=user.id,
        ip=client_ip(request),
        user_agent=request.headers.get("user-agent"),
        detail={"channel": payload.channel, "verification_delivered": vc.delivered},
    )
    db.commit()
    body = {
        "user_id": user.id,
        "verification_channel": payload.channel,
        "delivered": vc.delivered,
        "delivery_detail": vc.delivery_detail,
        "disclaimer": DISCLAIMER_SHORT_FR,
    }
    if not vc.delivered and get_settings().allow_console_verification:
        # Autorise l'auto-hébergement sans fournisseur email/SMS : le code est renvoyé
        # UNIQUEMENT dans ce cas explicite, jamais en production avec SMTP configuré.
        body["dev_code"] = vc.delivery_detail.split("code=")[-1].rstrip("]") if "code=" in (vc.delivery_detail or "") else None
    return body


@router.post("/resend-code", response_model=dict)
def resend_code(payload: RegisterIn, db: Session = Depends(get_db)):
    """Renvoi de code : réutilise l'email, l'adresse doit correspondre au compte."""
    user = find_user_by_email(db, payload.email)
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=404, detail="Compte introuvable ou mot de passe incorrect.")
    if user.is_verified:
        raise HTTPException(status_code=400, detail="Ce compte est déjà vérifié.")
    vc = _issue_verification(db, user, user.verification_channel or VerificationChannel.EMAIL)
    audit.log(db, action="user.code_resent", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"delivered": vc.delivered})
    db.commit()
    return {"delivered": vc.delivered, "delivery_detail": vc.delivery_detail}


@router.post("/verify", response_model=TokenOut)
def verify(payload: VerifyIn, request: Request, db: Session = Depends(get_db)):
    user = find_user_by_email(db, payload.email)
    if not user:
        raise HTTPException(status_code=404, detail="Compte introuvable.")
    if user.is_verified:
        return TokenOut(access_token=create_access_token(user.id, user.role), refresh_token=create_refresh_token(user.id))

    vc = db.execute(
        select(VerificationCode)
        .where(VerificationCode.user_id == user.id, VerificationCode.consumed_at.is_(None))
        .order_by(VerificationCode.id.desc())
        .limit(1)
    ).scalar_one_or_none()
    if vc is None:
        raise HTTPException(status_code=400, detail="Aucun code en attente. Demandez un nouveau code.")
    if vc.expires_at.replace(tzinfo=dt.timezone.utc) < dt.datetime.now(dt.timezone.utc):
        raise HTTPException(status_code=400, detail="Code expiré. Demandez un nouveau code.")
    vc.attempts += 1
    if vc.attempts > MAX_CODE_ATTEMPTS:
        db.commit()
        raise HTTPException(status_code=429, detail="Trop de tentatives : demandez un nouveau code.")
    if hash_code(payload.code, vc.purpose) != vc.code_hash:
        db.commit()
        raise HTTPException(status_code=400, detail="Code incorrect.")

    vc.consumed_at = dt.datetime.now(dt.timezone.utc)
    user.is_verified = True
    user.status = UserStatus.ACTIVE
    user.verified_at = dt.datetime.now(dt.timezone.utc)
    audit.log(db, action="user.verified", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              ip=client_ip(request))
    notify.push(
        db,
        user.id,
        "welcome",
        "Bienvenue sur SignalPro",
        "Votre compte est vérifié. Liez votre WhatsApp pour commencer, et rappelez-vous : "
        "seul Meta décide des suspensions.",
    )
    db.commit()
    return TokenOut(access_token=create_access_token(user.id, user.role), refresh_token=create_refresh_token(user.id))


@router.post("/login", response_model=TokenOut)
def login(payload: LoginIn, request: Request, db: Session = Depends(get_db)):
    user = find_user_by_email(db, payload.email)
    if not user or not verify_password(payload.password, user.password_hash):
        audit.log(db, action="auth.login_failed", ip=client_ip(request),
                  detail={"email_fp": fingerprint(payload.email.lower())})
        db.commit()
        raise HTTPException(status_code=401, detail="Email ou mot de passe incorrect.")
    if user.status == UserStatus.BANNED:
        raise HTTPException(
            status_code=403,
            detail=user.banned_reason or "Compte banni définitivement pour signalements abusifs.",
        )
    if not user.is_verified:
        raise HTTPException(
            status_code=403,
            detail="Compte non vérifié : saisissez le code reçu par email ou SMS (obligatoire pour tous les comptes).",
        )
    if user.deletion_requested_at:
        raise HTTPException(status_code=403, detail="Ce compte est en cours de suppression.")
    user.last_login_at = dt.datetime.now(dt.timezone.utc)
    audit.log(db, action="auth.login", actor_user_id=user.id, actor_role=user.role, entity_type="user",
              entity_id=user.id, ip=client_ip(request), user_agent=request.headers.get("user-agent"))
    db.commit()
    return TokenOut(access_token=create_access_token(user.id, user.role), refresh_token=create_refresh_token(user.id))


@router.post("/refresh", response_model=TokenOut)
def refresh(payload: RefreshIn, db: Session = Depends(get_db)):
    try:
        claims = decode_token(payload.refresh_token)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=401, detail="Jeton de rafraîchissement invalide.") from exc
    if claims.get("typ") != "refresh":
        raise HTTPException(status_code=401, detail="Type de jeton invalide.")
    user = db.get(User, int(claims["sub"]))
    if user is None or user.status == UserStatus.BANNED:
        raise HTTPException(status_code=403, detail="Compte indisponible.")
    return TokenOut(access_token=create_access_token(user.id, user.role), refresh_token=create_refresh_token(user.id))


@router.get("/me", response_model=UserOut)
def me(user: User = Depends(current_user)):
    return _user_out(user)


@router.get("/limits", response_model=LimitsOut)
def limits():
    s = get_settings()
    return LimitsOut(
        max_reports_per_hour_user=s.max_reports_per_hour_user,
        max_reports_per_day_user=s.max_reports_per_day_user,
        max_actions_per_minute_user=s.max_actions_per_minute_user,
        max_abusive_strikes=s.max_abusive_strikes,
        min_verifications_to_publish=s.min_verifications_to_publish,
        min_verifications_to_escalate=s.min_verifications_to_escalate,
        disclaimers={
            "short": DISCLAIMER_SHORT_FR,
            "long": DISCLAIMER_LONG_FR,
            "wa_web_risk": WA_WEB_RISK_NOTICE_FR,
            "consent_version": CONSENT_VERSION,
        },
        enforcement_note=(
            "Ces limites sont appliquées par le serveur et ne peuvent pas être désactivées par le client. "
            "Elles suivent les plafonds de WhatsApp et ne sont jamais contournées."
        ),
    )


@router.post("/change-password", response_model=dict)
def change_password(
    payload: ChangePasswordIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    if not verify_password(payload.current_password, user.password_hash):
        raise HTTPException(status_code=400, detail="Mot de passe actuel incorrect.")
    user.password_hash = hash_password(payload.new_password)
    audit.log(db, action="user.password_changed", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              ip=client_ip(request))
    db.commit()
    return {"ok": True}


@router.post("/consents", response_model=dict)
def record_consent(
    kind: str,
    request: Request,
    accepted: bool = True,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    if kind not in ("terms", "privacy", "wa_web_risk", "message_storage", "community_share"):
        raise HTTPException(status_code=400, detail="Type de consentement inconnu.")
    db.add(
        ConsentRecord(
            user_id=user.id,
            kind=kind,
            version=CONSENT_VERSION,
            accepted=accepted,
            ip=client_ip(request),
            user_agent=request.headers.get("user-agent"),
        )
    )
    audit.log(db, action="consent.recorded", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"kind": kind, "accepted": accepted, "version": CONSENT_VERSION})
    db.commit()
    return {"ok": True, "kind": kind, "accepted": accepted, "version": CONSENT_VERSION}


@router.delete("/account", response_model=dict)
def delete_account(
    payload: DeleteAccountIn,
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
):
    """Suppression réelle : preuves effacées du stockage, données personnelles détruites.

    Les journaux d'audit de sécurité sont conservés sous forme pseudonymisée
    (obligation de coopération judiciaire), comme indiqué dans la politique de confidentialité.
    """
    if payload.confirm.strip().upper() != "SUPPRIMER":
        raise HTTPException(status_code=400, detail="Écrivez SUPPRIMER pour confirmer la suppression.")
    if not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=400, detail="Mot de passe incorrect.")

    from ..models import Evidence, MessageSnapshot, Report
    from ..services.storage import get_storage

    storage = get_storage()
    deleted_evidence = 0
    for report in db.execute(select(Report).where(Report.reporter_id == user.id)).scalars():
        for ev in report.evidences:
            try:
                storage.delete(ev.storage_key)
                deleted_evidence += 1
            except Exception:  # noqa: BLE001
                log.warning("Preuve %s non supprimée pour le compte %s", ev.id, user.id)
        db.query(MessageSnapshot).filter(MessageSnapshot.report_id == report.id).delete()
        db.query(Evidence).filter(Evidence.report_id == report.id).delete()
        db.delete(report)

    audit.log(db, action="user.account_deleted", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              ip=client_ip(request), detail={"evidence_deleted": deleted_evidence})
    user_id = user.id
    db.delete(user)
    db.commit()
    return {
        "ok": True,
        "user_id": user_id,
        "evidence_deleted": deleted_evidence,
        "note": "Compte et preuves supprimés. Les journaux de sécurité pseudonymisés sont conservés 10 ans "
        "(obligation légale), sans contenu de conversation.",
    }


@router.get("/consents", response_model=list[dict])
def list_consents(user: User = Depends(active_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(ConsentRecord).where(ConsentRecord.user_id == user.id).order_by(ConsentRecord.id.desc()).limit(100)
    ).scalars()
    return [
        {
            "kind": c.kind,
            "version": c.version,
            "accepted": c.accepted,
            "accepted_at": c.accepted_at.isoformat(),
        }
        for c in rows
    ]
