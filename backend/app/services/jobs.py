"""File de traitement réelle (worker asynchrone intégré).

Contrairement à un tableau de bord qui affiche « envoyé » sans rien faire, ce
worker exécute effectivement les transmissions, respecte un débit volontairement
bas, réessaie en cas d'échec réseau, constitue les dossiers groupés et purge les
preuves arrivées à échéance de conservation.

En production multi-instances, la boucle peut être remplacée par Celery/RQ :
toute la logique métier vit dans `reports.execute_submission` et
`dossiers.build_dossier_pdf`.
"""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select

from ..config import get_settings
from ..constants import ReportStatus, SubmissionStatus, TargetStatus
from ..db import SessionLocal
from ..models import Dossier, Evidence, Report, ReportSubmission, Target
from . import audit, dossiers as dossier_svc, reports as reports_svc
from .connector import ConnectorError, ConnectorUnavailable
from .escalation import dispatch_dossier
from .storage import get_storage

log = logging.getLogger("signalpro.jobs")

WORKER_POLL_SECONDS = 5
SUBMISSION_PER_TICK = 3   # rythme volontairement bas : aucune rafale vers WhatsApp
MAX_ATTEMPTS = 3


class WorkerState:
    def __init__(self) -> None:
        self.running = False
        self.last_tick: datetime | None = None
        self.processed = 0
        self.failed = 0
        self.escalations = 0
        self.last_error: str | None = None

    def snapshot(self) -> dict:
        return {
            "running": self.running,
            "last_tick": self.last_tick.isoformat() if self.last_tick else None,
            "processed": self.processed,
            "failed": self.failed,
            "escalations": self.escalations,
            "last_error": self.last_error,
        }


state = WorkerState()


async def run_worker() -> None:
    state.running = True
    log.info("Worker SignalPro démarré : transmissions, dossiers, purge.")
    while True:
        try:
            await tick()
            state.last_tick = datetime.now(timezone.utc)
        except asyncio.CancelledError:
            state.running = False
            log.info("Worker SignalPro arrêté.")
            raise
        except Exception as exc:  # noqa: BLE001
            state.last_error = f"{type(exc).__name__}: {exc}"
            log.exception("Erreur dans le worker")
        await asyncio.sleep(WORKER_POLL_SECONDS)


def tick_sync() -> None:
    """Exécute un cycle complet du worker depuis un contexte synchrone.

    Utilisé par les tests d'intégration et par le script `scripts/run_jobs.py`
    pour forcer un passage sans attendre la boucle de fond.
    """
    asyncio.run(tick())


async def tick() -> None:
    with SessionLocal() as db:
        await _process_submissions(db)
        _build_dossiers(db)
        await _dispatch_dossiers(db)
        _purge_expired(db)


async def _process_submissions(db) -> None:
    rows = list(
        db.execute(
            select(ReportSubmission)
            .where(ReportSubmission.status == SubmissionStatus.QUEUED)
            .where(ReportSubmission.attempts < MAX_ATTEMPTS)
            .order_by(ReportSubmission.id)
            .limit(SUBMISSION_PER_TICK)
        ).scalars()
    )
    for sub in rows:
        try:
            await reports_svc.execute_submission(db, sub)
            db.commit()
            if sub.status == SubmissionStatus.SUCCEEDED:
                state.processed += 1
            elif sub.status == SubmissionStatus.FAILED:
                state.failed += 1
            # Espacement volontaire : les limites de WhatsApp sont respectées, jamais contournées.
            await asyncio.sleep(2)
        except (ConnectorError, ConnectorUnavailable) as exc:
            db.rollback()
            state.last_error = str(exc)[:300]
            log.warning("Transmission %s reportée : %s", sub.id, exc)
        except Exception:  # noqa: BLE001
            db.rollback()
            state.failed += 1
            log.exception("Échec du traitement de la transmission %s", sub.id)


def _build_dossiers(db) -> None:
    """Constitue un dossier groupé dès que la cible atteint le seuil de vérifications."""
    s = get_settings()
    candidates = list(
        db.execute(
            select(Target).where(
                Target.verified_reports >= s.min_verifications_to_escalate,
                Target.distinct_reporters >= s.min_verifications_to_escalate,
            )
        ).scalars()
    )
    for target in candidates:
        existing = db.execute(
            select(Dossier)
            .where(Dossier.target_id == target.id)
            .order_by(Dossier.id.desc())
            .limit(1)
        ).scalar_one_or_none()
        reports = reports_svc.eligible_reports_for_escalation(db, target)
        if len(reports) < s.min_verifications_to_escalate:
            continue
        if existing is not None and existing.report_count >= len(reports) and existing.dispatch_status in (
            "sent",
            "pending",
        ):
            continue
        try:
            pdf, info = dossier_svc.build_dossier_pdf(db, target)
        except Exception as exc:  # noqa: BLE001
            log.exception("Génération du dossier impossible pour la cible %s", target.id)
            state.last_error = f"Dossier {target.id}: {exc}"
            continue

        import secrets

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
            generated_at=datetime.now(timezone.utc),
            dispatch_status="pending",
        )
        db.add(dossier)
        db.flush()
        audit.log(
            db,
            action="dossier.built",
            entity_type="dossier",
            entity_id=dossier.id,
            detail={"target_id": target.id, "reports": len(reports), "sha256": info["sha256"]},
        )
        state.escalations += 1
    db.commit()


async def _dispatch_dossiers(db) -> None:
    pending = list(
        db.execute(
            select(Dossier).where(Dossier.dispatch_status == "pending").order_by(Dossier.id).limit(2)
        ).scalars()
    )
    for dossier in pending:
        target = db.get(Target, dossier.target_id)
        try:
            outcome = await dispatch_dossier(db, dossier, target)
            dossier.dispatch_status = outcome["status"]
            dossier.dispatch_channel = outcome["channel"]
            dossier.dispatch_ref = outcome.get("ref")
            dossier.dispatch_error = outcome.get("error")
            dossier.dispatched_at = datetime.now(timezone.utc)
            audit.log(
                db,
                action="dossier.dispatch",
                entity_type="dossier",
                entity_id=dossier.id,
                detail={"status": outcome["status"], "channel": outcome["channel"], "ref": outcome.get("ref")},
            )
        except Exception as exc:  # noqa: BLE001
            db.rollback()
            log.warning("Envoi du dossier %s impossible : %s", dossier.public_ref, exc)
            continue
        db.commit()


def _purge_expired(db) -> None:
    """Supprime les données dont la durée de conservation est dépassée (RGPD)."""
    s = get_settings()
    cutoff = datetime.now(timezone.utc) - timedelta(days=s.retention_days_evidence)
    old_reports = list(
        db.execute(
            select(Report).where(
                Report.created_at < cutoff,
                Report.status.in_([ReportStatus.REJECTED, ReportStatus.REJECTED_NO_EVIDENCE, ReportStatus.CLOSED]),
            )
        ).scalars()
    )
    storage = get_storage()
    for report in old_reports:
        for ev in list(report.evidences):
            try:
                storage.delete(ev.storage_key)
            except Exception:  # noqa: BLE001
                log.warning("Preuve %s non supprimée du stockage", ev.id)
            db.delete(ev)
        db.flush()
        audit.log(db, action="retention.evidence_purged", entity_type="report", entity_id=report.id)
    if old_reports:
        db.commit()
        log.info("Conservation : %d signalement(s) rejeté(s) purgé(s).", len(old_reports))
