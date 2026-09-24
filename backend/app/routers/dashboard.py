"""Tableau de bord utilisateur : suivi, statistiques, notifications, exports."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import PlainTextResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..api import active_user
from ..constants import CATEGORY_LABELS_FR, ReportStatus, TargetStatus
from ..db import get_db
from ..models import LinkedDevice, Notification, Report, Target, User
from ..schemas import NotificationOut
from ..security import decrypt_str
from ..services import audit, notify
from ..services import limits
from ..services import reports as reports_svc

router = APIRouter(prefix="/dashboard", tags=["Tableau de bord"])


@router.get("/overview", response_model=dict)
def overview(user: User = Depends(active_user), db: Session = Depends(get_db)):
    stats = reports_svc.reporting_statistics(db, user.id)
    usage = limits.usage_snapshot(db, user.id, user.strikes, user.status)
    devices = db.execute(
        select(LinkedDevice).where(LinkedDevice.user_id == user.id).order_by(LinkedDevice.id.desc())
    ).scalars()
    device_list = [
        {
            "id": d.id,
            "label": d.label,
            "mode": d.mode,
            "status": d.status,
            "masked": reports_svc.mask_phone(decrypt_str(d.wa_number_enc) or "") if d.wa_number_enc else None,
            "contacts_count": d.contacts_count,
        }
        for d in devices
    ]
    by_status = stats["by_status"]
    return {
        "usage": usage,
        "devices": device_list,
        "reports": {
            "total": sum(by_status.values()),
            "pending_evidence": by_status.get(ReportStatus.PENDING_EVIDENCE, 0),
            "pending_verification": by_status.get(ReportStatus.PENDING_VERIFICATION, 0),
            "verified": by_status.get(ReportStatus.VERIFIED, 0),
            "submitted": by_status.get(ReportStatus.SUBMITTED, 0),
            "rejected": by_status.get(ReportStatus.REJECTED, 0) + by_status.get(ReportStatus.REJECTED_ABUSIVE, 0),
        },
        "suspensions": {
            "confirmed_from_my_reports": stats["my_targets_suspended_confirmed"],
            "note": (
                "Seules les suspensions confirmées par Meta ou constatées par un modérateur sont comptées ici. "
                "Les déclarations d'utilisateurs n'y figurent jamais."
            ),
        },
        "unread_notifications": int(
            db.execute(
                select(func.count(Notification.id)).where(
                    Notification.user_id == user.id, Notification.read_at.is_(None)
                )
            ).scalar_one()
        ),
    }


@router.get("/notifications", response_model=list[NotificationOut])
def notifications(limit: int = 50, user: User = Depends(active_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(Notification).where(Notification.user_id == user.id).order_by(Notification.id.desc()).limit(min(limit, 200))
    ).scalars()
    return [NotificationOut.model_validate(n) for n in rows]


@router.post("/notifications/read", response_model=dict)
def mark_read(notification_id: int | None = None, user: User = Depends(active_user), db: Session = Depends(get_db)):
    import datetime as dt

    q = select(Notification).where(Notification.user_id == user.id, Notification.read_at.is_(None))
    if notification_id:
        q = q.where(Notification.id == notification_id)
    rows = list(db.execute(q).scalars())
    for n in rows:
        n.read_at = dt.datetime.now(dt.timezone.utc)
    db.commit()
    return {"ok": True, "marked": len(rows)}


@router.get("/export/reports", response_class=PlainTextResponse)
def export_reports(user: User = Depends(active_user), db: Session = Depends(get_db)):
    csv_text = reports_svc.export_my_reports_csv(db, user)
    audit.log(db, action="reports.exported", actor_user_id=user.id, entity_type="user", entity_id=user.id)
    db.commit()
    return PlainTextResponse(
        csv_text,
        headers={"Content-Disposition": "attachment; filename=mes_signalements.csv"},
    )


@router.get("/alerts", response_model=list[dict])
def alerts(user: User = Depends(active_user), db: Session = Depends(get_db)):
    """Alertes : un numéro confirmé malveillant a-t-il une conversation sur mes appareils ?

    L'appartenance est calculée par empreinte : le serveur ne compare que des
    empreintes HMAC, jamais des carnets d'adresses en clair.
    """
    from ..models import ContactIndex

    rows = db.execute(
        select(ContactIndex, Target)
        .join(Target, Target.phone_fp == ContactIndex.peer_fp)
        .where(
            ContactIndex.user_id == user.id,
            Target.verified_reports > 0,
            Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED]),
        )
    ).all()
    out = []
    for contact, target in rows:
        phone = decrypt_str(target.phone_enc) or ""
        out.append(
            {
                "target_id": target.id,
                "phone_masked": reports_svc.mask_phone(phone),
                "category": target.main_category,
                "category_label": CATEGORY_LABELS_FR.get(target.main_category or "other", "—"),
                "verified_reports": target.verified_reports,
                "last_message_at": contact.last_message_at.isoformat() if contact.last_message_at else None,
                "advice": "Ce numéro confirmé malveillant a une conversation sur votre compte : ne répondez pas, "
                          "conservez les messages, bloquez-le.",
            }
        )
    return out


@router.post("/alerts/scan", response_model=dict)
def scan_alerts(user: User = Depends(active_user), db: Session = Depends(get_db)):
    """Parcourt les conversations vérifiées et notifie les correspondances réelles."""
    from ..models import ContactIndex

    rows = db.execute(
        select(ContactIndex, Target)
        .join(Target, Target.phone_fp == ContactIndex.peer_fp)
        .where(
            ContactIndex.user_id == user.id,
            Target.verified_reports > 0,
            Target.status.in_([TargetStatus.CONFIRMED_MALICIOUS, TargetStatus.SUSPENSION_CONFIRMED]),
        )
    ).all()
    created = 0
    for contact, target in rows:
        phone = decrypt_str(target.phone_enc) or ""
        notify.push(
            db,
            user.id,
            "malicious_contact",
            "Un numéro malveillant confirmé vous a contacté",
            f"{reports_svc.mask_phone(phone)} ({CATEGORY_LABELS_FR.get(target.main_category or '', '—')}) "
            f"· {target.verified_reports} signalements vérifiés.",
            {"target_id": target.id, "device_id": contact.device_id},
        )
        created += 1
    audit.log(db, action="alerts.scanned", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"matches": created})
    db.commit()
    return {
        "matches": created,
        "note": "Les alertes sont calculées à partir des conversations vérifiées de vos appareils liés.",
    }
