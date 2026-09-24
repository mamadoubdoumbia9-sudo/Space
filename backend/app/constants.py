"""Constantes métier partagées (catégories, statuts, canaux, textes de conformité)."""
from __future__ import annotations

from enum import StrEnum


class Role(StrEnum):
    USER = "user"
    MODERATOR = "moderator"
    ADMIN = "admin"


class UserStatus(StrEnum):
    PENDING_VERIFICATION = "pending_verification"
    ACTIVE = "active"
    SUSPENDED = "suspended"       # temporaire (ex. comportement à risque)
    BANNED = "banned"             # définitif (faux signalements répétés)


class VerificationChannel(StrEnum):
    EMAIL = "email"
    SMS = "sms"


class DeviceStatus(StrEnum):
    PENDING = "pending"           # QR généré, pas encore scanné
    AWAITING_SCAN = "awaiting_scan"
    CONNECTED = "connected"
    DISCONNECTED = "disconnected"
    REVOKED = "revoked"
    RISK_BLOCKED = "risk_blocked"


class InfractionCategory(StrEnum):
    SPAM = "spam"
    FINANCIAL_SCAM = "financial_scam"
    IMPERSONATION = "impersonation"
    HARASSMENT = "harassment"
    HATE_SPEECH = "hate_speech"
    ILLEGAL_CONTENT = "illegal_content"
    OTHER = "other"


CATEGORY_LABELS_FR: dict[str, str] = {
    InfractionCategory.SPAM: "Spam / messages non sollicités",
    InfractionCategory.FINANCIAL_SCAM: "Arnaque financière",
    InfractionCategory.IMPERSONATION: "Usurpation d'identité",
    InfractionCategory.HARASSMENT: "Harcèlement / menaces",
    InfractionCategory.HATE_SPEECH: "Discours haineux",
    InfractionCategory.ILLEGAL_CONTENT: "Diffusion de contenu illégal",
    InfractionCategory.OTHER: "Autre infraction aux CGU de WhatsApp",
}


class EvidenceKind(StrEnum):
    SCREENSHOT = "screenshot"
    CHAT_EXPORT = "chat_export"
    MESSAGE_ID = "message_id"
    HEADER_DUMP = "header_dump"     # en-tête technique du message (métadonnées WhatsApp)


class ReportStatus(StrEnum):
    DRAFT = "draft"
    PENDING_EVIDENCE = "pending_evidence"
    REJECTED_NO_EVIDENCE = "rejected_no_evidence"
    PENDING_VERIFICATION = "pending_verification"
    VERIFIED = "verified"
    REJECTED = "rejected"                 # preuve non concluante
    REJECTED_ABUSIVE = "rejected_abusive"  # faux signalement -> strike
    QUEUED = "queued"                     # en file d'envoi vers Meta
    SUBMITTED = "submitted"
    CLOSED = "closed"


class ReportSource(StrEnum):
    DIRECT = "direct"
    CSV_IMPORT = "csv_import"
    AUTO_DETECTION = "auto_detection"
    CAMPAIGN = "campaign"


class SubmissionAdapter(StrEnum):
    """Comment un signalement atteint réellement WhatsApp/Meta."""
    USER_NATIVE = "user_native"   # action native exécutée depuis le compte de l'utilisateur
    CLOUD_API = "cloud_api"       # compte professionnel via WhatsApp Business Platform
    MANUAL_GUIDED = "manual_guided"  # parcours guidé pas-à-pas (aucune automatisation)


class SubmissionStatus(StrEnum):
    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    SKIPPED = "skipped"
    RATE_LIMITED = "rate_limited"


class TargetStatus(StrEnum):
    UNKNOWN = "unknown"
    UNDER_REVIEW = "under_review"
    CONFIRMED_MALICIOUS = "confirmed_malicious"
    APPEALED = "appealed"
    CLEARED = "cleared"                  # contestation acceptée -> retiré de la liste
    SUSPENSION_CONFIRMED = "suspension_confirmed"  # confirmé par Meta, jamais par nous


class SuspensionSource(StrEnum):
    UNKNOWN = "unknown"
    MODERATOR_VERIFIED = "moderator_verified"
    META_WEBHOOK = "meta_webhook"
    USER_DECLARED = "user_declared"      # déclaratif -> n'implique aucune confirmation


class AppealStatus(StrEnum):
    SUBMITTED = "submitted"
    UNDER_REVIEW = "under_review"
    ACCEPTED = "accepted"
    REJECTED = "rejected"


class CampaignStatus(StrEnum):
    DRAFT = "draft"
    QUEUED = "queued"
    RUNNING = "running"
    COMPLETED = "completed"
    PARTIAL = "partial"
    ABORTED = "aborted"


class ContactProofMethod(StrEnum):
    LINKED_DEVICE_SCAN = "linked_device_scan"   # vérifié dans les conversations réelles
    MANUAL_DECLARATION = "manual_declaration"   # déclaratif, statut « non vérifié »
    MODERATOR_RECORD = "moderator_record"       # preuve apportée par un modérateur


# --- Textes de conformité affichés dans l'application (obligatoires) -------
DISCLAIMER_SHORT_FR = (
    "Tout faux signalement est passible de poursuites judiciaires et du bannissement de votre "
    "compte WhatsApp. Ce service ne garantit pas le bannissement des numéros signalés : seul "
    "Meta/WhatsApp examine les signalements et décide de suspendre ou non un compte."
)

DISCLAIMER_LONG_FR = (
    DISCLAIMER_SHORT_FR
    + "\n\nCette application ne peut pas et ne cherchera jamais à forcer la suspension d'un compte : "
    "elle vous aide à rassembler des preuves, à les transmettre par les canaux officiels et à suivre "
    "l'avancement. Aucune action de contournement des limites de WhatsApp n'est effectuée. "
    "Les signalements sans preuve valide ne sont jamais transmis."
)

WA_WEB_RISK_NOTICE_FR = (
    "Utiliser un appareil lié WhatsApp Web pour automatiser des signalements comporte un risque de "
    "restriction de votre compte, même en respectant les limites. Ce mode est optionnel, vous devez "
    "donner votre consentement explicite, et vous pouvez révoquer l'appareil lié à tout moment depuis "
    "WhatsApp (Appareils connectés). Si vous utilisez un compte professionnel, préférez "
    "WhatsApp Business Platform, qui supprime ce risque."
)

CONSENT_VERSION = "2026-09-1"

WA_WEB_ACTIONS_PER_MINUTE = 10
