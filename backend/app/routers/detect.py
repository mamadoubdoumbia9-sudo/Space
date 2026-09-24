"""Détection de spam : signatures versionnées pour le client Android + analyse serveur.

Le téléchargement des signatures permet à l'application mobile de travailler
HORS LIGNE : elle analyse les conversations localement, sans envoyer le moindre
message au serveur. Le serveur n'analyse que les extraits que l'utilisateur
soumet explicitement.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..api import active_user
from ..constants import Role
from ..db import get_db
from ..models import DetectionHit, Report, SpamSignature, User
from ..schemas import ScanIn, ScanOut, SignatureOut, SignaturesOut
from ..services import audit
from ..services import limits
from ..services import reports as reports_svc
from ..services.spam import active_signatures, scan_text, seed_signatures, signatures_fingerprint

router = APIRouter(prefix="/detect", tags=["Détection automatique"])


@router.get("/signatures", response_model=SignaturesOut)
def get_signatures(
    since_version: str | None = None,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    seed_signatures(db, ) if db.execute(select(SpamSignature.id).limit(1)).scalar_one_or_none() is None else None
    version = signatures_fingerprint(db)
    rows = active_signatures(db)
    if since_version and since_version == version:
        return SignaturesOut(version=version, count=0, signatures=[])
    return SignaturesOut(
        version=version,
        count=len(rows),
        signatures=[
            SignatureOut(kind=s.kind, value_display=s.value_display, severity=s.severity, category=s.category)
            for s in rows
        ],
    )


@router.post("/scan", response_model=ScanOut)
def scan(
    payload: ScanIn,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    try:
        limits.consume_action(db, user.id, "scan")
    except limits.RateLimitExceeded as exc:
        db.rollback()
        raise HTTPException(status_code=429, detail=str(exc)) from exc

    result = scan_text(db, payload.text, payload.peer_phone)
    hit = DetectionHit(
        user_id=user.id,
        device_id=payload.device_id,
        target_fp=reports_svc.fingerprint(payload.peer_phone) if payload.peer_phone else None,
        category=(result["matches"][0]["category"] if result["matches"] else "spam"),
        match_kind=(result["matches"][0]["kind"] if result["matches"] else "keyword"),
        matched_sample=None,  # aucun extrait de message n'est conservé côté serveur
        score=result["score"],
    )
    db.add(hit)
    audit.log(db, action="detect.scan", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"score": result["score"], "matches": len(result["matches"]), "text_stored": False})
    db.commit()
    return ScanOut(**result)


@router.get("/hits", response_model=list[dict])
def my_hits(limit: int = 50, user: User = Depends(active_user), db: Session = Depends(get_db)):
    rows = db.execute(
        select(DetectionHit).where(DetectionHit.user_id == user.id).order_by(DetectionHit.id.desc()).limit(min(limit, 200))
    ).scalars()
    return [
        {
            "id": h.id,
            "category": h.category,
            "match_kind": h.match_kind,
            "score": h.score,
            "converted_report_id": h.converted_report_id,
            "created_at": h.created_at.isoformat(),
        }
        for h in rows
    ]


@router.post("/hits/{hit_id}/convert", response_model=dict)
def convert_hit(
    hit_id: int,
    target_phone: str,
    category: str,
    occurred_at: str,
    description: str,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Transforme une détection en signalement — en repassant par toutes les règles."""
    from ..services.evidence import parse_occurred_at

    hit = db.get(DetectionHit, hit_id)
    if hit is None or hit.user_id != user.id:
        raise HTTPException(status_code=404, detail="Détection introuvable.")
    when = parse_occurred_at(occurred_at)
    if when is None:
        raise HTTPException(status_code=422, detail="Date invalide (ex. 2026-03-12 14:22).")
    try:
        limits.consume_report_creation(db, user.id, count=1)
        report = reports_svc.create_report(
            db,
            user=user,
            target_phone=target_phone,
            category=category,
            occurred_at=when,
            description=description,
            source="auto_detection",
        )
    except (reports_svc.BusinessRuleError, limits.RateLimitExceeded) as exc:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    hit.converted_report_id = report.id
    db.commit()
    return {"report_id": report.id, "public_ref": report.public_ref, "note": "Preuve toujours obligatoire."}


@router.post("/signatures/reload", response_model=dict)
def reload_signatures(user: User = Depends(active_user), db: Session = Depends(get_db)):
    if user.role not in (Role.MODERATOR, Role.ADMIN):
        raise HTTPException(status_code=403, detail="Réservé aux modérateurs.")
    created = seed_signatures(db)
    audit.log(db, action="signatures.reloaded", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"created": created})
    db.commit()
    return {"created": created, "version": signatures_fingerprint(db)}
