package com.signalpro.app.core.domain

/**
 * Textes de conformité — identiques à ceux du serveur
 * (`backend/app/constants.py`). Ils ne doivent jamais être affaiblis : ils
 * conditionnent la licéité de l'usage de l'outil.
 */
object Disclaimer {

    const val SHORT =
        "Tout faux signalement est passible de poursuites judiciaires et du bannissement de votre " +
            "compte WhatsApp. Ce service ne garantit pas le bannissement des numéros signalés : seul " +
            "Meta/WhatsApp examine les signalements et décide de suspendre ou non un compte."

    const val LONG = SHORT +
        "\n\nSignalPro vous aide à documenter une infraction, à transmettre un signalement par les " +
        "canaux officiels et à suivre son traitement. SignalPro ne peut pas suspendre un compte et ne " +
        "tentera jamais de le faire. Aucune limite fixée par WhatsApp n'est contournée. Un signalement " +
        "sans preuve valide n'est jamais transmis."

    const val WA_WEB_RISK =
        "Utiliser un appareil lié WhatsApp Web comporte un risque de restriction de votre compte, " +
            "même en respectant les limites. Ce mode est optionnel, il exige votre consentement " +
            "explicite, et vous pouvez révoquer l'appareil lié à tout moment dans WhatsApp."

    const val BLOCK_ALL_ACK =
        "Je comprends que bloquer ces numéros est une mesure de protection personnelle et que cela " +
            "ne provoque ni ne garantit aucune suspension côté WhatsApp."

    const val CONSENT_VERSION = "2026-09-1"

    /** Libellés de catégories : miroir de `CATEGORY_LABELS_FR` côté serveur. */
    val CATEGORIES: List<Pair<String, String>> = listOf(
        "spam" to "Spam / messages non sollicités",
        "financial_scam" to "Arnaque financière",
        "impersonation" to "Usurpation d'identité",
        "harassment" to "Harcèlement / menaces",
        "hate_speech" to "Discours haineux",
        "illegal_content" to "Diffusion de contenu illégal",
        "other" to "Autre infraction aux CGU de WhatsApp",
    )

    fun categoryLabel(key: String): String =
        CATEGORIES.firstOrNull { it.first == key }?.second ?: key
}
