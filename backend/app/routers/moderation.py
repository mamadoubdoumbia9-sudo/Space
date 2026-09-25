"""Console de modération : relecture des signalements, contestations, dossiers, sanctions."""
from __future__ import annotations

import datetime as dt
import json

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..api import client_ip, moderator_user
from ..constants import AppealStatus, ReportStatus, Role, SuspensionSource, TargetStatus, UserStatus
from ..db import get_db
from ..models import Appeal, AuditLog, Dossier, Evidence, Report, Target, User
from ..schemas import DecisionIn, ModerationEvidenceOut, ModerationItemOut
from ..security import decrypt_str
from ..services import audit, dossiers as dossier_svc, notify
from ..services import reports as reports_svc
from ..services.storage import get_storage

router = APIRouter(prefix="/moderation", tags=["Modération"])


@router.get("/queue", response_model=list[ModerationItemOut])
def queue(
    limit: int = 100,
    status_filter: str = ReportStatus.PENDING_VERIFICATION,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    rows = db.execute(
        select(Report).where(Report.status == status_filter).order_by(Report.id).limit(min(limit, 300))
    ).scalars()
    items: list[ModerationItemOut] = []
    for r in rows:
        phone = decrypt_str(r.target.phone_enc) or ""
        target = r.target
        reporter = db.get(User, r.reporter_id)
        evidences = []
        for ev in r.evidences:
            try:
                ids = json.loads(ev.message_ids or "[]")
            except (TypeError, ValueError):
                ids = []
            evidences.append(
                ModerationEvidenceOut(
                    id=ev.id,
                    kind=ev.kind,
                    filename=ev.filename,
                    mime=ev.mime,
                    size_bytes=ev.size_bytes,
                    sha256=(ev.sha256[:16] + "…") if ev.sha256 else None,
                    encrypted=bool(ev.encrypted),
                    integrity_ok=bool(ev.integrity_ok),
                    duplicate_of=ev.duplicate_of,
                    message_ids=ids,
                    validation_detail=ev.validation_detail,
                )
            )
        items.append(
            ModerationItemOut(
                entity_type="report",
                entity_id=r.id,
                public_ref=r.public_ref,
                # Identifiants nécessaires pour décider réellement : sans le
                # target_id, un modérateur ne pourrait ni confirmer une
                # suspension ni constituer un dossier.
                target_id=r.target_id,
                target_status=target.status if target else None,
                suspension_status=target.suspension_status if target else None,
                verified_reports=target.verified_reports if target else 0,
                distinct_reporters=target.distinct_reporters if target else 0,
                target_phone_masked=reports_svc.mask_phone(phone),
                category=r.category,
                summary=r.description[:300],
                description=r.description,
                occurred_at=r.occurred_at,
                contact_proof_method=r.contact_proof_method,
                source=r.source,
                reporter_id=r.reporter_id,
                reporter_strikes=reporter.strikes if reporter else None,
                submissions_count=len(r.submissions),
                created_at=r.created_at,
                evidences_count=len(r.evidences),
                evidences=evidences,
                auto_flags=json.loads(r.auto_flags or "[]"),
            )
        )
    return items


@router.get("/appeals", response_model=list[dict])
def appeals(
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    rows = db.execute(
        select(Appeal).where(Appeal.status.in_([AppealStatus.SUBMITTED, AppealStatus.UNDER_REVIEW])).order_by(Appeal.id)
    ).scalars()
    out = []
    for a in rows:
        target = db.get(Target, a.target_id)
        out.append(
            {
                "id": a.id,
                "public_ref": a.public_ref,
                "target_phone_masked": reports_svc.mask_phone(decrypt_str(target.phone_enc) or "") if target else "—",
                "target_id": a.target_id,
                "claimant_contact": decrypt_str(a.claimant_contact_enc),
                "statement": a.statement,
                "evidence_note": a.evidence_note,
                "status": a.status,
                "created_at": a.created_at.isoformat(),
            }
        )
    return out


@router.post("/reports/{report_id}/decision", response_model=dict)
def decide_report(
    report_id: int,
    payload: DecisionIn,
    request: Request,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    report = db.get(Report, report_id)
    if report is None:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    try:
        reports_svc.moderator_decide(
            db, moderator=moderator, report=report, decision=payload.decision, reason=payload.reason,
            ip=client_ip(request),
        )
    except reports_svc.BusinessRuleError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    from ..models import ModerationDecision

    db.add(
        ModerationDecision(
            moderator_id=moderator.id,
            entity_type="report",
            entity_id=report.id,
            decision=payload.decision,
            reason=payload.reason,
        )
    )
    db.commit()
    return {
        "ok": True,
        "report_id": report.id,
        "status": report.status,
        "target_status": report.target.status,
        "verified_reports": report.target.verified_reports,
        "escalation_ready": reports_svc.should_escalate(db, report.target),
    }


@router.post("/targets/{target_id}/suspension", response_model=dict)
def record_suspension(
    target_id: int,
    confirmed: bool,
    evidence: str,
    request: Request,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    target = db.get(Target, target_id)
    if target is None:
        raise HTTPException(status_code=404, detail="Cible introuvable.")
    if confirmed:
        reports_svc.record_suspension(
            db, target=target, source=SuspensionSource.MODERATOR_VERIFIED, evidence=evidence, actor=moderator
        )
    else:
        target.suspension_status = "not_confirmed"
        target.suspension_source = SuspensionSource.UNKNOWN
        target.suspension_evidence = evidence
    db.flush()
    from ..models import ModerationDecision

    db.add(
        ModerationDecision(
            moderator_id=moderator.id,
            entity_type="target",
            entity_id=target.id,
            decision="confirm_suspension" if confirmed else "deny_suspension",
            reason=evidence,
        )
    )
    db.commit()
    return {"ok": True, "target_id": target.id, "status": target.suspension_status, "source": target.suspension_source}


@router.post("/appeals/{appeal_id}/decision", response_model=dict)
def decide_appeal(
    appeal_id: int,
    payload: DecisionIn,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    appeal = db.get(Appeal, appeal_id)
    if appeal is None:
        raise HTTPException(status_code=404, detail="Contestation introuvable.")
    if payload.decision not in ("verify", "reject", "clear_target"):
        raise HTTPException(status_code=400, detail="Décision attendue : verify (acceptée), reject (refusée), clear_target.")
    target = db.get(Target, appeal.target_id)

    if payload.decision == "clear_target":
        appeal.status = AppealStatus.ACCEPTED
        if target:
            target.status = TargetStatus.CLEARED
            target.delisted_at = dt.datetime.now(dt.timezone.utc)
            # Les signalements en cause sont retirés de la vitrine publique et marqués rejetés.
            for report in db.execute(
                select(Report).where(Report.target_id == target.id, Report.status == ReportStatus.PENDING_VERIFICATION)
            ).scalars():
                report.status = ReportStatus.REJECTED
                report.decision_reason = "Retiré après contestation acceptée."
    elif payload.decision == "verify":
        appeal.status = AppealStatus.REJECTED  # la contestation est rejetée, le numéro reste listé
        if target:
            target.status = TargetStatus.CONFIRMED_MALICIOUS
    else:
        appeal.status = AppealStatus.REJECTED

    appeal.moderator_id = moderator.id
    appeal.decided_at = dt.datetime.now(dt.timezone.utc)
    appeal.decision_reason = payload.reason
    db.flush()

    from ..models import ModerationDecision

    db.add(
        ModerationDecision(
            moderator_id=moderator.id,
            entity_type="appeal",
            entity_id=appeal.id,
            decision=payload.decision,
            reason=payload.reason,
        )
    )
    audit.log(db, action=f"appeal.{payload.decision}", actor_user_id=moderator.id, actor_role=moderator.role,
              entity_type="appeal", entity_id=appeal.id, detail={"reason": payload.reason, "target_id": appeal.target_id})
    db.commit()
    return {"ok": True, "appeal_status": appeal.status, "target_status": target.status if target else None}


@router.post("/users/{user_id}/sanction", response_model=dict)
def sanction_user(
    user_id: int,
    payload: DecisionIn,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    if moderator.role != Role.ADMIN and payload.decision == "ban_user":
        raise HTTPException(status_code=403, detail="Seul un administrateur peut bannir un compte.")
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="Utilisateur introuvable.")
    now = dt.datetime.now(dt.timezone.utc)
    if payload.decision == "ban_user":
        user.status = UserStatus.BANNED
        user.banned_at = now
        user.banned_reason = payload.reason
    elif payload.decision == "suspend_user":
        user.status = UserStatus.SUSPENDED
        user.banned_reason = payload.reason
    elif payload.decision == "reinstate_user":
        user.status = UserStatus.ACTIVE
        user.banned_at = None
        user.banned_reason = None
    else:
        raise HTTPException(status_code=400, detail="Décision attendue : ban_user, suspend_user, reinstate_user.")
    audit.log(db, action=f"user.{payload.decision}", actor_user_id=moderator.id, actor_role=moderator.role,
              entity_type="user", entity_id=user.id, detail={"reason": payload.reason, "strikes": user.strikes})
    notify.push(db, user.id, f"user_{payload.decision}", "Décision vous concernant", payload.reason)
    db.commit()
    return {"ok": True, "user_id": user.id, "status": user.status, "strikes": user.strikes}


@router.post("/users/{user_id}/strike", response_model=dict)
def add_strike(
    user_id: int,
    reason: str,
    report_id: int | None = None,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    try:
        user = reports_svc.issue_strike(
            db, user_id=user_id, report_id=report_id, reason=reason, issued_by=moderator.id
        )
    except reports_svc.BusinessRuleError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    db.commit()
    return {
        "ok": True,
        "user_id": user.id,
        "strikes": user.strikes,
        "status": user.status,
        "note": "Le bannissement est automatique au seuil configuré (MAX_ABUSIVE_STRIKES).",
    }


@router.post("/targets/{target_id}/dossier", response_model=dict)
def build_dossier(
    target_id: int,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    target = db.get(Target, target_id)
    if target is None:
        raise HTTPException(status_code=404, detail="Cible introuvable.")
    reports = reports_svc.eligible_reports_for_escalation(db, target)
    if len(reports) < 1:
        raise HTTPException(
            status_code=409,
            detail="Aucun signalement vérifié : un dossier ne peut pas être constitué sans preuve examinée.",
        )
    import secrets

    pdf, info = dossier_svc.build_dossier_pdf(db, target)
    ref = "DOS-" + secrets.token_hex(4).upper()
    key = f"dossiers/{target.id}/{ref}.pdf"
    get_storage().put(key, pdf)
    dossier = Dossier(
        public_ref=ref,
        target_id=target.id,
        report_count=len(reports),
        distinct_reporters=target.distinct_reporters,
        pdf_sha256=info["sha256"],
        storage_key=key,
        generated_at=dt.datetime.now(dt.timezone.utc),
        dispatch_status="pending",
    )
    db.add(dossier)
    db.flush()
    audit.log(db, action="dossier.built_manual", actor_user_id=moderator.id, entity_type="dossier",
              entity_id=dossier.id, detail={"target_id": target.id, "reports": len(reports)})
    db.commit()
    return {"ok": True, "dossier_ref": ref, "pdf_sha256": info["sha256"], "reports": len(reports),
            "download": f"/moderation/dossiers/{ref}/download"}


@router.get("/dossiers", response_model=list[dict])
def list_dossiers(moderator: User = Depends(moderator_user), db: Session = Depends(get_db)):
    rows = db.execute(select(Dossier).order_by(Dossier.id.desc()).limit(200)).scalars()
    return [
        {
            "public_ref": d.public_ref,
            "target_id": d.target_id,
            "reports": d.report_count,
            "reporters": d.distinct_reporters,
            "sha256": d.pdf_sha256,
            "dispatch_status": d.dispatch_status,
            "dispatch_channel": d.dispatch_channel,
            "dispatch_error": d.dispatch_error,
            "generated_at": d.generated_at.isoformat(),
            "dispatched_at": d.dispatched_at.isoformat() if d.dispatched_at else None,
        }
        for d in rows
    ]


@router.get("/dossiers/{public_ref}/download")
def download_dossier(
    public_ref: str,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    dossier = db.execute(select(Dossier).where(Dossier.public_ref == public_ref)).scalar_one_or_none()
    if dossier is None:
        raise HTTPException(status_code=404, detail="Dossier introuvable.")
    pdf = get_storage().get(dossier.storage_key)
    import hashlib

    if hashlib.sha256(pdf).hexdigest() != dossier.pdf_sha256:
        raise HTTPException(status_code=500, detail="Intégrité du dossier compromise : téléchargement bloqué.")
    audit.log(db, action="dossier.downloaded", actor_user_id=moderator.id, entity_type="dossier",
              entity_id=dossier.id)
    db.commit()
    return Response(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{dossier.public_ref}.pdf"'},
    )


@router.get("/audit", response_model=list[dict])
def audit_trail(
    limit: int = 200,
    action: str | None = None,
    entity_id: int | None = None,
    moderator: User = Depends(moderator_user),
    db: Session = Depends(get_db),
):
    rows = audit.recent(db, limit=limit, action=action, entity_id=entity_id)
    return [
        {
            "id": r.id,
            "action": r.action,
            "actor_user_id": r.actor_user_id,
            "actor_role": r.actor_role,
            "entity_type": r.entity_type,
            "entity_id": r.entity_id,
            "ip": r.ip,
            "detail": r.detail,
            "created_at": r.created_at.isoformat(),
        }
        for r in rows
    ]


@router.get("/stats", response_model=dict)
def moderation_stats(moderator: User = Depends(moderator_user), db: Session = Depends(get_db)):
    def count(q) -> int:
        return int(db.execute(q).scalar_one())

    return {
        "reports_pending_verification": count(
            select(func.count(Report.id)).where(Report.status == ReportStatus.PENDING_VERIFICATION)
        ),
        "reports_pending_evidence": count(
            select(func.count(Report.id)).where(Report.status == ReportStatus.PENDING_EVIDENCE)
        ),
        "reports_verified": count(select(func.count(Report.id)).where(Report.status == ReportStatus.VERIFIED)),
        "reports_rejected_abusive": count(
            select(func.count(Report.id)).where(Report.status == ReportStatus.REJECTED_ABUSIVE)
        ),
        "appeals_open": count(
            select(func.count(Appeal.id)).where(Appeal.status.in_([AppealStatus.SUBMITTED, AppealStatus.UNDER_REVIEW]))
        ),
        "targets_confirmed": count(
            select(func.count(Target.id)).where(Target.status == TargetStatus.CONFIRMED_MALICIOUS)
        ),
        "users_banned": count(select(func.count(User.id)).where(User.status == UserStatus.BANNED)),
        "evidence_stored": count(select(func.count(Evidence.id))),
        "audit_entries": count(select(func.count(AuditLog.id))),
    }
