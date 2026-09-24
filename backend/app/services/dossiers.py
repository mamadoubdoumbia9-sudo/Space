"""Génération de dossiers PDF horodatés (preuves + chaîne de traçabilité).

Un dossier n'est produit que pour une cible ayant réuni le nombre de signalements
vérifiés exigé. Il contient les éléments nécessaires à un examen par un tiers :
identité du numéro visé, catégories, dates, empreintes SHA-256 des preuves,
copies des captures et journal d'audit correspondant.
"""
from __future__ import annotations

import io
import json
import logging
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..constants import CATEGORY_LABELS_FR, ReportStatus
from ..models import AuditLog, Evidence, Report, Target
from ..security import decrypt_str, sha256_hex
from . import evidence as evidence_svc
from .reports import eligible_reports_for_escalation, mask_phone

log = logging.getLogger("signalpro.dossiers")

DISCLAIMER = (
    "DOSSIER DE SIGNALEMENT — la décision de suspendre ou non un compte appartient exclusivement "
    "à Meta/WhatsApp après examen. Ce dossier ne constitue ni une demande de bannissement automatique "
    "ni une garantie de résultat. Les données personnelles qu'il contient sont traitées uniquement dans "
    "le but de faire traiter ce signalement."
)


def build_dossier_pdf(db: Session, target: Target) -> tuple[bytes, dict]:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import mm
    from reportlab.lib.utils import ImageReader
    from reportlab.platypus import Image, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

    reports = eligible_reports_for_escalation(db, target)
    phone_full = decrypt_str(target.phone_enc) or ""
    styles = getSampleStyleSheet()
    h1 = ParagraphStyle("h1x", parent=styles["Heading1"], fontSize=15, spaceAfter=6)
    h2 = ParagraphStyle("h2x", parent=styles["Heading2"], fontSize=11, spaceAfter=4)
    body = ParagraphStyle("bodyx", parent=styles["BodyText"], fontSize=9, leading=12)
    small = ParagraphStyle("smallx", parent=styles["BodyText"], fontSize=7.5, leading=10, textColor="#444444")

    buf = io.BytesIO()
    doc = SimpleDocTemplate(
        buf, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm, topMargin=16 * mm, bottomMargin=16 * mm,
        title=f"Dossier SignalPro {target.id}", author="SignalPro Collectif",
    )
    story: list = []
    story.append(Paragraph("Dossier de signalement — WhatsApp", h1))
    story.append(Paragraph(DISCLAIMER, small))
    story.append(Spacer(1, 6 * mm))

    meta = [
        ["Référence dossier", f"TGT-{target.id:06d}"],
        ["Numéro visé (format international)", phone_full],
        ["Affichage public", mask_phone(phone_full)],
        ["Statut interne", target.status],
        ["Signalements vérifiés (plaignants distincts)", f"{target.verified_reports} ({target.distinct_reporters})"],
        ["Signalements jugés abusifs", str(target.abusive_reports)],
        ["Score de risque", f"{target.risk_score}/100"],
        ["Statut suspension (source)", f"{target.suspension_status} / {target.suspension_source}"],
        ["Généré le", datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")],
    ]
    t = Table(meta, colWidths=[70 * mm, 90 * mm])
    t.setStyle(TableStyle([
        ("GRID", (0, 0), (-1, -1), 0.4, "#999999"),
        ("BACKGROUND", (0, 0), (0, -1), "#F2F2F2"),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ]))
    story.append(t)
    story.append(Spacer(1, 6 * mm))
    story.append(Paragraph(f"Détail des signalements vérifiés ({len(reports)})", h2))

    for idx, rep in enumerate(reports, start=1):
        reporter_ref = sha256_hex(f"reporter:{rep.reporter_id}:{target.id}".encode())[:16]
        story.append(Paragraph(
            f"<b>{idx}. {rep.public_ref}</b> — {CATEGORY_LABELS_FR.get(rep.category, rep.category)}", body))
        story.append(Paragraph(
            f"Date de l'infraction : {rep.occurred_at.strftime('%Y-%m-%d %H:%M UTC')} · "
            f"Plaignant (pseudonymisé) : #{reporter_ref} · "
            f"Preuve de contact : {rep.contact_proof_method or 'non vérifiée'} · "
            f"Déposé le : {rep.created_at.strftime('%Y-%m-%d %H:%M UTC')}", small))
        story.append(Paragraph((rep.description or "")[:1200], body))
        for ev in rep.evidences:
            story.append(Paragraph(
                f"Preuve : {ev.kind} · {ev.filename or 'sans nom'} · {ev.size_bytes} octets · "
                f"SHA-256 {ev.sha256[:32]}… · intégrité {'OK' if ev.integrity_ok else 'NON VÉRIFIÉE'}", small))
        story.append(Spacer(1, 3 * mm))

    story.append(PageBreak())
    story.append(Paragraph("Annexes — copies des preuves", h2))
    for rep in reports:
        for ev in rep.evidences:
            if ev.kind != "screenshot":
                continue
            try:
                raw = evidence_svc.load_evidence_bytes(ev.storage_key)
                img = ImageReader(io.BytesIO(raw))
                iw, ih = img.getSize()
                max_w, max_h = 150 * mm, 180 * mm
                ratio = min(max_w / iw, max_h / ih, 1.0)
                story.append(Paragraph(f"{rep.public_ref} · SHA-256 {ev.sha256[:24]}…", small))
                story.append(Image(io.BytesIO(raw), width=iw * ratio, height=ih * ratio))
                story.append(Spacer(1, 4 * mm))
            except Exception as exc:  # noqa: BLE001
                story.append(Paragraph(f"[preuve {ev.id} illisible : {exc}]", small))

    story.append(PageBreak())
    story.append(Paragraph("Journal d'audit (extrait — chaîne de traçabilité)", h2))
    logs = list(
        db.execute(
            select(AuditLog)
            .where(AuditLog.entity_type == "target", AuditLog.entity_id == target.id)
            .order_by(AuditLog.id)
            .limit(200)
        ).scalars()
    )
    for entry in logs:
        story.append(Paragraph(
            f"{entry.created_at.strftime('%Y-%m-%d %H:%M:%S')} · {entry.action} · acteur={entry.actor_user_id} "
            f"· {(entry.detail or '')[:200]}", small))
    if not logs:
        story.append(Paragraph("Aucun événement d'audit enregistré pour cette cible.", small))
    story.append(Spacer(1, 4 * mm))
    story.append(Paragraph(
        "Le dossier complet (pièces d'origine chiffrées) peut être fourni sur réquisition d'une autorité "
        "judiciaire compétente, conformément à notre obligation de coopération.", small))

    doc.build(story)
    pdf = buf.getvalue()
    info = {
        "reports": len(reports),
        "target_id": target.id,
        "sha256": sha256_hex(pdf),
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "evidence_count": sum(len(r.evidences) for r in reports),
    }
    return pdf, info


def dossier_json_summary(db: Session, target: Target) -> str:
    reports = eligible_reports_for_escalation(db, target)
    payload = {
        "target": {
            "ref": f"TGT-{target.id:06d}",
            "phone": decrypt_str(target.phone_enc),
            "status": target.status,
            "verified_reports": target.verified_reports,
            "distinct_reporters": target.distinct_reporters,
            "risk_score": target.risk_score,
        },
        "reports": [
            {
                "ref": r.public_ref,
                "category": r.category,
                "occurred_at": r.occurred_at.isoformat(),
                "description": r.description,
                "evidence": [
                    {"kind": e.kind, "sha256": e.sha256, "size": e.size_bytes, "integrity": e.integrity_ok}
                    for e in r.evidences
                ],
                "contact_proof": r.contact_proof_method,
            }
            for r in reports
        ],
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "disclaimer": DISCLAIMER,
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)
