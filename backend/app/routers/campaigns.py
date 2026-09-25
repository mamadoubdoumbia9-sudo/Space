"""Campagnes : « signaler ce numéro N fois » — exécution réelle, jamais simulée.

L'utilisateur indique le numéro cible ET le nombre de signalements souhaité. Le
serveur calcule ensuite ce qui est réellement possible : le nombre de comptes
distincts, réellement contactés par la cible, consentants, disposant d'un appareil
connecté et n'ayant pas encore signalé ce numéro. Une campagne ne dépasse jamais
ce nombre : on n'envoie pas de signalements fabriqués depuis des comptes qui n'ont
jamais reçu de message.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..api import active_user, client_ip
from ..constants import CampaignStatus
from ..db import get_db
from ..models import Campaign, LinkedDevice, Target, User
from ..schemas import CampaignCreateIn, CampaignOut
from ..security import decrypt_str
from ..services import audit, notify
from ..services import limits
from ..services import reports as reports_svc

router = APIRouter(prefix="/campaigns", tags=["Campagnes de signalement"])


def _campaign_out(campaign: Campaign, target: Target | None) -> CampaignOut:
    phone = decrypt_str(target.phone_enc) if target else ""
    return CampaignOut(
        id=campaign.id,
        public_ref=campaign.public_ref,
        target_phone_masked=reports_svc.mask_phone(phone) if phone else "—",
        requested_count=campaign.requested_count,
        eligible_count=campaign.eligible_count,
        executed_count=campaign.executed_count,
        succeeded_count=campaign.succeeded_count,
        status=campaign.status,
        notes=campaign.notes,
        last_error=campaign.last_error,
        created_at=campaign.created_at,
        eligibility_explanation=(
            f"{campaign.eligible_count} compte(s) lié(s) ont réellement reçu des messages de ce numéro et ont "
            "consenti à signaler. Le système ne peut pas en mobiliser davantage : il ne crée jamais de compte "
            "fictif et ne contourne jamais les quotas par utilisateur."
        ),
    )


@router.post("/preview", response_model=dict)
def preview_campaign(
    payload: CampaignCreateIn,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Dit ce qui est RÉELLEMENT exécutable avant toute création."""
    from ..config import get_settings

    target = reports_svc.get_target(db, payload.target_phone, create=False)
    eligible = reports_svc.eligible_accounts_for_target(db, payload.target_phone)
    mine = db.execute(
        select(LinkedDevice).where(LinkedDevice.user_id == user.id, LinkedDevice.status == "connected")
    ).scalars().all()
    return {
        "target_in_database": target is not None,
        "requested_count": payload.requested_count,
        "requested_hard_cap": get_settings().campaign_hard_cap,
        "eligible_accounts": len(eligible),
        "executable_count": min(payload.requested_count, len(eligible)),
        "your_connected_devices": len(mine),
        "explanation": (
            "Chaque signalement est émis depuis un compte DIFFÉRENT qui a réellement reçu des messages de la "
            "cible. Demander 100 signalements ne crée pas 100 signalements : c'est le nombre de personnes "
            "réellement concernées qui détermine le volume possible."
        ),
        "blocking_reason": None
        if eligible
        else (
            "Aucun compte éligible pour ce numéro : personne n'a encore lié un WhatsApp ayant reçu des "
            "messages de ce numéro. Aucun envoi ne sera effectué."
        ),
    }


@router.post("", response_model=CampaignOut, status_code=201)
def create_campaign(
    payload: CampaignCreateIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    try:
        limits.consume_campaign(db, user.id)
        limits.consume_action(db, user.id, "campaign")
    except limits.RateLimitExceeded as exc:
        db.rollback()
        raise HTTPException(status_code=429, detail=str(exc), headers={"Retry-After": str(exc.retry_after_s)}) from exc

    try:
        campaign = reports_svc.create_campaign(
            db,
            user=user,
            target_phone=payload.target_phone,
            requested_count=payload.requested_count,
            category=payload.category.value,
            occurred_at=payload.occurred_at,
            description=payload.description,
            consent_ack=payload.consent_ack,
        )
    except reports_svc.BusinessRuleError as exc:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    target = db.get(Target, campaign.target_id)
    notify.push(
        db,
        user.id,
        "campaign_created",
        f"Campagne {campaign.public_ref}",
        campaign.notes or "",
        {"campaign_id": campaign.id},
    )
    audit.log(db, action="campaign.created_by_user", actor_user_id=user.id, entity_type="campaign",
              entity_id=campaign.id, ip=client_ip(request),
              detail={"requested": payload.requested_count, "queued": campaign.executed_count})
    db.commit()
    return _campaign_out(campaign, target)


@router.get("", response_model=list[CampaignOut])
def list_campaigns(user: User = Depends(active_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(Campaign).where(Campaign.created_by == user.id).order_by(Campaign.id.desc()).limit(100)
    ).scalars()
    return [_campaign_out(c, db.get(Target, c.target_id)) for c in rows]


@router.get("/{campaign_id}", response_model=CampaignOut)
def get_campaign(campaign_id: int, user: User = Depends(active_user), db: Session = Depends(get_db)):
    campaign = db.get(Campaign, campaign_id)
    if campaign is None or (campaign.created_by != user.id and user.role == "user"):
        raise HTTPException(status_code=404, detail="Campagne introuvable.")
    return _campaign_out(campaign, db.get(Target, campaign.target_id))


@router.post("/{campaign_id}/cancel", response_model=CampaignOut)
def cancel_campaign(
    campaign_id: int,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    campaign = db.get(Campaign, campaign_id)
    if campaign is None or campaign.created_by != user.id:
        raise HTTPException(status_code=404, detail="Campagne introuvable.")
    if campaign.status in (CampaignStatus.COMPLETED, CampaignStatus.ABORTED):
        raise HTTPException(status_code=409, detail="Cette campagne est déjà terminée.")
    from ..constants import ReportStatus, SubmissionStatus
    from ..models import Report, ReportSubmission

    pending = db.execute(
        select(ReportSubmission).where(
            ReportSubmission.campaign_id == campaign.id, ReportSubmission.status == SubmissionStatus.QUEUED
        )
    ).scalars()
    cancelled = 0
    for sub in pending:
        sub.status = SubmissionStatus.SKIPPED
        sub.response_summary = "Annulé par l'utilisateur avant transmission."
        report = db.get(Report, sub.report_id)
        if report and report.status == ReportStatus.QUEUED:
            report.status = ReportStatus.VERIFIED
        cancelled += 1
    campaign.status = CampaignStatus.ABORTED
    campaign.last_error = f"Annulée par l'utilisateur : {cancelled} transmission(s) retirée(s) de la file."
    audit.log(db, action="campaign.cancelled", actor_user_id=user.id, entity_type="campaign",
              entity_id=campaign.id, detail={"cancelled": cancelled})
    db.commit()
    return _campaign_out(campaign, db.get(Target, campaign.target_id))
