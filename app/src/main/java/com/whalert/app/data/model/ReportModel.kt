package com.whalert.app.data.model

import com.squareup.moshi.JsonClass

/**
 * Catégories de signalement légitimes.
 */
enum class ReportCategory(val label: String, val description: String) {
    SPAM("Spam", "Messages indésirables massifs, sollicitations publicitaires non consenties"),
    SCAM("Arnaque financière", "Tentatives d'escroquerie, fausses opportunités d'investissement, phishing bancaire"),
    IMPERSONATION("Usurpation d'identité", "Faux profil prétendant être un proche, une institution ou une entreprise officielle"),
    HARASSMENT("Harcèlement", "Menaces répétées, intimidations, messages haineux ou persécution"),
    FRAUDULENT_CONTENT("Contenu frauduleux", "Liens malveillants, malwares, faux concours ou faux avis d'expédition"),
    MALICIOUS_BEHAVIOR("Comportement malveillant", "Tentative d'ingénierie sociale, chantage, tentative de vol de compte"),
    OTHER("Autre incident", "Autre motif suspect documenté")
}

/**
 * États du cycle de vie d'un dossier de signalement.
 */
enum class DossierStatus(val label: String) {
    DRAFT("Brouillon"),
    PREPARED("Préparé"),
    SUBMITTED("Soumis"),
    CONFIRMATION_OBTAINED("Confirmation technique obtenue"),
    FOLLOW_UP_REQUIRED("Suivi nécessaire"),
    CLOSED("Clôturé")
}

/**
 * État de modération WhatsApp (information publique strictement vérifiable).
 */
enum class ModerationDecision(val label: String, val explanation: String) {
    UNKNOWN(
        "Inconnue",
        "WhatsApp ne fournit pas de confirmation publique permettant à cette application de vérifier directement si ce compte a été suspendu."
    ),
    NO_OFFICIAL_CONFIRMATION(
        "Aucune confirmation officielle",
        "Aucun retour direct reçu du service tiers. La décision de modération relève exclusivement de WhatsApp."
    ),
    OFFICIAL_INFO_AVAILABLE(
        "Information officielle disponible",
        "Une notification officielle a été transmise ou reçue sur le canal officiel."
    ),
    ACTION_REPORTED(
        "Action signalée par WhatsApp",
        "Information officielle de traitement documentée par le support."
    )
}

/**
 * État de connexion du compte de l'utilisateur.
 */
enum class UserConnectionState(val label: String) {
    CONNECTED("CONNECTÉ"),
    NOT_CONNECTED("NON CONNECTÉ"),
    SESSION_EXPIRED("SESSION EXPIRÉE"),
    CONNECTION_ERROR("ERREUR DE CONNEXION")
}

/**
 * Pièce de preuve jointe à un dossier (capture d'écran, texte horodaté, etc.).
 */
@JsonClass(generateAdapter = true)
data class EvidenceItem(
    val id: String,
    val type: String, // "SCREENSHOT" ou "DOCUMENT"
    val filename: String,
    val uriString: String? = null,
    val note: String? = null,
    val addedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Dossier de signalement complet.
 */
@JsonClass(generateAdapter = true)
data class ReportDossier(
    val id: String,
    val targetPhoneRaw: String,
    val targetPhoneE164: String,
    val targetCountryCode: String,
    val category: ReportCategory,
    val incidentTimestamp: String,
    val description: String,
    val contextNotes: String = "",
    val evidenceList: List<EvidenceItem> = emptyList(),
    val status: DossierStatus = DossierStatus.DRAFT,
    val moderationDecision: ModerationDecision = ModerationDecision.UNKNOWN,
    val generatedFormalText: String = "",
    val userConfirmed: Boolean = false,
    val transmissionChannel: String = "IN_APP_WHATSAPP_CONTACT",
    val technicalConfirmationReceived: Boolean = false,
    val technicalConfirmationDetails: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Journal d'audit pour la console privée d'administration.
 */
@JsonClass(generateAdapter = true)
data class AuditLogEntry(
    val id: String,
    val timestampMillis: Long,
    val action: String,
    val status: String,
    val details: String
)
