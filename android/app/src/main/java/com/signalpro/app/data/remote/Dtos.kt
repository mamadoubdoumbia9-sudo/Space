package com.signalpro.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Contrats d'API — miroir exact de `backend/app/schemas.py`.
 * Toute évolution côté serveur doit être répercutée ici (et inversement).
 */

@Serializable
data class RegisterRequest(
    val email: String,
    val phone: String,
    val password: String,
    @SerialName("display_name") val displayName: String = "",
    val channel: String = "email",
    @SerialName("accept_terms") val acceptTerms: Boolean = true,
    @SerialName("accept_privacy") val acceptPrivacy: Boolean = true,
)

@Serializable
data class RegisterResponse(
    @SerialName("user_id") val userId: Int,
    @SerialName("verification_channel") val channel: String,
    val delivered: Boolean,
    @SerialName("delivery_detail") val deliveryDetail: String? = null,
    @SerialName("dev_code") val devCode: String? = null,
    val disclaimer: String? = null,
)

@Serializable
data class VerifyRequest(val email: String, val code: String)

@Serializable
data class ResendCodeRequest(val email: String, val password: String)

@Serializable
data class ResendCodeResponse(val delivered: Boolean, @SerialName("delivery_detail") val detail: String? = null)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class UserDto(
    val id: Int,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "user",
    val status: String = "pending_verification",
    @SerialName("is_verified") val isVerified: Boolean = false,
    val strikes: Int = 0,
    @SerialName("is_business") val isBusiness: Boolean = false,
)

@Serializable
data class LimitsDto(
    @SerialName("max_reports_per_hour_user") val maxReportsPerHour: Int,
    @SerialName("max_reports_per_day_user") val maxReportsPerDay: Int,
    @SerialName("max_actions_per_minute_user") val maxActionsPerMinute: Int,
    @SerialName("max_abusive_strikes") val maxAbusiveStrikes: Int,
    @SerialName("min_verifications_to_publish") val minToPublish: Int,
    @SerialName("min_verifications_to_escalate") val minToEscalate: Int,
    val disclaimers: Map<String, String> = emptyMap(),
    @SerialName("enforcement_note") val enforcementNote: String = "",
)

@Serializable
data class UsageDto(
    @SerialName("reports_last_hour") val reportsLastHour: Int,
    @SerialName("reports_last_day") val reportsLastDay: Int,
    @SerialName("hourly_limit") val hourlyLimit: Int,
    @SerialName("daily_limit") val dailyLimit: Int,
    @SerialName("remaining_today") val remainingToday: Int,
    val strikes: Int,
    val status: String,
    @SerialName("ban_threshold") val banThreshold: Int,
)

@Serializable
data class GatewayStatusDto(
    val configured: Boolean,
    val reachable: Boolean = false,
    @SerialName("base_url") val baseUrl: String? = null,
    val reason: String? = null,
    val version: String? = null,
    val capabilities: GatewayCapabilitiesDto? = null,
    val notice: String? = null,
)

@Serializable
data class GatewayCapabilitiesDto(
    @SerialName("report_native") val reportNative: Boolean = false,
    @SerialName("block_contact") val blockContact: Boolean = false,
    @SerialName("contact_sync") val contactSync: Boolean = true,
)

@Serializable
data class LinkStartRequest(
    val label: String,
    @SerialName("risk_consent") val riskConsent: Boolean,
    @SerialName("consent_version") val consentVersion: String,
)

@Serializable
data class LinkStartResponse(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("session_ref") val sessionRef: String,
    @SerialName("pairing_payload") val pairingPayload: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 120,
    @SerialName("gateway_configured") val gatewayConfigured: Boolean,
    val notice: String = "",
)

@Serializable
data class DeviceDto(
    val id: Int,
    val mode: String = "web_linked",
    val label: String = "",
    val status: String = "pending",
    @SerialName("wa_number") val waNumber: String? = null,
    @SerialName("risk_consent") val riskConsent: Boolean = false,
    @SerialName("contacts_count") val contactsCount: Int = 0,
    @SerialName("linked_at") val linkedAt: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReportCreateRequest(
    @SerialName("target_phone") val targetPhone: String,
    val category: String,
    @SerialName("occurred_at") val occurredAt: String,
    val description: String,
    @SerialName("message_ids") val messageIds: List<String> = emptyList(),
    @SerialName("contact_proof_method") val contactProofMethod: String = "linked_device_scan",
    // Toujours transmis, même à `false` : le serveur reçoit une déclaration
    // explicite « aucun message stocké » au lieu d'une absence de champ.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("store_messages") val storeMessages: Boolean = false,
    @SerialName("message_excerpt") val messageExcerpt: String? = null,
)

@Serializable
data class EvidenceDto(
    val id: Int,
    val kind: String,
    val filename: String = "",
    val mime: String = "",
    @SerialName("size_bytes") val sizeBytes: Int = 0,
    val sha256: String = "",
    @SerialName("integrity_ok") val integrityOk: Boolean = false,
    @SerialName("validation_detail") val validationDetail: String? = null,
)

@Serializable
data class SubmissionDto(
    val id: Int,
    val adapter: String,
    val status: String,
    val attempts: Int = 0,
    @SerialName("response_summary") val responseSummary: String? = null,
    val error: String? = null,
)

@Serializable
data class ReportDto(
    val id: Int,
    @SerialName("public_ref") val publicRef: String,
    @SerialName("target_phone_masked") val targetPhoneMasked: String,
    @SerialName("target_phone") val targetPhone: String? = null,
    val category: String,
    @SerialName("category_label") val categoryLabel: String,
    @SerialName("occurred_at") val occurredAt: String,
    val description: String = "",
    val status: String,
    @SerialName("status_label") val statusLabel: String,
    val source: String = "direct",
    @SerialName("contact_verified") val contactVerified: Boolean = false,
    @SerialName("decision_reason") val decisionReason: String? = null,
    val evidences: List<EvidenceDto> = emptyList(),
    val submissions: List<SubmissionDto> = emptyList(),
)

@Serializable
data class ReportListDto(val total: Int, val items: List<ReportDto>)

@Serializable
data class SubmitResponse(
    val queued: Boolean,
    @SerialName("submission_id") val submissionId: Int? = null,
    val adapter: String? = null,
    val fallback: String? = null,
    val steps: List<String> = emptyList(),
    val detail: String = "",
)

@Serializable
data class BlacklistEntryDto(
    val phone: String? = null,
    @SerialName("phone_masked") val phoneMasked: String,
    val category: String,
    @SerialName("category_label") val categoryLabel: String,
    @SerialName("verified_reports") val verifiedReports: Int,
    @SerialName("distinct_reporters") val distinctReporters: Int,
    val status: String,
    @SerialName("suspension_status") val suspensionStatus: String,
    @SerialName("suspension_source") val suspensionSource: String,
)

@Serializable
data class BlacklistDto(
    val total: Int,
    @SerialName("min_reports_required") val minReportsRequired: Int,
    val note: String,
    val items: List<BlacklistEntryDto>,
)

@Serializable
data class BlacklistExportDto(val count: Int, val numbers: List<String>, val disclaimer: String)

@Serializable
data class BlockAllRequest(
    @SerialName("device_id") val deviceId: Int,
    @SerialName("max_numbers") val maxNumbers: Int = 100,
    @SerialName("consent_ack") val consentAck: Boolean,
)

@Serializable
data class BlockAllResponse(
    val requested: Int,
    val blocked: Int,
    val failed: Int,
    @SerialName("device_status") val deviceStatus: String,
    val details: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
)

@Serializable
data class AppealRequest(
    @SerialName("target_phone") val targetPhone: String,
    @SerialName("claimant_contact") val claimantContact: String,
    val statement: String,
    @SerialName("evidence_note") val evidenceNote: String? = null,
)

@Serializable
data class AppealDto(
    val id: Int,
    @SerialName("public_ref") val publicRef: String,
    @SerialName("target_phone_masked") val targetPhoneMasked: String,
    val status: String,
    val statement: String,
)

@Serializable
data class ScanRequest(
    val text: String,
    @SerialName("peer_phone") val peerPhone: String? = null,
    @SerialName("device_id") val deviceId: Int? = null,
)

@Serializable
data class ScanResponse(
    val score: Double,
    @SerialName("is_suspicious") val isSuspicious: Boolean,
    val matches: List<Map<String, kotlinx.serialization.json.JsonElement>> = emptyList(),
    @SerialName("peer_known_malicious") val peerKnownMalicious: Boolean = false,
    @SerialName("peer_reported_count") val peerReportedCount: Int = 0,
    val advice: List<String> = emptyList(),
)

@Serializable
data class SignatureDto(
    val kind: String,
    @SerialName("value_display") val valueDisplay: String,
    val severity: Int,
    val category: String,
)

@Serializable
data class SignaturesDto(val version: String, val count: Int, val signatures: List<SignatureDto>)

@Serializable
data class CampaignPreviewRequest(
    @SerialName("target_phone") val targetPhone: String,
    @SerialName("requested_count") val requestedCount: Int,
    val category: String,
    @SerialName("occurred_at") val occurredAt: String,
    val description: String,
    @SerialName("consent_ack") val consentAck: Boolean,
)

@Serializable
data class CampaignPreviewDto(
    @SerialName("target_in_database") val targetInDatabase: Boolean,
    @SerialName("requested_count") val requestedCount: Int,
    @SerialName("eligible_accounts") val eligibleAccounts: Int,
    @SerialName("executable_count") val executableCount: Int,
    @SerialName("your_connected_devices") val yourConnectedDevices: Int,
    val explanation: String = "",
    @SerialName("blocking_reason") val blockingReason: String? = null,
)

@Serializable
data class CampaignDto(
    val id: Int,
    @SerialName("public_ref") val publicRef: String,
    @SerialName("target_phone_masked") val targetPhoneMasked: String,
    @SerialName("requested_count") val requestedCount: Int,
    @SerialName("eligible_count") val eligibleCount: Int,
    @SerialName("executed_count") val executedCount: Int,
    @SerialName("succeeded_count") val succeededCount: Int,
    val status: String,
    val notes: String? = null,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("eligibility_explanation") val eligibilityExplanation: String? = null,
)

@Serializable
data class OverviewDto(
    val usage: UsageDto,
    val devices: List<DeviceDto> = emptyList(),
    val reports: Map<String, Int> = emptyMap(),
    val suspensions: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    @SerialName("unread_notifications") val unreadNotifications: Int = 0,
)

@Serializable
data class AlertDto(
    @SerialName("target_id") val targetId: Int,
    @SerialName("phone_masked") val phoneMasked: String,
    @SerialName("category_label") val categoryLabel: String,
    @SerialName("verified_reports") val verifiedReports: Int,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    val advice: String = "",
)

@Serializable
data class NotificationDto(
    val id: Int,
    val kind: String,
    val title: String,
    val body: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BusinessLinkRequest(
    @SerialName("waba_id") val wabaId: String,
    @SerialName("phone_number_id") val phoneNumberId: String,
    @SerialName("access_token") val accessToken: String,
)

@Serializable
data class DeleteAccountRequest(val password: String, val confirm: String)

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

@Serializable
data class SimpleOk(val ok: Boolean, val detail: String? = null, val note: String? = null)

@Serializable
data class ApiErrorBody(val detail: String? = null, val message: String? = null)
