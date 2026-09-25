package com.signalpro.app.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Validations côté client. Elles ne remplacent jamais celles du serveur : elles
 * évitent à l'utilisateur d'envoyer une requête qui serait refusée.
 */
object Validation {

    private val PHONE_REGEX = Regex("^\\+?[0-9]{8,15}$")
    private val ISO = DateTimeFormatter.ISO_DATE_TIME
    private val HUMAN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** Normalise un numéro saisi (espaces, tirets, parenthèses) au format international. */
    fun normalizePhone(input: String): String {
        val trimmed = input.trim().replace(" ", "").replace("-", "")
            .replace("(", "").replace(")", "").replace(".", "")
        return if (trimmed.startsWith("+")) trimmed else "+$trimmed"
    }

    fun phoneError(input: String): String? {
        val normalized = normalizePhone(input)
        if (normalized == "+") return "Numéro manquant."
        val digits = normalized.drop(1)
        if (!PHONE_REGEX.matches(normalized)) {
            return "Numéro invalide : 8 à 15 chiffres, format international (ex. +223 61 23 45 67)."
        }
        if (digits.length < 8) return "Numéro trop court."
        return null
    }

    fun descriptionError(input: String): String? = when {
        input.isBlank() -> "La description est obligatoire."
        input.trim().length < 10 -> "Décrivez les faits en 10 caractères minimum (soyez précis : c'est ce qui sera examiné)."
        else -> null
    }

    /** Analyse une date saisie par l'utilisateur (ISO ou jj/MM/aaaa hh:mm). */
    fun parseOccurredAt(input: String, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val value = input.trim()
        if (value.isEmpty()) return null
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(value, ISO).atZone(zone).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDateTime.parse(value, HUMAN).atZone(zone).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun occurredAtError(input: String): String? {
        if (input.isBlank()) return "La date et l'heure de réception du message sont obligatoires."
        val instant = parseOccurredAt(input) ?: return "Date illisible : utilisez jj/MM/aaaa hh:mm ou le format ISO."
        if (instant.isAfter(Instant.now())) return "La date ne peut pas être dans le futur."
        if (instant.isBefore(Instant.now().minusSeconds(60L * 60 * 24 * 365 * 5))) {
            return "Date trop ancienne (plus de 5 ans) : WhatsApp n'examine pas ces signalements."
        }
        return null
    }

    fun toApiTimestamp(input: String): String {
        val instant = parseOccurredAt(input) ?: Instant.now()
        return instant.toString()
    }

    /** Contrôle préalable d'un fichier de preuve, avant envoi réseau. */
    fun evidencePreCheck(fileName: String, sizeBytes: Long, kind: String): String? = when {
        sizeBytes == 0L -> "Le fichier est vide."
        sizeBytes > 12L * 1024 * 1024 -> "Fichier trop volumineux (12 Mo maximum)."
        kind == "screenshot" && !fileName.lowercase().matches(Regex(".*\\.(png|jpg|jpeg|webp)$")) ->
            "Une capture d'écran doit être une image PNG, JPEG ou WebP."
        kind == "chat_export" && !fileName.lowercase().matches(Regex(".*\\.(txt|zip|json)$")) ->
            "Un export de conversation doit être le fichier .txt (ou .zip) exporté par WhatsApp."
        kind == "header_dump" && !fileName.lowercase().matches(Regex(".*\\.(json|txt)$")) ->
            "L'en-tête technique doit être un fichier JSON ou texte."
        else -> null
    }

    fun toSignature(kind: String, value: String, severity: Int, category: String): Signature =
        Signature(kind = kind, value = value, severity = severity, category = category)
}

/** Signature de détection utilisée par [ScamScanner]. */
data class Signature(
    val kind: String,
    val value: String,
    val severity: Int,
    val category: String,
)
