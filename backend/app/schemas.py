"""Schémas d'entrée/sortie de l'API (Pydantic v2) avec validation stricte."""
from __future__ import annotations

import re
from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

from .constants import InfractionCategory

PHONE_RE = re.compile(r"^\+?[0-9]{8,15}$")


def _clean_phone(v: str) -> str:
    v = (v or "").strip().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
    if v and not v.startswith("+"):
        v = "+" + v
    if not PHONE_RE.match(v):
        raise ValueError("Numéro invalide : utilisez le format international (+223 ...), 8 à 15 chiffres.")
    return v


# --- Authentification -------------------------------------------------------
class RegisterIn(BaseModel):
    email: EmailStr
    phone: str = Field(description="Numéro international, ex: +22361234567")
    password: str = Field(min_length=10, max_length=128)
    display_name: str = Field(default="", max_length=120)
    channel: Literal["email", "sms"] = "email"
    accept_terms: bool = True
    accept_privacy: bool = True

    @field_validator("phone")
    @classmethod
    def _p(cls, v: str) -> str:
        return _clean_phone(v)

    @field_validator("password")
    @classmethod
    def _strong(cls, v: str) -> str:
        if not re.search(r"[A-Za-z]", v) or not re.search(r"\d", v):
            raise ValueError("Le mot de passe doit contenir au moins une lettre et un chiffre.")
        return v


class VerifyIn(BaseModel):
    email: EmailStr
    code: str = Field(min_length=4, max_length=8)


class LoginIn(BaseModel):
    email: EmailStr
    password: str


class TokenOut(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    must_change_password: bool = False


class RefreshIn(BaseModel):
    refresh_token: str


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    email: str | None = None
    phone: str | None = None
    display_name: str
    role: str
    status: str
    is_verified: bool
    strikes: int
    is_business: bool
    created_at: datetime


class ChangePasswordIn(BaseModel):
    current_password: str
    new_password: str = Field(min_length=10, max_length=128)


class DeleteAccountIn(BaseModel):
    password: str
    confirm: str = Field(description="Écrivez SUPPRIMER pour confirmer")


# --- Appareils liés ---------------------------------------------------------
class LinkStartIn(BaseModel):
    label: str = Field(default="Mon téléphone", max_length=80)
    risk_consent: bool = Field(description="Consentement explicite au risque WhatsApp Web")
    consent_version: str


class LinkStartOut(BaseModel):
    device_id: int
    session_ref: str
    pairing_payload: str | None = None   # chaîne de couplage/QR fournie par la passerelle
    expires_in: int
    gateway_configured: bool
    notice: str


class LinkConfirmIn(BaseModel):
    device_id: int


class DeviceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    mode: str
    label: str
    status: str
    wa_number: str | None = None
    risk_consent: bool
    linked_at: datetime | None = None
    last_seen_at: datetime | None = None
    contacts_synced_at: datetime | None = None
    contacts_count: int


class BusinessLinkIn(BaseModel):
    waba_id: str
    phone_number_id: str
    access_token: str = Field(description="Jeton système, stocké chiffré, jamais renvoyé")


# --- Signalements -----------------------------------------------------------
class ReportCreateIn(BaseModel):
    target_phone: str
    category: InfractionCategory
    occurred_at: datetime
    description: str = Field(min_length=10, max_length=4000)
    message_ids: list[str] = Field(default_factory=list, max_length=50)
    contact_proof_method: Literal["linked_device_scan", "manual_declaration"] = "linked_device_scan"
    store_messages: bool = Field(
        default=False,
        description="Consentement explicite pour conserver le texte des messages fournis comme preuve.",
    )
    message_excerpt: str | None = Field(default=None, max_length=20000)

    @field_validator("target_phone")
    @classmethod
    def _p(cls, v: str) -> str:
        return _clean_phone(v)

    @field_validator("occurred_at")
    @classmethod
    def _not_future(cls, v: datetime) -> datetime:
        from datetime import timezone

        now = datetime.now(timezone.utc)
        if v.tzinfo is None:
            v = v.replace(tzinfo=timezone.utc)
        if v > now:
            raise ValueError("La date de l'infraction ne peut pas être dans le futur.")
        return v


class EvidenceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    kind: str
    filename: str
    mime: str
    size_bytes: int
    sha256: str
    integrity_ok: bool
    validation_detail: str | None = None
    created_at: datetime


class SubmissionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    adapter: str
    status: str
    attempts: int
    http_status: int | None = None
    response_summary: str | None = None
    error: str | None = None
    started_at: datetime | None = None
    finished_at: datetime | None = None


class ReportOut(BaseModel):
    id: int
    public_ref: str
    target_phone_masked: str
    target_phone: str | None = None
    category: str
    category_label: str
    occurred_at: datetime
    description: str
    status: str
    status_label: str
    source: str
    contact_proof_method: str | None = None
    contact_verified: bool
    created_at: datetime
    decided_at: datetime | None = None
    decision_reason: str | None = None
    target_status: str | None = None
    suspension_status: str | None = None
    evidences: list[EvidenceOut] = Field(default_factory=list)
    submissions: list[SubmissionOut] = Field(default_factory=list)


class ReportListOut(BaseModel):
    total: int
    items: list[ReportOut]


# --- Import CSV / Excel -----------------------------------------------------
class CsvPreviewRow(BaseModel):
    line: int
    target_phone: str | None = None
    category: str | None = None
    occurred_at: str | None = None
    description: str | None = None
    evidence_ref: str | None = None
    message_ids: list[str] = Field(default_factory=list)
    valid: bool
    errors: list[str] = Field(default_factory=list)


class CsvImportOut(BaseModel):
    filename: str
    total_rows: int
    valid_rows: int
    rejected_rows: int
    committed: bool
    created_report_ids: list[int] = Field(default_factory=list)
    rows: list[CsvPreviewRow]
    columns_detected: list[str] = Field(default_factory=list)


# --- Détection --------------------------------------------------------------
class SignatureOut(BaseModel):
    kind: str
    value_display: str
    severity: int
    category: str


class SignaturesOut(BaseModel):
    version: str
    count: int
    signatures: list[SignatureOut]


class ScanIn(BaseModel):
    """L'utilisateur soumet un extrait précis de sa conversation (jamais tout le fil)."""

    text: str = Field(min_length=1, max_length=8000)
    peer_phone: str | None = None
    device_id: int | None = None

    @field_validator("peer_phone")
    @classmethod
    def _p(cls, v: str | None) -> str | None:
        return _clean_phone(v) if v else None


class ScanOut(BaseModel):
    score: float
    is_suspicious: bool
    matches: list[dict[str, Any]]
    peer_known_malicious: bool
    peer_reported_count: int
    advice: list[str]


# --- Campagnes --------------------------------------------------------------
class CampaignCreateIn(BaseModel):
    target_phone: str
    requested_count: int = Field(ge=1, le=500, description="Nombre de signalements demandé")
    category: InfractionCategory
    occurred_at: datetime
    description: str = Field(min_length=10, max_length=4000)
    consent_ack: bool = Field(description="L'utilisateur confirme avoir lu l'avertissement")

    @field_validator("target_phone")
    @classmethod
    def _p(cls, v: str) -> str:
        return _clean_phone(v)


class CampaignOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    public_ref: str
    target_phone_masked: str
    requested_count: int
    eligible_count: int
    executed_count: int
    succeeded_count: int
    status: str
    notes: str | None = None
    last_error: str | None = None
    created_at: datetime
    eligibility_explanation: str | None = None


# --- Communauté -------------------------------------------------------------
class BlacklistEntryOut(BaseModel):
    phone: str | None
    phone_masked: str
    category: str
    category_label: str
    verified_reports: int
    distinct_reporters: int
    first_published_at: datetime | None
    status: str
    suspension_status: str
    suspension_source: str


class BlacklistOut(BaseModel):
    total: int
    min_reports_required: int
    note: str
    items: list[BlacklistEntryOut]


class BlockAllIn(BaseModel):
    device_id: int
    max_numbers: int = Field(default=100, ge=1, le=1000)
    consent_ack: bool


class BlockAllOut(BaseModel):
    requested: int
    blocked: int
    failed: int
    details: list[dict[str, Any]]
    device_status: str


# --- Contestations ----------------------------------------------------------
class AppealCreateIn(BaseModel):
    target_phone: str
    claimant_contact: str = Field(description="Email ou téléphone du propriétaire du numéro")
    statement: str = Field(min_length=20, max_length=4000)
    evidence_note: str | None = Field(default=None, max_length=2000)

    @field_validator("target_phone")
    @classmethod
    def _p(cls, v: str) -> str:
        return _clean_phone(v)


class AppealOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    public_ref: str
    target_phone_masked: str
    status: str
    statement: str
    created_at: datetime
    decided_at: datetime | None = None
    decision_reason: str | None = None


# --- Modération -------------------------------------------------------------
class DecisionIn(BaseModel):
    decision: Literal[
        "verify",
        "reject",
        "reject_abusive",
        "confirm_suspension",
        "deny_suspension",
        "clear_target",
        "suspend_user",
        "ban_user",
        "reinstate_user",
    ]
    reason: str = Field(min_length=5, max_length=2000)


class ModerationEvidenceOut(BaseModel):
    """Métadonnées d'une preuve, pour que le modérateur puisse réellement la contrôler."""

    id: int
    kind: str
    filename: str
    mime: str | None = None
    size_bytes: int
    sha256: str | None = None
    encrypted: bool = False
    integrity_ok: bool = False
    duplicate_of: int | None = None
    message_ids: list[str] = Field(default_factory=list)
    validation_detail: str | None = None


class ModerationItemOut(BaseModel):
    entity_type: str
    entity_id: int
    public_ref: str | None
    target_id: int | None = None
    target_phone_masked: str | None
    target_status: str | None = None
    suspension_status: str | None = None
    verified_reports: int = 0
    distinct_reporters: int = 0
    category: str | None
    summary: str
    description: str | None = None
    occurred_at: datetime | None = None
    contact_proof_method: str | None = None
    source: str | None = None
    reporter_id: int | None = None
    reporter_strikes: int | None = None
    submissions_count: int = 0
    created_at: datetime
    evidences_count: int
    evidences: list[ModerationEvidenceOut] = Field(default_factory=list)
    auto_flags: list[str] = Field(default_factory=list)


# --- Notifications ----------------------------------------------------------
class NotificationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    kind: str
    title: str
    body: str
    read_at: datetime | None
    created_at: datetime


# --- Divers -----------------------------------------------------------------
class HealthOut(BaseModel):
    status: str
    version: str
    database: str
    storage: str
    connector_configured: bool
    cloud_api_configured: bool
    verification_delivery: str


class LimitsOut(BaseModel):
    max_reports_per_hour_user: int
    max_reports_per_day_user: int
    max_actions_per_minute_user: int
    max_abusive_strikes: int
    min_verifications_to_publish: int
    min_verifications_to_escalate: int
    disclaimers: dict[str, str]
    enforcement_note: str


class UsageOut(BaseModel):
    reports_last_hour: int
    reports_last_day: int
    hourly_limit: int
    daily_limit: int
    remaining_today: int
    strikes: int
    status: str
    ban_threshold: int
