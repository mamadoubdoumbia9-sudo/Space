"""Pipeline complet des signalements.

Étapes réelles, toutes traçables :

  1. Création         -> vérifications anti-abus (quotas, preuve de contact)
  2. Preuves          -> validation binaire réelle (magic bytes, décodage, analyse)
                         + détection de réutilisation (empreinte perceptuelle)
  3. File de relecture-> `pending_verification` : AUCUN envoi automatique à ce stade
  4. Décision humaine -> `verified` (preuve suffisante) ou `rejected*`
  5. Regroupement     -> un même numéro doit réunir >= MIN_VERIFICATIONS_TO_ESCALATE
                         signalements vérifiés d'utilisateurs DISTINCTS pour être escaladé
  6. Escalade         -> transmission réelle (natif depuis le compte utilisateur,
                         ou dossier horodaté vers le canal officiel Meta) ;
                         l'issue (suspension) ne peut venir que de Meta.
"""
from __future__ import annotations

import json
import logging
import secrets
from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..config import get_settings
from ..constants import (
    CATEGORY_LABELS_FR,
    CampaignStatus,
    ContactProofMethod,
    EvidenceKind,
    InfractionCategory,
    ReportSource,
    ReportStatus,
    SubmissionAdapter,
    SubmissionStatus,
    SuspensionSource,
    TargetStatus,
)
from ..models import (
    Campaign,
    ContactIndex,
    Evidence,
    LinkedDevice,
    Report,
    ReportSubmission,
    Target,
    User,
)
from ..security import decrypt_str, encrypt_str, fingerprint
from . import audit, evidence as evidence_svc, notify
from .storage import get_storage

log = logging.getLogger("signalpro.reports")


class BusinessRuleError(Exception):
    """Violation d'une règle métier (anti-abus, conformité) : 4xx côté API."""


# ---------------------------------------------------------------------------
# Cibles
# ---------------------------------------------------------------------------
def mask_phone(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) <= 6:
        return digits[:2] + "•" * max(len(digits) - 2, 0)
    return f"+{digits[:3]} {'•' * 3} {'•' * 3} {digits[-2:]}"


def get_target(db: Session, phone: str, create: bool = True) -> Target | None:
    fp = fingerprint(phone)
    target = db.execute(select(Target).where(Target.phone_fp == fp)).scalar_one_or_none()
    if target is None and create:
        digits = "".join(c for c in phone if c.isdigit())
        target = Target(
            phone_enc=encrypt_str("+" + digits) or "",
            phone_fp=fp,
            country_code=digits[:3],
            status=TargetStatus.UNKNOWN,
        )
        db.add(target)
        db.flush()
    return target


def target_display(target: Target, reveal: bool = False) -> str:
    phone = decrypt_str(target.phone_enc) or ""
    return phone if reveal else mask_phone(phone)


# ---------------------------------------------------------------------------
# Preuve de contact (anti-abus : « interdiction de signaler des numéros qui ne
# vous ont pas contacté »)
# ---------------------------------------------------------------------------
def contact_index_available(db: Session, user_id: int) -> bool:
    return (
        db.execute(select(func.count(ContactIndex.id)).where(ContactIndex.user_id == user_id)).scalar_one() > 0
    )


def has_contact(db: Session, user_id: int, phone: str) -> bool:
    """Un même numéro peut apparaître sur plusieurs appareils liés : `.first()` est requis."""
    fp = fingerprint(phone)
    row = db.execute(
        select(ContactIndex).where(ContactIndex.user_id == user_id, ContactIndex.peer_fp == fp).limit(1)
    ).scalars().first()
    return row is not None


def peer_jid(phone: str) -> str:
    return "".join(c for c in phone if c.isdigit()) + "@s.whatsapp.net"


def check_contact_proof(db: Session, user: User, phone: str, declared_method: str) -> tuple[str, str]:
    """Retourne (méthode_effective, détail). Les modérateurs en sont dispensés."""
    from ..constants import Role

    if user.role in (Role.MODERATOR, Role.ADMIN):
        return ContactProofMethod.MODERATOR_RECORD, "Compte modérateur : exemption de preuve de contact."
    if not contact_index_available(db, user.id):
        raise BusinessRuleError(
            "Aucune conversation vérifiée sur votre compte. Liez d'abord votre WhatsApp : "
            "la preuve de contact est obligatoire pour éviter les faux signalements."
        )
    if has_contact(db, user.id, phone):
        return ContactProofMethod.LINKED_DEVICE_SCAN, "Numéro trouvé dans vos conversations vérifiées."
    raise BusinessRuleError(
        "Ce numéro n'apparaît pas dans vos conversations. Vous ne pouvez signaler que des numéros "
        "qui vous ont réellement contacté (règle anti-abus, non désactivable)."
    )


# ---------------------------------------------------------------------------
# Création de signalement
# ---------------------------------------------------------------------------
def create_report(
    db: Session,
    *,
    user: User,
    target_phone: str,
    category: str,
    occurred_at: datetime,
    description: str,
    source: str = ReportSource.DIRECT,
    message_ids: list[str] | None = None,
    declared_contact_method: str = ContactProofMethod.LINKED_DEVICE_SCAN,
    require_contact_proof: bool = True,
    auto_flags: list[str] | None = None,
) -> Report:
    if category not in {c.value for c in InfractionCategory}:
        raise BusinessRuleError(f"Catégorie d'infraction invalide : {category}")

    target = get_target(db, target_phone)
    assert target is not None

    proof_method, proof_detail = (None, None)
    if require_contact_proof:
        proof_method, proof_detail = check_contact_proof(db, user, target_phone, declared_contact_method)

    existing = db.execute(
        select(Report).where(
            Report.reporter_id == user.id, Report.target_id == target.id, Report.occurred_at == occurred_at
        )
    ).scalar_one_or_none()
    if existing is not None:
        raise BusinessRuleError(
            f"Un signalement identique existe déjà (réf. {existing.public_ref}) : même numéro, même date d'infraction."
        )

    report = Report(
        public_ref="RPT-" + secrets.token_hex(4).upper(),
        reporter_id=user.id,
        target_id=target.id,
        category=category,
        occurred_at=occurred_at,
        description=description.strip(),
        source=source,
        status=ReportStatus.PENDING_EVIDENCE,
        contact_proof_method=proof_method,
        contact_proof_at=datetime.now(timezone.utc) if proof_method else None,
        contact_proof_detail=proof_detail,
        auto_flags=json.dumps(auto_flags or []),
    )
    db.add(report)
    db.flush()

    target.total_reports += 1
    if target.total_reports == 1:
        target.main_category = category
    if not target.phone_enc:
        target.phone_enc = encrypt_str(target_phone) or ""

    audit.log(
        db,
        action="report.created",
        actor_user_id=user.id,
        actor_role=user.role,
        entity_type="report",
        entity_id=report.id,
        detail={
            "target_ref": target.id,
            "category": category,
            "occurred_at": occurred_at.isoformat(),
            "source": source,
            "contact_proof": proof_method,
            "message_ids": len(message_ids or []),
        },
    )
    return report


def attach_evidence(
    db: Session,
    *,
    report: Report,
    user: User,
    kind: str,
    filename: str,
    data: bytes,
    declared_mime: str | None = None,
    message_ids: list[str] | None = None,
) -> Evidence:
    """Valide réellement puis stocke (chiffré) une preuve, et recalcule le statut."""
    s = get_settings()
    if len(data) > s.max_evidence_bytes:
        raise BusinessRuleError(
            f"Preuve trop volumineuse ({len(data) // 1024} Ko) : limite {s.max_evidence_bytes // 1024 // 1024} Mo."
        )

    target_phone = decrypt_str(report.target.phone_enc) or ""
    target_digits = "".join(c for c in target_phone if c.isdigit())

    result = evidence_svc.validate_upload(
        kind=kind,
        filename=filename,
        data=data,
        declared_mime=declared_mime,
        message_ids=message_ids,
        target_phone_digits=target_digits,
    )
    if not result.ok:
        audit.log(
            db,
            action="evidence.rejected",
            actor_user_id=user.id,
            entity_type="report",
            entity_id=report.id,
            detail={"kind": kind, "errors": result.errors, "filename": filename},
        )
        db.flush()
        raise BusinessRuleError("Preuve refusée : " + " ".join(result.errors))

    duplicate = evidence_svc.find_duplicate(db, result, exclude_report_id=report.id)
    if duplicate is not None:
        # Une preuve identique déposée par un autre compte est un signal fort de
        # faux signalement concerté : on accepte la trace mais on bloque l'envoi.
        flags = json.loads(report.auto_flags or "[]")
        flags.append(f"preuve_identique_rapport_{duplicate.report_id}")
        report.auto_flags = json.dumps(flags)
        report.status = ReportStatus.REJECTED_ABUSIVE
        report.decision_reason = "Preuve déjà utilisée dans un autre signalement (fichier identique au bit près)."
        db.flush()
        # Le marquage « abusif » doit survivre au refus : on valide avant de lever l'erreur,
        # sinon un appelant qui annule la transaction effacerait la trace de la fraude.
        db.commit()
        raise BusinessRuleError(
            "Cette preuve a déjà été déposée dans un autre signalement (fichier identique). "
            "Un faux signalement entraîne le bannissement du compte."
        )

    near_dup: int | None = None
    if result.perceptual_hash:
        near_dup = evidence_svc.find_near_duplicate_image(db, result.perceptual_hash, exclude_report_id=report.id)

    storage_key = evidence_svc.store_evidence_bytes(user.id, report.public_ref, filename, data)
    ev = Evidence(
        report_id=report.id,
        kind=kind,
        filename=filename[:255],
        mime=result.mime,
        size_bytes=result.size,
        sha256=result.sha256,
        storage_key=storage_key,
        encrypted=True,
        integrity_ok=True,
        validation_detail=result.detail_json(),
        message_ids=json.dumps(message_ids or result.detail.get("message_ids", [])),
        captured_at=report.occurred_at,
    )
    db.add(ev)
    db.flush()

    if near_dup:
        flags = json.loads(report.auto_flags or "[]")
        flags.append(f"capture_similaire_rapport_{near_dup}")
        report.auto_flags = json.dumps(flags)

    audit.log(
        db,
        action="evidence.accepted",
        actor_user_id=user.id,
        entity_type="report",
        entity_id=report.id,
        detail={
            "kind": kind,
            "sha256": result.sha256,
            "size": result.size,
            "near_duplicate_of": near_dup,
            "detail": result.detail,
        },
    )
    refresh_report_status(db, report)
    return ev


def refresh_report_status(db: Session, report: Report) -> None:
    """Un rapport passe en relecture seulement s'il porte au moins une preuve valide."""
    if report.status in (
        ReportStatus.REJECTED,
        ReportStatus.REJECTED_ABUSIVE,
        ReportStatus.REJECTED_NO_EVIDENCE,
        ReportStatus.CLOSED,
    ):
        return
    valid = [e for e in report.evidences if e.integrity_ok]
    if not valid:
        report.status = ReportStatus.PENDING_EVIDENCE
        return
    if report.status in (ReportStatus.PENDING_EVIDENCE, ReportStatus.DRAFT):
        report.status = ReportStatus.PENDING_VERIFICATION
    db.flush()


def store_message_snapshot(db: Session, *, report: Report, user: User, excerpt: str, consent: bool, sent_at=None) -> bool:
    """Ne stocke le texte d'un message QUE si l'utilisateur a consenti explicitement."""
    if not consent or not excerpt.strip():
        return False
    from ..models import MessageSnapshot

    snap = MessageSnapshot(
        report_id=report.id,
        user_id=user.id,
        consent_granted=True,
        body_enc=encrypt_str(excerpt[:20000]) or "",
        sent_at=sent_at or report.occurred_at,
        direction="inbound",
        expires_at=datetime.now(timezone.utc) + timedelta(days=get_settings().retention_days_evidence),
    )
    db.add(snap)
    audit.log(
        db,
        action="message_snapshot.stored",
        actor_user_id=user.id,
        entity_type="report",
        entity_id=report.id,
        detail={"chars": len(excerpt), "consent": True},
    )
    db.flush()
    return True


# ---------------------------------------------------------------------------
# Vérification humaine et compteurs de cible
# ---------------------------------------------------------------------------
def recompute_target(db: Session, target: Target) -> None:
    s = get_settings()
    verified = db.execute(
        select(func.count(Report.id)).where(
            Report.target_id == target.id, Report.status.in_([ReportStatus.VERIFIED, ReportStatus.QUEUED, ReportStatus.SUBMITTED])
        )
    ).scalar_one()
    distinct = db.execute(
        select(func.count(func.distinct(Report.reporter_id))).where(
            Report.target_id == target.id, Report.status.in_([ReportStatus.VERIFIED, ReportStatus.QUEUED, ReportStatus.SUBMITTED])
        )
    ).scalar_one()
    abusive = db.execute(
        select(func.count(Report.id)).where(Report.target_id == target.id, Report.status == ReportStatus.REJECTED_ABUSIVE)
    ).scalar_one()
    total = db.execute(select(func.count(Report.id)).where(Report.target_id == target.id)).scalar_one()

    target.verified_reports = int(verified)
    target.distinct_reporters = int(distinct)
    target.abusive_reports = int(abusive)
    target.total_reports = int(total)
    # Score de risque : volume vérifié pondéré par le nombre de plaignants distincts.
    target.risk_score = round(min(100.0, verified * 8.0 + distinct * 6.0 - abusive * 10.0), 2)

    if target.status in (TargetStatus.CLEARED, TargetStatus.SUSPENSION_CONFIRMED):
        db.flush()
        return
    if target.suspension_status == "unknown":
        target.suspension_status = "not_confirmed"
    if verified >= s.min_verifications_to_publish and distinct >= s.min_verifications_to_publish:
        if target.status != TargetStatus.CONFIRMED_MALICIOUS:
            target.status = TargetStatus.CONFIRMED_MALICIOUS
            target.published_at = target.published_at or datetime.now(timezone.utc)
            audit.log(
                db,
                action="target.published",
                entity_type="target",
                entity_id=target.id,
                detail={"verified_reports": verified, "distinct_reporters": distinct},
            )
            _notify_reporters(db, target, "target_published")
    elif target.status == TargetStatus.UNKNOWN and verified:
        target.status = TargetStatus.UNDER_REVIEW
    db.flush()


def _notify_reporters(db: Session, target: Target, kind: str) -> None:
    rows = db.execute(select(Report.reporter_id).where(Report.target_id == target.id).distinct()).scalars().all()
    for uid in rows:
        notify.push(
            db,
            uid,
            kind,
            "Un numéro que vous avez signalé est confirmé malveillant",
            "Il est désormais visible dans la liste communautaire ; vous pouvez le bloquer en un clic.",
            {"target_id": target.id},
        )


def moderator_decide(
    db: Session,
    *,
    moderator: User,
    report: Report,
    decision: str,
    reason: str,
    ip: str | None = None,
) -> Report:
    """Décisions possibles : verify | reject | reject_abusive."""
    now = datetime.now(timezone.utc)
    if report.status in (ReportStatus.SUBMITTED,) and decision in ("verify", "reject", "reject_abusive"):
        raise BusinessRuleError("Ce signalement a déjà été transmis : il n'est plus modifiable.")

    report.moderator_id = moderator.id
    report.decided_at = now
    report.decision_reason = reason

    if decision == "verify":
        report.status = ReportStatus.VERIFIED
    elif decision == "reject":
        report.status = ReportStatus.REJECTED
    elif decision == "reject_abusive":
        report.status = ReportStatus.REJECTED_ABUSIVE
        report.strike_issued = True
        issue_strike(db, user_id=report.reporter_id, report_id=report.id, reason=reason, issued_by=moderator.id)
    else:
        raise BusinessRuleError(f"Décision inconnue : {decision}")

    db.flush()
    recompute_target(db, report.target)
    audit.log(
        db,
        action=f"report.{decision}",
        actor_user_id=moderator.id,
        actor_role=moderator.role,
        entity_type="report",
        entity_id=report.id,
        ip=ip,
        detail={"reason": reason, "target_id": report.target_id},
    )
    notify.push(
        db,
        report.reporter_id,
        f"report_{decision}",
        "Mise à jour de votre signalement",
        f"Réf. {report.public_ref} : {reason}",
        {"report_id": report.id},
    )
    return report


def issue_strike(db: Session, *, user_id: int, report_id: int | None, reason: str, issued_by: int | None, automatic: bool = False) -> User:
    from ..constants import UserStatus
    from ..models import Strike

    s = get_settings()
    user = db.get(User, user_id)
    if user is None:
        raise BusinessRuleError("Utilisateur introuvable.")
    db.add(Strike(user_id=user_id, report_id=report_id, reason=reason, issued_by=issued_by, automatic=automatic))
    user.strikes += 1
    if user.strikes >= s.max_abusive_strikes and user.status != UserStatus.BANNED:
        user.status = UserStatus.BANNED
        user.banned_at = datetime.now(timezone.utc)
        user.banned_reason = (
            f"Bannissement définitif : {user.strikes} signalements jugés abusifs après vérification "
            f"(dernier motif : {reason})."
        )
        audit.log(
            db,
            action="user.banned",
            actor_user_id=issued_by,
            entity_type="user",
            entity_id=user_id,
            detail={"strikes": user.strikes, "reason": reason, "automatic": automatic},
        )
    else:
        audit.log(
            db,
            action="user.strike_issued",
            actor_user_id=issued_by,
            entity_type="user",
            entity_id=user_id,
            detail={"strikes": user.strikes, "reason": reason, "automatic": automatic},
        )
    db.flush()
    return user


# ---------------------------------------------------------------------------
# Regroupement et escalade
# ---------------------------------------------------------------------------
def eligible_reports_for_escalation(db: Session, target: Target) -> list[Report]:
    """Signalements vérifiés, un seul par utilisateur (indépendance des plaignants)."""
    rows = list(
        db.execute(
            select(Report).where(
                Report.target_id == target.id,
                Report.status.in_([ReportStatus.VERIFIED, ReportStatus.QUEUED]),
                Report.strike_issued.is_(False),
            )
        ).scalars()
    )
    seen: set[int] = set()
    unique: list[Report] = []
    for r in sorted(rows, key=lambda x: x.id):
        if r.reporter_id in seen:
            continue
        seen.add(r.reporter_id)
        unique.append(r)
    return unique


def should_escalate(db: Session, target: Target) -> bool:
    s = get_settings()
    return len(eligible_reports_for_escalation(db, target)) >= s.min_verifications_to_escalate


async def execute_submission(db: Session, submission: ReportSubmission) -> ReportSubmission:
    """Exécute RÉELLEMENT la transmission et enregistre le résultat exact."""
    from .connector import ConnectorError, ConnectorUnavailable, get_connector

    submission.status = SubmissionStatus.RUNNING
    submission.attempts += 1
    submission.started_at = datetime.now(timezone.utc)
    db.flush()

    report = submission.report
    target = report.target
    phone = decrypt_str(target.phone_enc) or ""
    reporter = db.get(User, report.reporter_id)
    device = db.get(LinkedDevice, submission.actor_device_id) if submission.actor_device_id else None

    try:
        if submission.adapter == SubmissionAdapter.USER_NATIVE:
            if device is None or not device.session_ref:
                raise ConnectorUnavailable("Aucun appareil lié : liez votre WhatsApp avant toute transmission.")
            connector = get_connector()
            caps = connector.capabilities()
            if not caps.report_native:
                # Honnêteté : la passerelle ne sait pas signaler -> parcours guidé.
                submission.adapter = SubmissionAdapter.MANUAL_GUIDED
                submission.status = SubmissionStatus.SKIPPED
                submission.response_summary = (
                    "Signalement natif non pris en charge par la passerelle active. "
                    "L'utilisateur a été redirigé vers le parcours de signalement guidé dans WhatsApp."
                )
                submission.finished_at = datetime.now(timezone.utc)
                db.flush()
                return submission

            message_ids = _collect_message_ids(report)
            result = connector.native_report(
                device.session_ref, peer_jid(phone), report.category, message_ids, report.description[:1000]
            )
            performed = bool(result.get("performed"))
            submission.external_ref = str(result.get("reference") or result.get("id") or "")[:120] or None
            submission.response_summary = json.dumps(
                {k: v for k, v in result.items() if k not in ("token", "secret")}, ensure_ascii=False
            )[:2000]
            submission.status = SubmissionStatus.SUCCEEDED if performed else SubmissionStatus.FAILED
            if not performed:
                submission.error = result.get("detail") or "La passerelle n'a pas confirmé l'action de signalement."
            else:
                _mark_report_submitted(db, report)

        elif submission.adapter == SubmissionAdapter.CLOUD_API:
            from .cloud_api import get_cloud_client

            client = get_cloud_client()
            info = client.verify_configuration()
            submission.response_summary = json.dumps(
                {
                    "compte_pro": info.get("display_phone_number"),
                    "nom_verifie": info.get("verified_name"),
                    "qualite": info.get("quality_rating"),
                    "note": (
                        "WhatsApp Business Platform ne fournit pas d'endpoint de signalement tiers. "
                        "Ce signalement a été intégré au dossier groupé transmis au canal officiel Meta."
                    ),
                },
                ensure_ascii=False,
            )[:2000]
            submission.external_ref = str(info.get("display_phone_number") or "")[:120]
            submission.status = SubmissionStatus.SUCCEEDED
            _mark_report_submitted(db, report)

        else:
            submission.status = SubmissionStatus.SKIPPED
            submission.response_summary = "Transmission manuelle : aucune action automatique revendiquée."
            submission.finished_at = datetime.now(timezone.utc)

    except (ConnectorError, ConnectorUnavailable) as exc:
        submission.status = SubmissionStatus.FAILED
        submission.error = str(exc)[:2000]
        if submission.attempts >= 3:
            submission.status = SubmissionStatus.FAILED
        else:
            submission.status = SubmissionStatus.QUEUED  # réessai par le worker
    except Exception as exc:  # noqa: BLE001
        log.exception("Échec inattendu de la transmission")
        submission.status = SubmissionStatus.FAILED
        submission.error = f"{type(exc).__name__}: {exc}"[:2000]

    submission.finished_at = datetime.now(timezone.utc) if submission.status != SubmissionStatus.QUEUED else None
    db.flush()
    if reporter:
        notify.push(
            db,
            reporter.id,
            "submission_update",
            f"Signalement {report.public_ref} : {submission.status}",
            submission.response_summary or submission.error or "",
            {"report_id": report.id, "submission_id": submission.id},
        )
    audit.log(
        db,
        action="submission.executed",
        actor_user_id=submission.actor_user_id,
        entity_type="report_submission",
        entity_id=submission.id,
        detail={
            "adapter": submission.adapter,
            "status": submission.status,
            "http_status": submission.http_status,
            "attempt": submission.attempts,
            "error": submission.error,
            "campaign_id": submission.campaign_id,
        },
    )
    if report.status in (ReportStatus.VERIFIED, ReportStatus.QUEUED):
        recompute_target(db, report.target)
    return submission


def _collect_message_ids(report: Report) -> list[str]:
    ids: list[str] = []
    for ev in report.evidences:
        if ev.message_ids:
            try:
                ids.extend(json.loads(ev.message_ids))
            except json.JSONDecodeError:
                continue
    return list(dict.fromkeys(ids))[:6]


def _mark_report_submitted(db: Session, report: Report) -> None:
    report.status = ReportStatus.SUBMITTED
    db.flush()


def create_submission(
    db: Session,
    *,
    report: Report,
    actor: User,
    adapter: str,
    device: LinkedDevice | None = None,
    campaign: Campaign | None = None,
) -> ReportSubmission:
    sub = ReportSubmission(
        report_id=report.id,
        target_id=report.target_id,
        campaign_id=campaign.id if campaign else None,
        actor_user_id=actor.id,
        actor_device_id=device.id if device else None,
        adapter=adapter,
        status=SubmissionStatus.QUEUED,
        request_hash=None,
    )
    if report.status in (ReportStatus.VERIFIED, ReportStatus.PENDING_VERIFICATION):
        report.status = ReportStatus.QUEUED
    db.add(sub)
    db.flush()
    audit.log(
        db,
        action="submission.queued",
        actor_user_id=actor.id,
        entity_type="report_submission",
        entity_id=sub.id,
        detail={"adapter": adapter, "report_id": report.id, "campaign_id": sub.campaign_id},
    )
    return sub


# ---------------------------------------------------------------------------
# Campagnes (« signaler N fois pour ce numéro »)
# ---------------------------------------------------------------------------
def eligible_accounts_for_target(db: Session, target_phone: str) -> list[tuple[User, LinkedDevice]]:
    """Comptes liés distincts ayant réellement été contactés par la cible.

    C'est la SEULE source d'expansion possible d'un signalement : un compte ne
    peut participer que s'il a réellement reçu des messages de ce numéro et que
    son propriétaire consent. Aucune création de compte fictif n'est possible.
    """
    fp = fingerprint(target_phone)
    rows = db.execute(
        select(ContactIndex, LinkedDevice, User)
        .join(LinkedDevice, ContactIndex.device_id == LinkedDevice.id)
        .join(User, LinkedDevice.user_id == User.id)
        .where(ContactIndex.peer_fp == fp)
    ).all()
    from ..constants import DeviceStatus, UserStatus

    out: list[tuple[User, LinkedDevice]] = []
    seen_users: set[int] = set()
    for _ci, device, user in rows:
        if user.id in seen_users:
            continue
        if user.status != UserStatus.ACTIVE:
            continue
        if user.strikes >= get_settings().max_abusive_strikes:
            continue
        if device.status != DeviceStatus.CONNECTED or not device.session_ref:
            continue
        seen_users.add(user.id)
        out.append((user, device))
    return out


def create_campaign(
    db: Session,
    *,
    user: User,
    target_phone: str,
    requested_count: int,
    category: str,
    occurred_at: datetime,
    description: str,
    consent_ack: bool,
) -> Campaign:
    s = get_settings()
    if not consent_ack:
        raise BusinessRuleError(
            "Vous devez confirmer avoir lu l'avertissement : tout faux signalement est passible de "
            "poursuites et du bannissement de votre compte WhatsApp."
        )
    if requested_count > s.campaign_hard_cap:
        raise BusinessRuleError(f"Plafond dur de campagne : {s.campaign_hard_cap} signalements.")

    target = get_target(db, target_phone)
    assert target is not None
    if require_contact_proof_for(db, user):
        check_contact_proof(db, user, target_phone, ContactProofMethod.LINKED_DEVICE_SCAN)

    eligible = eligible_accounts_for_target(db, target_phone)
    eligible_count = len(eligible)
    if eligible_count == 0:
        raise BusinessRuleError(
            "Aucun compte éligible : pour ce numéro, aucun compte lié n'a réellement été contacté. "
            "Le système ne fabrique jamais de signalements depuis des comptes qui n'ont reçu aucun message."
        )

    campaign = Campaign(
        public_ref="CMP-" + secrets.token_hex(4).upper(),
        created_by=user.id,
        target_id=target.id,
        requested_count=requested_count,
        eligible_count=eligible_count,
        status=CampaignStatus.QUEUED if eligible_count else CampaignStatus.PARTIAL,
        consent_ack=True,
        notes=(
            f"Signalements indépendants possibles : {eligible_count} (comptes réellement contactés et consentants). "
            f"Demandé : {requested_count}."
        ),
    )
    db.add(campaign)
    db.flush()

    # Chaque compte éligible exécute SON signalement, à partir de SA conversation.
    created = 0
    for acc_user, device in eligible[: min(requested_count, s.campaign_hard_cap)]:
        try:
            report = create_report(
                db,
                user=acc_user,
                target_phone=target_phone,
                category=category,
                occurred_at=occurred_at,
                description=description,
                source=ReportSource.CAMPAIGN,
                declared_contact_method=ContactProofMethod.LINKED_DEVICE_SCAN,
            )
        except BusinessRuleError as exc:
            log.info("Campagne %s : compte %s inéligible (%s)", campaign.public_ref, acc_user.id, exc)
            continue
        create_submission(
            db, report=report, actor=acc_user, adapter=SubmissionAdapter.USER_NATIVE, device=device, campaign=campaign
        )
        created += 1
        campaign.executed_count = created

    if created == 0:
        campaign.status = CampaignStatus.ABORTED
        campaign.last_error = (
            "Aucun signalement n'a pu être mis en file : les comptes éligibles avaient déjà signalé ce numéro "
            "ou leurs quotas sont atteints. Le système ne double pas les signalements existants."
        )
        audit.log(
            db,
            action="campaign.aborted",
            actor_user_id=user.id,
            entity_type="campaign",
            entity_id=campaign.id,
            detail={"reason": campaign.last_error},
        )
    else:
        audit.log(
            db,
            action="campaign.created",
            actor_user_id=user.id,
            entity_type="campaign",
            entity_id=campaign.id,
            detail={
                "requested": requested_count,
                "eligible": eligible_count,
                "queued": created,
                "target_id": target.id,
            },
        )
    db.flush()
    return campaign


def require_contact_proof_for(db: Session, user: User) -> bool:
    from ..constants import Role

    return user.role not in (Role.MODERATOR, Role.ADMIN)


# ---------------------------------------------------------------------------
# Suivi des suspensions (déclaré par l'utilisateur vs confirmé par Meta)
# ---------------------------------------------------------------------------
def record_suspension(
    db: Session,
    *,
    target: Target,
    source: str,
    evidence: str,
    actor: User | None = None,
) -> Target:
    now = datetime.now(timezone.utc)
    if source == SuspensionSource.USER_DECLARED:
        if target.suspension_status != "suspended_confirmed":
            target.suspension_status = "declared_suspended"
            target.suspension_source = source
            target.suspension_confirmed_at = None
            target.suspension_evidence = evidence[:2000]
    elif source == SuspensionSource.META_WEBHOOK:
        target.suspension_status = "suspended_confirmed"
        target.suspension_source = source
        target.suspension_confirmed_at = now
        target.suspension_evidence = evidence[:2000]
        target.status = TargetStatus.SUSPENSION_CONFIRMED
    else:  # MODERATOR_VERIFIED
        target.suspension_status = "suspended_confirmed"
        target.suspension_source = source
        target.suspension_confirmed_at = now
        target.suspension_evidence = evidence[:2000]
        target.status = TargetStatus.SUSPENSION_CONFIRMED
    db.flush()
    audit.log(
        db,
        action="target.suspension_recorded",
        actor_user_id=actor.id if actor else None,
        entity_type="target",
        entity_id=target.id,
        detail={"source": source, "evidence": evidence[:300], "status": target.suspension_status},
    )
    return target


def reporting_statistics(db: Session, user_id: int) -> dict:
    counts = dict(
        db.execute(
            select(Report.status, func.count(Report.id)).where(Report.reporter_id == user_id).group_by(Report.status)
        ).all()
    )
    targets_suspended = db.execute(
        select(func.count(func.distinct(Report.target_id)))
        .where(Report.reporter_id == user_id, Report.status == ReportStatus.SUBMITTED)
        .where(
            Report.target_id.in_(
                select(Target.id).where(Target.suspension_status == "suspended_confirmed")
            )
        )
    ).scalar_one()
    return {"by_status": counts, "my_targets_suspended_confirmed": int(targets_suspended)}


def export_my_reports_csv(db: Session, user: User) -> str:
    import csv
    import io as _io

    buf = _io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(
        [
            "reference", "numero_cible", "categorie", "date_infraction", "statut",
            "preuves", "transmissions", "cree_le", "decision",
        ]
    )
    for r in db.execute(
        select(Report).where(Report.reporter_id == user.id).order_by(Report.id.desc())
    ).scalars():
        writer.writerow(
            [
                r.public_ref,
                mask_phone(decrypt_str(r.target.phone_enc) or ""),
                CATEGORY_LABELS_FR.get(r.category, r.category),
                r.occurred_at.isoformat(),
                r.status,
                len(r.evidences),
                len(r.submissions),
                r.created_at.isoformat(),
                r.decision_reason or "",
            ]
        )
    return buf.getvalue()
