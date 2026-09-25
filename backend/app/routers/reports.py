"""Signalements : création, preuves réelles, suivi, import CSV/Excel, export."""
from __future__ import annotations

import json
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import PlainTextResponse, Response
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..api import active_user, client_ip
from ..config import get_settings
from ..constants import (
    CATEGORY_LABELS_FR,
    EvidenceKind,
    InfractionCategory,
    ReportSource,
    ReportStatus,
    SubmissionAdapter,
    SubmissionStatus,
)
from ..db import get_db
from ..models import Evidence, LinkedDevice, Report, ReportSubmission, Target, User
from ..schemas import (
    CsvImportOut,
    CsvPreviewRow,
    EvidenceOut,
    ReportCreateIn,
    ReportListOut,
    ReportOut,
    SubmissionOut,
    UsageOut,
)
from ..security import decrypt_str, fingerprint
from ..services import audit, csv_import, notify
from ..services import limits
from ..services import reports as reports_svc
from ..services.connector import ConnectorError, ConnectorUnavailable, get_connector

router = APIRouter(prefix="/reports", tags=["Signalements"])

STATUS_LABELS = {
    ReportStatus.DRAFT: "Brouillon",
    ReportStatus.PENDING_EVIDENCE: "Preuve manquante",
    ReportStatus.REJECTED_NO_EVIDENCE: "Rejeté : aucune preuve valide",
    ReportStatus.PENDING_VERIFICATION: "En relecture par un modérateur",
    ReportStatus.VERIFIED: "Vérifié",
    ReportStatus.REJECTED: "Rejeté après examen",
    ReportStatus.REJECTED_ABUSIVE: "Rejeté : signalement abusif",
    ReportStatus.QUEUED: "En file de transmission",
    ReportStatus.SUBMITTED: "Transmis à WhatsApp",
    ReportStatus.CLOSED: "Clôturé",
}


def _report_out(report: Report, reveal_target: bool = False) -> ReportOut:
    target_phone = decrypt_str(report.target.phone_enc) or ""
    return ReportOut(
        id=report.id,
        public_ref=report.public_ref,
        target_phone_masked=reports_svc.mask_phone(target_phone),
        target_phone=target_phone if reveal_target else None,
        category=report.category,
        category_label=CATEGORY_LABELS_FR.get(report.category, report.category),
        occurred_at=report.occurred_at,
        description=report.description,
        status=report.status,
        status_label=STATUS_LABELS.get(report.status, report.status),
        source=report.source,
        contact_proof_method=report.contact_proof_method,
        contact_verified=bool(report.contact_proof_method)
        and report.contact_proof_method != "manual_declaration",
        created_at=report.created_at,
        decided_at=report.decided_at,
        decision_reason=report.decision_reason,
        target_status=report.target.status if report.target else None,
        suspension_status=report.target.suspension_status if report.target else None,
        evidences=[EvidenceOut.model_validate(e) for e in report.evidences],
        submissions=[SubmissionOut.model_validate(s) for s in report.submissions],
    )


@router.get("/usage", response_model=UsageOut)
def usage(user: User = Depends(active_user), db: Session = Depends(get_db)):
    snap = limits.usage_snapshot(db, user.id, user.strikes, user.status)
    return UsageOut(**snap)


@router.post("", response_model=ReportOut, status_code=201)
def create_report(
    payload: ReportCreateIn,
    request: Request,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Crée un signalement. La preuve est exigée juste après (endpoint /evidence).

    Aucun envoi vers WhatsApp n'a lieu à cette étape : un modérateur humain doit
    d'abord vérifier la preuve.
    """
    try:
        limits.consume_action(db, user.id, "create_report")
        limits.consume_report_creation(db, user.id, count=1)
    except limits.RateLimitExceeded as exc:
        db.rollback()
        raise HTTPException(status_code=429, detail=str(exc), headers={"Retry-After": str(exc.retry_after_s)}) from exc

    try:
        report = reports_svc.create_report(
            db,
            user=user,
            target_phone=payload.target_phone,
            category=payload.category.value,
            occurred_at=payload.occurred_at,
            description=payload.description,
            source=ReportSource.DIRECT,
            message_ids=payload.message_ids,
            declared_contact_method=payload.contact_proof_method,
        )
    except reports_svc.BusinessRuleError as exc:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    if payload.message_ids:
        ev = Evidence(
            report_id=report.id,
            kind=EvidenceKind.MESSAGE_ID,
            filename="message_ids.json",
            mime="application/json",
            size_bytes=len(json.dumps(payload.message_ids)),
            sha256="",
            storage_key="",
            encrypted=False,
            integrity_ok=True,
            validation_detail=json.dumps({"message_ids": payload.message_ids, "checked": True}),
            message_ids=json.dumps(payload.message_ids),
        )
        # Validation réelle du format des identifiants avant enregistrement
        from ..services.evidence import validate_upload

        res = validate_upload(
            kind=EvidenceKind.MESSAGE_ID,
            filename="message_ids.json",
            data=json.dumps(payload.message_ids).encode(),
            declared_mime="application/json",
            message_ids=payload.message_ids,
        )
        if not res.ok:
            db.rollback()
            raise HTTPException(status_code=422, detail="Identifiants de message refusés : " + " ".join(res.errors))
        ev.sha256 = res.sha256
        db.add(ev)
        db.flush()
        reports_svc.refresh_report_status(db, report)

    if payload.store_messages and payload.message_excerpt:
        reports_svc.store_message_snapshot(
            db, report=report, user=user, excerpt=payload.message_excerpt, consent=True
        )

    audit.log(db, action="report.requested", actor_user_id=user.id, entity_type="report", entity_id=report.id,
              ip=client_ip(request), detail={"category": payload.category.value, "source": "direct"})
    db.refresh(report)
    out = _report_out(report)
    db.commit()
    return out


@router.post("/{report_id}/evidence", response_model=EvidenceOut, status_code=201)
async def upload_evidence(
    report_id: int,
    request: Request,
    kind: str = Form(...),
    file: UploadFile = File(...),
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    report = db.get(Report, report_id)
    if report is None or report.reporter_id != user.id:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    if report.status in (ReportStatus.SUBMITTED, ReportStatus.CLOSED, ReportStatus.REJECTED_ABUSIVE):
        raise HTTPException(status_code=409, detail="Ce signalement n'accepte plus de preuve.")
    if kind not in {k.value for k in EvidenceKind}:
        raise HTTPException(status_code=400, detail=f"Type de preuve inconnu : {kind}")

    try:
        limits.consume_action(db, user.id, "upload_evidence")
    except limits.RateLimitExceeded as exc:
        db.rollback()
        raise HTTPException(status_code=429, detail=str(exc)) from exc

    data = await file.read()
    try:
        ev = reports_svc.attach_evidence(
            db,
            report=report,
            user=user,
            kind=kind,
            filename=file.filename or f"preuve-{kind}",
            data=data,
            declared_mime=file.content_type,
        )
    except reports_svc.BusinessRuleError as exc:
        db.rollback()
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    out = EvidenceOut.model_validate(ev)
    db.commit()
    return out


@router.get("", response_model=ReportListOut)
def list_reports(
    status_filter: str | None = None,
    limit: int = 50,
    offset: int = 0,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    base = select(Report).where(Report.reporter_id == user.id)
    count_q = select(func.count(Report.id)).where(Report.reporter_id == user.id)
    if status_filter:
        base = base.where(Report.status == status_filter)
        count_q = count_q.where(Report.status == status_filter)
    total = db.execute(count_q).scalar_one()
    rows = db.execute(base.order_by(Report.id.desc()).limit(min(limit, 200)).offset(offset)).scalars()
    return ReportListOut(total=int(total), items=[_report_out(r) for r in rows])


@router.get("/{report_id}", response_model=ReportOut)
def get_report(report_id: int, user: User = Depends(active_user), db: Session = Depends(get_db)):
    report = db.get(Report, report_id)
    if report is None or report.reporter_id != user.id:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    return _report_out(report, reveal_target=True)


@router.post("/{report_id}/submit", response_model=dict)
def submit_report(
    report_id: int,
    request: Request,
    adapter: str = Form("user_native"),
    device_id: int | None = Form(None),
    manual_ack: bool = Form(False),
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Transmet un signalement VÉRIFIÉ. Explique honnêtement le canal utilisé."""
    report = db.get(Report, report_id)
    if report is None or report.reporter_id != user.id:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    if report.status not in (ReportStatus.VERIFIED, ReportStatus.QUEUED):
        raise HTTPException(
            status_code=409,
            detail=(
                "Seul un signalement vérifié par un modérateur peut être transmis "
                f"(statut actuel : {STATUS_LABELS.get(report.status, report.status)})."
            ),
        )
    if not manual_ack:
        raise HTTPException(
            status_code=400,
            detail=(
                "Vous devez confirmer avoir lu l'avertissement : tout faux signalement est passible de "
                "poursuites judiciaires et du bannissement de votre compte WhatsApp. Ce service ne garantit "
                "pas le bannissement : seul Meta examine et décide."
            ),
        )

    device: LinkedDevice | None = None
    if adapter == SubmissionAdapter.USER_NATIVE:
        device = db.get(LinkedDevice, device_id) if device_id else None
        if device is None:
            device = db.execute(
                select(LinkedDevice)
                .where(LinkedDevice.user_id == user.id, LinkedDevice.mode == "web_linked")
                .order_by(LinkedDevice.id.desc())
            ).scalars().first()
        if device is None or device.status != "connected":
            raise HTTPException(
                status_code=409,
                detail=(
                    "Aucun appareil WhatsApp connecté : votre signalement ne peut pas être transmis depuis "
                    "votre compte. Liez votre WhatsApp (ou choisissez le parcours guidé) — nous n'inventons "
                    "jamais un envoi."
                ),
            )
        try:
            caps = get_connector().capabilities()
        except (ConnectorError, ConnectorUnavailable) as exc:
            db.rollback()
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        if not caps.report_native:
            return {
                "queued": False,
                "fallback": "manual_guided",
                "steps": _guided_steps(report),
                "detail": (
                    "La passerelle active n'expose pas l'action de signalement natif. Utilisez le parcours guidé : "
                    "les étapes ci-dessous reproduisent exactement le signalement manuel dans WhatsApp, "
                    "avec les messages concernés déjà transmis."
                ),
            }
    elif adapter == SubmissionAdapter.CLOUD_API:
        if not user.is_business:
            raise HTTPException(status_code=409, detail="Compte professionnel requis pour le canal Cloud API.")
    elif adapter != SubmissionAdapter.MANUAL_GUIDED:
        raise HTTPException(status_code=400, detail="Adaptateur de transmission inconnu.")

    submission = reports_svc.create_submission(db, report=report, actor=user, adapter=adapter, device=device)
    audit.log(db, action="submission.requested", actor_user_id=user.id, entity_type="report_submission",
              entity_id=submission.id, ip=client_ip(request),
              detail={"adapter": adapter, "report_ref": report.public_ref})
    db.commit()
    return {
        "queued": True,
        "submission_id": submission.id,
        "adapter": adapter,
        "detail": (
            "Transmission mise en file. Le worker l'exécute réellement et enregistre le résultat exact "
            "(succès, échec, ou parcours guidé). Aucun statut « envoyé » n'est affiché sans confirmation."
        ),
    }


def _guided_steps(report: Report) -> list[str]:
    return [
        "Ouvrez WhatsApp et la conversation avec le numéro signalé.",
        "Appuyez sur le nom du contact, puis faites défiler jusqu'à « Signaler ».",
        f"Choisissez la catégorie correspondante : {CATEGORY_LABELS_FR.get(report.category, report.category)}.",
        "Validez « Signaler » — WhatsApp transmet automatiquement les derniers messages du contact.",
        "Revenez dans SignalPro et appuyez sur « J'ai signalé » pour enregistrer l'action et sa date.",
    ]


@router.post("/{report_id}/mark-manual-done", response_model=dict)
def mark_manual_done(
    report_id: int,
    done: bool = Form(True),
    note: str = Form(""),
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Trace un signalement effectué manuellement par l'utilisateur (parcours guidé)."""
    report = db.get(Report, report_id)
    if report is None or report.reporter_id != user.id:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    sub = ReportSubmission(
        report_id=report.id,
        target_id=report.target_id,
        actor_user_id=user.id,
        adapter=SubmissionAdapter.MANUAL_GUIDED,
        status=SubmissionStatus.SUCCEEDED if done else SubmissionStatus.SKIPPED,
        attempts=1,
        response_summary=(
            "Signalement effectué manuellement depuis l'application WhatsApp de l'utilisateur "
            f"(déclaration de l'utilisateur){' : ' + note if note else ''}."
        ),
        finished_at=datetime.utcnow(),
    )
    db.add(sub)
    if done and report.status == ReportStatus.VERIFIED:
        report.status = ReportStatus.SUBMITTED
    db.flush()
    audit.log(db, action="submission.manual_declared", actor_user_id=user.id, entity_type="report_submission",
              entity_id=sub.id, detail={"report_ref": report.public_ref, "done": done})
    db.commit()
    return {"ok": True, "submission_id": sub.id, "adapter": "manual_guided"}


@router.get("/{report_id}/export", response_class=PlainTextResponse)
def export_report(report_id: int, user: User = Depends(active_user), db: Session = Depends(get_db)):
    report = db.get(Report, report_id)
    if report is None or report.reporter_id != user.id:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    lines = [
        f"Référence : {report.public_ref}",
        f"Numéro signalé : {reports_svc.mask_phone(decrypt_str(report.target.phone_enc) or '')}",
        f"Catégorie : {CATEGORY_LABELS_FR.get(report.category, report.category)}",
        f"Date de l'infraction : {report.occurred_at.isoformat()}",
        f"Description : {report.description}",
        f"Statut : {STATUS_LABELS.get(report.status, report.status)}",
        f"Preuve de contact : {report.contact_proof_method} ({report.contact_proof_detail})",
        "",
        "Preuves :",
    ]
    for ev in report.evidences:
        lines.append(f"  - [{ev.kind}] {ev.filename} · {ev.size_bytes} octets · SHA-256 {ev.sha256}")
    lines.append("")
    lines.append("Transmissions :")
    for s in report.submissions:
        lines.append(f"  - {s.adapter} · {s.status} · {s.finished_at or 'en cours'} · {s.response_summary or s.error or ''}")
    lines.append("")
    lines.append(
        "Avertissement : ce service ne garantit pas la suspension du numéro signalé. "
        "Seul Meta/WhatsApp examine les signalements et décide."
    )
    return "\n".join(lines)


# --- Import en masse --------------------------------------------------------
@router.get("/import/template", response_class=PlainTextResponse)
def import_template(user: User = Depends(active_user)):
    return PlainTextResponse(
        csv_import.template_csv(),
        headers={"Content-Disposition": "attachment; filename=modele_import_signalements.csv"},
    )


@router.post("/import/preview", response_model=CsvImportOut)
async def import_preview(
    file: UploadFile = File(...),
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    data = await file.read()
    if len(data) > 5 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Fichier trop volumineux (5 Mo maximum).")
    try:
        headers, rows = csv_import.read_rows(file.filename or "import.csv", data)
        validated = csv_import.validate_rows(headers, rows)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    valid = [r for r in validated if r.valid]
    audit.log(db, action="import.previewed", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              detail={"file": file.filename, "rows": len(validated), "valid": len(valid)})
    db.commit()
    return CsvImportOut(
        filename=file.filename or "import.csv",
        total_rows=len(validated),
        valid_rows=len(valid),
        rejected_rows=len(validated) - len(valid),
        committed=False,
        rows=[
            CsvPreviewRow(
                line=r.line,
                target_phone=r.target_phone,
                category=r.category,
                occurred_at=r.occurred_at.isoformat() if r.occurred_at else None,
                description=r.description,
                evidence_ref=r.evidence_ref or None,
                message_ids=r.message_ids,
                valid=r.valid,
                errors=r.errors,
            )
            for r in validated[:500]
        ],
        columns_detected=list(csv_import.detect_columns(headers).values()),
    )


@router.post("/import/commit", response_model=CsvImportOut)
async def import_commit(
    request: Request,
    file: UploadFile = File(...),
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    """Import réel : chaque ligne valide crée un signalement ; toute ligne sans preuve est refusée."""
    data = await file.read()
    if len(data) > 5 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Fichier trop volumineux (5 Mo maximum).")
    try:
        headers, rows = csv_import.read_rows(file.filename or "import.csv", data)
        validated = csv_import.validate_rows(headers, rows)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    valid = [r for r in validated if r.valid]
    if not valid:
        raise HTTPException(
            status_code=422,
            detail=(
                "Aucune ligne valide : chaque signalement doit contenir un numéro, une catégorie d'infraction, "
                "une date et une preuve. L'import est refusé dans son intégralité."
            ),
        )

    created: list[int] = []
    errors: list[str] = []
    for r in valid[:50]:  # plafond par import, les quotas journaliers s'appliquent en plus
        try:
            limits.consume_report_creation(db, user.id, count=1)
        except limits.RateLimitExceeded as exc:
            errors.append(f"Ligne {r.line} : {exc}")
            db.rollback()
            break
        try:
            report = reports_svc.create_report(
                db,
                user=user,
                target_phone=r.target_phone or "",
                category=r.category or "",
                occurred_at=r.occurred_at,  # type: ignore[arg-type]
                description=r.description,
                source=ReportSource.CSV_IMPORT,
                message_ids=r.message_ids,
            )
        except reports_svc.BusinessRuleError as exc:
            errors.append(f"Ligne {r.line} : {exc}")
            db.rollback()
            continue
        if r.message_ids:
            ev = Evidence(
                report_id=report.id,
                kind=EvidenceKind.MESSAGE_ID,
                filename="message_ids.json",
                mime="application/json",
                size_bytes=len(json.dumps(r.message_ids)),
                sha256="",
                storage_key="",
                encrypted=False,
                integrity_ok=True,
                validation_detail=json.dumps({"message_ids": r.message_ids, "source": "csv"}),
                message_ids=json.dumps(r.message_ids),
            )
            from ..services.evidence import validate_upload

            res = validate_upload(
                kind=EvidenceKind.MESSAGE_ID,
                filename="message_ids.json",
                data=json.dumps(r.message_ids).encode(),
                declared_mime="application/json",
                message_ids=r.message_ids,
            )
            if not res.ok:
                db.rollback()
                errors.append(f"Ligne {r.line} : identifiants de message invalides")
                continue
            ev.sha256 = res.sha256
            db.add(ev)
            db.flush()
            reports_svc.refresh_report_status(db, report)
        created.append(report.id)
        db.commit()

    audit.log(db, action="import.committed", actor_user_id=user.id, entity_type="user", entity_id=user.id,
              ip=client_ip(request),
              detail={"file": file.filename, "created": len(created), "errors": errors[:20]})
    db.commit()
    return CsvImportOut(
        filename=file.filename or "import.csv",
        total_rows=len(validated),
        valid_rows=len(valid),
        rejected_rows=len(validated) - len(valid),
        committed=True,
        created_report_ids=created,
        rows=[
            CsvPreviewRow(
                line=r.line,
                target_phone=r.target_phone,
                category=r.category,
                occurred_at=r.occurred_at.isoformat() if r.occurred_at else None,
                description=r.description,
                evidence_ref=r.evidence_ref or None,
                message_ids=r.message_ids,
                valid=r.valid,
                errors=r.errors + [e for e in errors if e.startswith(f"Ligne {r.line}")],
            )
            for r in validated[:500]
        ],
        columns_detected=list(csv_import.detect_columns(headers).values()),
    )


# --- Preuves : téléchargement vérifié --------------------------------------
@router.get("/evidence/{evidence_id}/download")
def download_evidence(
    evidence_id: int,
    user: User = Depends(active_user),
    db: Session = Depends(get_db),
):
    from ..constants import Role
    from ..services import evidence as ev_svc

    ev = db.get(Evidence, evidence_id)
    if ev is None:
        raise HTTPException(status_code=404, detail="Preuve introuvable.")
    report = db.get(Report, ev.report_id)
    if report is None:
        raise HTTPException(status_code=404, detail="Signalement introuvable.")
    if report.reporter_id != user.id and user.role not in (Role.MODERATOR, Role.ADMIN):
        raise HTTPException(status_code=403, detail="Accès refusé à cette preuve.")
    if not ev.storage_key:
        raise HTTPException(status_code=409, detail="Cette preuve ne contient pas de fichier (identifiants seuls).")
    try:
        raw = ev_svc.load_evidence_bytes(ev.storage_key)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"Lecture de la preuve impossible : {exc}") from exc
    if ev_svc.compute_hash(raw) and len(raw) and __import__("hashlib").sha256(raw).hexdigest() != ev.sha256:
        audit.log(db, action="evidence.integrity_failed", actor_user_id=user.id, entity_type="evidence",
                  entity_id=ev.id)
        db.commit()
        raise HTTPException(status_code=500, detail="L'empreinte de la preuve ne correspond plus : accès bloqué.")
    audit.log(db, action="evidence.downloaded", actor_user_id=user.id, entity_type="evidence", entity_id=ev.id,
              detail={"report_ref": report.public_ref})
    db.commit()
    return Response(
        content=raw,
        media_type=ev.mime or "application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{ev.filename or "preuve"}"'},
    )
