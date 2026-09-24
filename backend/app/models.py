"""Modèle de données complet.

Principes appliqués :
* aucun numéro de téléphone n'est stocké en clair : `*_enc` (AES-256-GCM) +
  `*_fp` (index aveugle HMAC, unique, pour la recherche et l'anti-doublon) ;
* le contenu des messages n'est JAMAIS stocké sans consentement explicite
  (`MessageSnapshot.consent_granted`) ;
* le journal d'audit est en ajout seul (append-only) et n'est jamais supprimé
  automatiquement avant `audit_log_retention_days`.
"""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .constants import (
    AppealStatus,
    CampaignStatus,
    DeviceStatus,
    ReportSource,
    ReportStatus,
    Role,
    SubmissionStatus,
    TargetStatus,
    UserStatus,
    VerificationChannel,
)
from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class TimestampMixin:
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=utcnow, onupdate=utcnow, nullable=False
    )


# ---------------------------------------------------------------------------
class User(Base, TimestampMixin):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    email_enc: Mapped[str | None] = mapped_column(Text)
    email_fp: Mapped[str | None] = mapped_column(String(64), unique=True, index=True)
    phone_enc: Mapped[str | None] = mapped_column(Text)
    phone_fp: Mapped[str | None] = mapped_column(String(64), unique=True, index=True)
    display_name: Mapped[str] = mapped_column(String(120), default="")
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)
    role: Mapped[str] = mapped_column(String(20), default=Role.USER, nullable=False)
    status: Mapped[str] = mapped_column(String(30), default=UserStatus.PENDING_VERIFICATION, nullable=False)
    is_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    verification_channel: Mapped[str | None] = mapped_column(String(10))
    verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    strikes: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    banned_reason: Mapped[str | None] = mapped_column(Text)
    banned_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    # Profil professionnel (WhatsApp Business Platform)
    is_business: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    business_waba_id: Mapped[str | None] = mapped_column(String(64))
    business_phone_number_id: Mapped[str | None] = mapped_column(String(64))
    business_verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    deletion_requested_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    devices: Mapped[list["LinkedDevice"]] = relationship(back_populates="user", cascade="all, delete-orphan")
    reports: Mapped[list["Report"]] = relationship(
        back_populates="reporter", cascade="all, delete-orphan", foreign_keys="Report.reporter_id"
    )


class VerificationCode(Base, TimestampMixin):
    __tablename__ = "verification_codes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    channel: Mapped[str] = mapped_column(String(10), default=VerificationChannel.EMAIL)
    purpose: Mapped[str] = mapped_column(String(30), default="account_verification")
    code_hash: Mapped[str] = mapped_column(String(128), nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    consumed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    delivered: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    delivery_detail: Mapped[str | None] = mapped_column(Text)


class ConsentRecord(Base):
    __tablename__ = "consents"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(40), nullable=False)  # terms | wa_web_risk | privacy | message_storage
    version: Mapped[str] = mapped_column(String(20), nullable=False)
    accepted: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    accepted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)
    ip: Mapped[str | None] = mapped_column(String(64))
    user_agent: Mapped[str | None] = mapped_column(String(255))


class LinkedDevice(Base, TimestampMixin):
    """Appareil lié WhatsApp (session utilisateur) ou compte WhatsApp Business."""

    __tablename__ = "linked_devices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    mode: Mapped[str] = mapped_column(String(20), default="web_linked")  # web_linked | cloud_api
    label: Mapped[str] = mapped_column(String(80), default="")
    wa_jid_enc: Mapped[str | None] = mapped_column(Text)
    wa_jid_fp: Mapped[str | None] = mapped_column(String(64), index=True)
    wa_number_enc: Mapped[str | None] = mapped_column(Text)
    wa_number_fp: Mapped[str | None] = mapped_column(String(64), index=True)
    status: Mapped[str] = mapped_column(String(24), default=DeviceStatus.PENDING, nullable=False)
    # Référence de session côté passerelle locale (jamais le matériel de clé).
    session_ref: Mapped[str | None] = mapped_column(String(80), unique=True)
    risk_consent: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    risk_consent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    risk_consent_version: Mapped[str | None] = mapped_column(String(20))
    linked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    phone_number_id: Mapped[str | None] = mapped_column(String(64))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked_reason: Mapped[str | None] = mapped_column(Text)
    # Instantané du carnet de conversations (empreintes uniquement, 0 contenu).
    contacts_synced_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    contacts_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)

    user: Mapped[User] = relationship(back_populates="devices")


class ContactIndex(Base):
    """Empreinte des conversations présentes sur l'appareil lié de l'utilisateur.

    Sert à prouver qu'un numéro a réellement contacté l'utilisateur (anti-abus
    « interdiction de signaler des numéros qui ne vous ont pas contacté »).
    Aucun message, aucun nom, aucun contenu : seulement une empreinte HMAC et
    la date du dernier message.
    """

    __tablename__ = "contact_index"
    __table_args__ = (UniqueConstraint("device_id", "peer_fp", name="uq_contact_device_peer"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("linked_devices.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    peer_fp: Mapped[str] = mapped_column(String(64), index=True)
    is_group: Mapped[bool] = mapped_column(Boolean, default=False)
    last_message_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    synced_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Target(Base, TimestampMixin):
    """Numéro signalé. Le numéro lui-même reste chiffré."""

    __tablename__ = "targets"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    phone_enc: Mapped[str] = mapped_column(Text, nullable=False)
    phone_fp: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    country_code: Mapped[str | None] = mapped_column(String(6))
    status: Mapped[str] = mapped_column(String(30), default=TargetStatus.UNKNOWN, nullable=False)
    total_reports: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    verified_reports: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    distinct_reporters: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    abusive_reports: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    delisted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    suspension_status: Mapped[str] = mapped_column(String(30), default="unknown", nullable=False)
    suspension_source: Mapped[str] = mapped_column(String(30), default="unknown", nullable=False)
    suspension_confirmed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    suspension_evidence: Mapped[str | None] = mapped_column(Text)
    risk_score: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    main_category: Mapped[str | None] = mapped_column(String(30))


class Report(Base, TimestampMixin):
    __tablename__ = "reports"
    __table_args__ = (
        Index("ix_reports_target_status", "target_id", "status"),
        UniqueConstraint("reporter_id", "target_id", "occurred_at", name="uq_report_dedup"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    public_ref: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    reporter_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("targets.id", ondelete="CASCADE"), index=True)
    category: Mapped[str] = mapped_column(String(30), nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    description: Mapped[str] = mapped_column(Text, default="")
    source: Mapped[str] = mapped_column(String(20), default=ReportSource.DIRECT)
    status: Mapped[str] = mapped_column(String(30), default=ReportStatus.PENDING_EVIDENCE, nullable=False)

    # Preuve de contact (anti-abus)
    contact_proof_method: Mapped[str | None] = mapped_column(String(30))
    contact_proof_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    contact_proof_detail: Mapped[str | None] = mapped_column(Text)

    # Vérification humaine
    moderator_id: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    decision_reason: Mapped[str | None] = mapped_column(Text)
    strike_issued: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    auto_flags: Mapped[str | None] = mapped_column(Text)  # JSON: signaux automatiques

    reporter: Mapped[User] = relationship(back_populates="reports", foreign_keys=[reporter_id])
    target: Mapped[Target] = relationship()
    evidences: Mapped[list["Evidence"]] = relationship(back_populates="report", cascade="all, delete-orphan")
    submissions: Mapped[list["ReportSubmission"]] = relationship(
        back_populates="report", cascade="all, delete-orphan"
    )


class Evidence(Base, TimestampMixin):
    __tablename__ = "evidences"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(20), nullable=False)
    filename: Mapped[str] = mapped_column(String(255), default="")
    mime: Mapped[str] = mapped_column(String(120), default="")
    size_bytes: Mapped[int] = mapped_column(Integer, default=0)
    sha256: Mapped[str] = mapped_column(String(64), index=True)
    storage_key: Mapped[str] = mapped_column(String(255))
    encrypted: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    integrity_ok: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    validation_detail: Mapped[str | None] = mapped_column(Text)
    message_ids: Mapped[str | None] = mapped_column(Text)  # JSON list d'identifiants WhatsApp réels
    duplicate_of: Mapped[int | None] = mapped_column(ForeignKey("evidences.id", ondelete="SET NULL"))
    captured_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    report: Mapped[Report] = relationship(back_populates="evidences")


class MessageSnapshot(Base):
    """Copie d'un message — UNIQUEMENT si l'utilisateur a donné son consentement.

    Sans `consent_granted`, aucune ligne n'est créée.
    """

    __tablename__ = "message_snapshots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    consent_granted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    body_enc: Mapped[str] = mapped_column(Text, nullable=False)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    direction: Mapped[str] = mapped_column(String(10), default="inbound")
    wa_message_id: Mapped[str | None] = mapped_column(String(120))
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Campaign(Base, TimestampMixin):
    """Demande d'envoi multiple pour un même numéro.

    `requested_count` est ce que l'utilisateur demande ; `eligible_count` est ce
    qui est réellement possible, c'est-à-dire le nombre de comptes liés distincts
    ayant réellement été contactés par la cible et ayant consenti. La campagne ne
    peut jamais dépasser `eligible_count` : on n'envoie pas de signalements
    fabriqués depuis des comptes qui n'ont jamais reçu de message de la cible.
    """

    __tablename__ = "campaigns"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    public_ref: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    created_by: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("targets.id", ondelete="CASCADE"), index=True)
    requested_count: Mapped[int] = mapped_column(Integer, nullable=False)
    eligible_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    executed_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    succeeded_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default=CampaignStatus.DRAFT, nullable=False)
    consent_ack: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    notes: Mapped[str | None] = mapped_column(Text)
    last_error: Mapped[str | None] = mapped_column(Text)


class ReportSubmission(Base, TimestampMixin):
    """Trace d'une transmission réellement tentée vers WhatsApp/Meta."""

    __tablename__ = "report_submissions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    report_id: Mapped[int] = mapped_column(ForeignKey("reports.id", ondelete="CASCADE"), index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("targets.id", ondelete="CASCADE"), index=True)
    campaign_id: Mapped[int | None] = mapped_column(ForeignKey("campaigns.id", ondelete="SET NULL"), index=True)
    actor_user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    actor_device_id: Mapped[int | None] = mapped_column(ForeignKey("linked_devices.id", ondelete="SET NULL"))
    adapter: Mapped[str] = mapped_column(String(20), nullable=False)
    status: Mapped[str] = mapped_column(String(20), default=SubmissionStatus.QUEUED, nullable=False)
    attempts: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    request_hash: Mapped[str | None] = mapped_column(String(64))
    http_status: Mapped[int | None] = mapped_column(Integer)
    response_summary: Mapped[str | None] = mapped_column(Text)  # réponse redigée (jamais de jeton)
    external_ref: Mapped[str | None] = mapped_column(String(120))
    error: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    report: Mapped[Report] = relationship(back_populates="submissions")


class Dossier(Base, TimestampMixin):
    """Dossier groupé horodaté, constitué uniquement au seuil de vérifications atteint."""

    __tablename__ = "dossiers"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    public_ref: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("targets.id", ondelete="CASCADE"), index=True)
    report_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    distinct_reporters: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    pdf_sha256: Mapped[str] = mapped_column(String(64), nullable=False)
    storage_key: Mapped[str] = mapped_column(String(255), nullable=False)
    generated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, nullable=False)
    dispatched_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    dispatch_status: Mapped[str] = mapped_column(String(24), default="pending", nullable=False)
    dispatch_channel: Mapped[str | None] = mapped_column(String(60))
    dispatch_ref: Mapped[str | None] = mapped_column(String(255))
    dispatch_error: Mapped[str | None] = mapped_column(Text)


class Appeal(Base, TimestampMixin):
    """Contestation déposée par le propriétaire d'un numéro listé."""

    __tablename__ = "appeals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    public_ref: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    target_id: Mapped[int] = mapped_column(ForeignKey("targets.id", ondelete="CASCADE"), index=True)
    claimant_contact_enc: Mapped[str] = mapped_column(Text, nullable=False)
    claimant_contact_fp: Mapped[str] = mapped_column(String(64), index=True)
    statement: Mapped[str] = mapped_column(Text, nullable=False)
    evidence_note: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), default=AppealStatus.SUBMITTED, nullable=False)
    moderator_id: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    decision_reason: Mapped[str | None] = mapped_column(Text)


class ModerationDecision(Base):
    """Décision humaine tracée (jamais effacée, même si le rapport est supprimé)."""

    __tablename__ = "moderation_decisions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    moderator_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    entity_type: Mapped[str] = mapped_column(String(20), nullable=False)  # report | target | appeal | user
    entity_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    decision: Mapped[str] = mapped_column(String(30), nullable=False)
    reason: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Strike(Base):
    __tablename__ = "strikes"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    report_id: Mapped[int | None] = mapped_column(ForeignKey("reports.id", ondelete="SET NULL"))
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    issued_by: Mapped[int | None] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"))
    automatic: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Notification(Base):
    __tablename__ = "notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    kind: Mapped[str] = mapped_column(String(40), nullable=False)
    title: Mapped[str] = mapped_column(String(160), nullable=False)
    body: Mapped[str] = mapped_column(Text, default="")
    payload: Mapped[str | None] = mapped_column(Text)  # JSON
    read_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class AuditLog(Base):
    """Journal d'audit en ajout seul — répond aux demandes judiciaires."""

    __tablename__ = "audit_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    actor_user_id: Mapped[int | None] = mapped_column(Integer, index=True)
    actor_role: Mapped[str | None] = mapped_column(String(20))
    action: Mapped[str] = mapped_column(String(60), nullable=False, index=True)
    entity_type: Mapped[str | None] = mapped_column(String(30))
    entity_id: Mapped[int | None] = mapped_column(Integer)
    ip: Mapped[str | None] = mapped_column(String(64))
    user_agent: Mapped[str | None] = mapped_column(String(255))
    detail: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, index=True)


class UsageCounter(Base):
    """Compteur de débit générique (minute / heure / jour)."""

    __tablename__ = "usage_counters"
    __table_args__ = (UniqueConstraint("user_id", "action", "window_key", name="uq_usage_window"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    action: Mapped[str] = mapped_column(String(40), nullable=False)
    window_key: Mapped[str] = mapped_column(String(40), nullable=False)
    count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)


class SpamSignature(Base):
    """Signatures utilisées par la détection de spam (mots-clés, domaines, numéros)."""

    __tablename__ = "spam_signatures"
    __table_args__ = (UniqueConstraint("kind", "value_hash", name="uq_signature"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    kind: Mapped[str] = mapped_column(String(20), nullable=False)  # keyword | domain | phone | regex
    value_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    value_display: Mapped[str] = mapped_column(Text, default="")
    severity: Mapped[int] = mapped_column(Integer, default=1)
    category: Mapped[str] = mapped_column(String(30), default="spam")
    source: Mapped[str] = mapped_column(String(20), default="seed")
    active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class DetectionHit(Base):
    """Résultat d'une détection côté utilisateur (aucun message complet stocké)."""

    __tablename__ = "detection_hits"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    device_id: Mapped[int | None] = mapped_column(ForeignKey("linked_devices.id", ondelete="SET NULL"))
    target_fp: Mapped[str | None] = mapped_column(String(64), index=True)
    category: Mapped[str] = mapped_column(String(30), default="spam")
    match_kind: Mapped[str] = mapped_column(String(20), default="keyword")
    matched_sample: Mapped[str | None] = mapped_column(Text)  # extrait fourni par l'utilisateur
    score: Mapped[float] = mapped_column(Float, default=0.0)
    converted_report_id: Mapped[int | None] = mapped_column(ForeignKey("reports.id", ondelete="SET NULL"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class ConnectorEvent(Base):
    """Événements brut reçus de la passerelle locale (observabilité / audit)."""

    __tablename__ = "connector_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[int | None] = mapped_column(ForeignKey("linked_devices.id", ondelete="SET NULL"), index=True)
    kind: Mapped[str] = mapped_column(String(40), nullable=False)
    payload_summary: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
