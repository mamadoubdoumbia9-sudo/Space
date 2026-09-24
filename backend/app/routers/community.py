"""Base communautaire : liste publique, blocage en un clic, contestations."""
from __future__ import annotations

import datetime as dt
import logging

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..api import active_user, client_ip
from ..config import get_settings
from ..constants import (
    CATEGORY_LABELS_FR,
    AppealStatus,
    DeviceStatus,
    ReportStatus,
    TargetStatus,
    UserStatus,
)
from ..db import get_db
from ..models import Appeal, LinkedDevice, Notification, Report, Target, User
from ..schemas import AppealCreateIn, AppealOut, BlacklistEntryOut, BlacklistOut, BlockAllIn, BlockAllOut
from ..security import decrypt_str, encrypt_str, fingerprint, normalize_phone
from ..services import audit, notify
from ..services import limits
from ..services import reports as reports_svc
from ..services.connector import ConnectorError, ConnectorUnavailable, get_connector

log = logging.getLogger("signalpro.community")
router = APIRouter(prefix="/community", tags=["Base communautaire"])

PUBLIC_NOTE = (
    "Cette liste recense des numéros pour lesquels au moins {min_reports} signalements ont été VÉRIFIÉS par notre "
    "équipe, provenant de personnes distinctes ayant réellement été contactées. Elle ne constitue pas une décision "
    "de WhatsApp : bloquer un numéro est un choix de protection personnelle, et tout numéro peut demander un "
    "réexamen. Nous ne garantissons aucune suspension."
)


@router.get("/blacklist", response_model=BlacklistOut)
def blacklist(
    limit: int = 100,
    offset: int = 0,
    category: str | None = None,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Liste publique (comptes vérifiés requis : pas de scraping anonyme)."""
    s = get_settings()
    base = select(Target).where(
        Target.verified_reports >= s.min_verifications_to_publish,
        Target.distinct_reporters >= s.min_verifications_to_publish,
        Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED, TargetStatus.APPEALED]),
    )
    count_q = select(func.count(Target.id)).where(
        Target.verified_reports >= s.min_verifications_to_publish,
        Target.distinct_reporters >= s.min_verifications_to_publish,
        Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED, TargetStatus.APPEALED]),
    )
    if category:
        base = base.where(Target.main_category == category)
        count_q = count_q.where(Target.main_category == category)
    total = db.execute(count_q).scalar_one()
    rows = list(
        db.execute(base.order_by(Target.risk_score.desc(), Target.id.desc()).limit(min(limit, 500)).offset(offset))
        .scalars()
    )
    return BlacklistOut(
        total=int(total),
        min_reports_required=s.min_verifications_to_publish,
        note=PUBLIC_NOTE.format(min_reports=s.min_verifications_to_publish),
        items=[
            BlacklistEntryOut(
                phone=None,  # le numéro complet n'est révélé qu'au téléchargement authentifié ci-dessous
                phone_masked=reports_svc.mask_phone(decrypt_str(t.phone_enc) or ""),
                category=t.main_category or "other",
                category_label=CATEGORY_LABELS_FR.get(t.main_category or "other", t.main_category or "—"),
                verified_reports=t.verified_reports,
                distinct_reporters=t.distinct_reporters,
                first_published_at=t.published_at,
                status=t.status,
                suspension_status=t.suspension_status,
                suspension_source=t.suspension_source,
            )
            for t in rows
        ],
    )


@router.get("/blacklist/export")
def export_blacklist(
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Export authentifié : la liste des numéros est fournie en clair pour permettre le blocage."""
    s = get_settings()
    rows = db.execute(
        select(Target).where(
            Target.verified_reports >= s.min_verifications_to_publish,
            Target.distinct_reporters >= s.min_verifications_to_publish,
            Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED]),
        )
    ).scalars()
    numbers = []
    for t in rows:
        phone = decrypt_str(t.phone_enc) or ""
        if phone:
            numbers.append(phone)
    audit.log(db, action="blacklist.exported", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"count": len(numbers)})
    db.commit()
    return {
        "count": len(numbers),
        "numbers": numbers,
        "disclaimer": (
            "Bloquer ces numéros est une mesure de protection personnelle. Cela ne déclenche ni ne garantit "
            "aucune suspension côté WhatsApp."
        ),
    }


@router.post("/block-all", response_model=BlockAllOut)
async def block_all(
    payload: BlockAllIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Bloque RÉELLEMENT, depuis l'appareil de l'utilisateur, les numéros de la liste.

    Chaque blocage est exécuté par la passerelle sur le compte de l'utilisateur,
    un par un, à un rythme lent (limite WhatsApp respectée). Les échecs sont
    remontés individuellement : aucun succès n'est inventé.
    """
    if not payload.consent_ack:
        raise HTTPException(status_code=400, detail="Confirmation requise : vous devez autoriser ce blocage.")
    device = db.get(LinkedDevice, payload.device_id)
    if device is None or device.user_id != user.id:
        raise HTTPException(status_code=404, detail="Appareil introuvable.")
    if device.status != DeviceStatus.CONNECTED or not device.session_ref:
        raise HTTPException(
            status_code=409,
            detail="Votre appareil WhatsApp n'est pas connecté : le blocage ne peut pas être exécuté réellement.",
        )
    connector = get_connector()
    try:
        caps = connector.capabilities()
    except (ConnectorError, ConnectorUnavailable) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    if not caps.block_contact:
        raise HTTPException(
            status_code=501,
            detail=(
                "La passerelle active n'expose pas l'action de blocage. Utilisez l'export de la liste et bloquez "
                "manuellement dans WhatsApp : nous n'afficherons jamais un blocage qui n'a pas eu lieu."
            ),
        )

    s = get_settings()
    rows = list(
        db.execute(
            select(Target)
            .where(
                Target.verified_reports >= s.min_verifications_to_publish,
                Target.distinct_reporters >= s.min_verifications_to_publish,
                Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED]),
            )
            .limit(payload.max_numbers)
        ).scalars()
    )
    details: list[dict] = []
    blocked = failed = 0
    for target in rows:
        phone = decrypt_str(target.phone_enc) or ""
        jid = normalize_phone(phone) + "@s.whatsapp.net"
        try:
            limits.consume_action(db, user.id, "block")
            res = connector.block_contact(device.session_ref, jid)
            ok = bool(res.get("performed"))
            details.append({"phone_masked": reports_svc.mask_phone(phone), "ok": ok, "detail": res.get("detail")})
            blocked += 1 if ok else 0
            failed += 0 if ok else 1
        except limits.RateLimitExceeded as exc:
            details.append({"phone_masked": reports_svc.mask_phone(phone), "ok": False, "detail": str(exc)})
            failed += 1
            db.rollback()
            break
        except (ConnectorError, ConnectorUnavailable) as exc:
            details.append({"phone_masked": reports_svc.mask_phone(phone), "ok": False, "detail": str(exc)})
            failed += 1
            db.rollback()
            break

    audit.log(db, action="blacklist.block_all", actor_user_id=user.id, entity_type="linked_device",
              entity_id=device.id, ip=client_ip(request),
              detail={"requested": len(rows), "blocked": blocked, "failed": failed})
    db.commit()
    return BlockAllOut(
        requested=len(rows),
        blocked=blocked,
        failed=failed,
        details=details,
        device_status=device.status,
    )


# --- Contestations ----------------------------------------------------------
@router.post("/appeals", response_model=AppealOut, status_code=201)
def create_appeal(payload: AppealCreateIn, request: Request, db: Session = Depends(get_db)):
    """Dépôt public : le propriétaire d'un numéro listé peut demander un réexamen."""
    target = reports_svc.get_target(db, payload.target_phone, create=False)
    if target is None:
        raise HTTPException(status_code=404, detail="Ce numéro ne figure pas dans notre base.")
    existing = db.execute(
        select(Appeal)
        .where(Appeal.target_id == target.id, Appeal.status.in_([AppealStatus.SUBMITTED, AppealStatus.UNDER_REVIEW]))
        .order_by(Appeal.id.desc())
        .limit(1)
    ).scalars().first()
    if existing:
        raise HTTPException(status_code=409, detail=f"Une contestation est déjà en cours ({existing.public_ref}).")

    import secrets

    appeal = Appeal(
        public_ref="APL-" + secrets.token_hex(4).upper(),
        target_id=target.id,
        claimant_contact_enc=encrypt_str(payload.claimant_contact) or "",
        claimant_contact_fp=fingerprint(payload.claimant_contact.lower()),
        statement=payload.statement,
        evidence_note=payload.evidence_note,
        status=AppealStatus.SUBMITTED,
    )
    db.add(appeal)
    db.flush()
    target.status = TargetStatus.APPEALED if target.status == TargetStatus.CONFIRMED_MALICIOUS else target.status
    audit.log(db, action="appeal.submitted", entity_type="appeal", entity_id=appeal.id, ip=client_ip(request),
              detail={"target_id": target.id, "ref": appeal.public_ref})
    db.commit()
    return AppealOut(
        id=appeal.id,
        public_ref=appeal.public_ref,
        target_phone_masked=reports_svc.mask_phone(payload.target_phone),
        status=appeal.status,
        statement=appeal.statement,
        created_at=appeal.created_at,
    )


@router.get("/appeals/{public_ref}", response_model=AppealOut)
def get_appeal(public_ref: str, db: Session = Depends(get_db)):
    appeal = db.execute(select(Appeal).where(Appeal.public_ref == public_ref)).scalar_one_or_none()
    if appeal is None:
        raise HTTPException(status_code=404, detail="Contestation introuvable.")
    target = db.get(Target, appeal.target_id)
    return AppealOut(
        id=appeal.id,
        public_ref=appeal.public_ref,
        target_phone_masked=reports_svc.mask_phone(decrypt_str(target.phone_enc) or "") if target else "—",
        status=appeal.status,
        statement=appeal.statement,
        created_at=appeal.created_at,
        decided_at=appeal.decided_at,
        decision_reason=appeal.decision_reason,
    )


@router.get("/stats", response_model=dict)
def community_stats(user: User = Depends(active_user), db: Session = Depends(get_db)):
    from ..services.spam import community_stats as stats

    base = stats(db)
    s = get_settings()
    published = db.execute(
        select(func.count(Target.id)).where(
            Target.verified_reports >= s.min_verifications_to_publish,
            Target.distinct_reporters >= s.min_verifications_to_publish,
        )
    ).scalar_one()
    submitted = db.execute(
        select(func.count(Report.id)).where(Report.status == ReportStatus.SUBMITTED)
    ).scalar_one()
    mine = reports_svc.reporting_statistics(db, user.id)
    return {
        **base,
        "published_targets": int(published),
        "reports_submitted_total": int(submitted),
        "my_stats": mine,
        "honesty_note": (
            "« Suspensions confirmées » ne compte que les cas confirmés par une source vérifiable (webhook Meta "
            "ou constat d'un modérateur). Les simples déclarations d'utilisateurs n'y sont jamais incluses."
        ),
    }
