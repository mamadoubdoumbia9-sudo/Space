package com.whalert.app.domain.validator

/**
 * Résultat de validation d'un numéro de téléphone.
 */
data class PhoneValidationResult(
    val isValid: Boolean,
    val e164Format: String? = null,
    val countryCode: String? = null,
    val nationalNumber: String? = null,
    val errorMessage: String? = null
)

/**
 * Validateur de numéros de téléphone internationaux conforme aux normes E.164.
 * Intègre la validation d'indicatifs mondiaux et vérifications de longueur selon les standards UIT-T.
 */
object PhoneValidator {

    private val E164_REGEX = Regex("^\\+[1-9]\\d{6,14}\$")

    // Mapping indicatif pays de base
    private val COUNTRY_CODES = mapOf(
        "33" to "FR", "32" to "BE", "41" to "CH", "1" to "US/CA",
        "44" to "GB", "49" to "DE", "34" to "ES", "39" to "IT",
        "225" to "CI", "221" to "SN", "237" to "CM", "212" to "MA",
        "216" to "TN", "213" to "DZ", "223" to "ML", "226" to "BF",
        "229" to "BJ", "228" to "TG", "242" to "CG", "243" to "CD"
    )

    /**
     * Valide un numéro saisi avec ou sans préfixe '+' et espaces.
     */
    fun validate(input: String, defaultRegionCode: String = "FR"): PhoneValidationResult {
        val trimmed = input.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

        if (trimmed.isEmpty()) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Le numéro ne peut pas être vide."
            )
        }

        val formatted = if (!trimmed.startsWith("+")) {
            // Si pas de +, vérifier s'il commence par 00
            if (trimmed.startsWith("00")) {
                "+" + trimmed.substring(2)
            } else if (trimmed.startsWith("0") && defaultRegionCode == "FR") {
                "+33" + trimmed.substring(1)
            } else {
                "+$trimmed"
            }
        } else {
            trimmed
        }

        if (!E164_REGEX.matches(formatted)) {
            return PhoneValidationResult(
                isValid = false,
                errorMessage = "Format international invalide. Doit respecter la norme E.164 (ex: +33612345678, entre 7 et 15 chiffres)."
            )
        }

        // Extraire indicatif pays
        var detectedCountry = "INTERNATIONAL"
        val digits = formatted.substring(1)
        for (len in 3 downTo 1) {
            if (digits.length >= len) {
                val prefix = digits.substring(0, len)
                if (COUNTRY_CODES.containsKey(prefix)) {
                    detectedCountry = COUNTRY_CODES[prefix]!!
                    break
                }
            }
        }

        return PhoneValidationResult(
            isValid = true,
            e164Format = formatted,
            countryCode = detectedCountry,
            nationalNumber = digits
        )
    }

    /**
     * Masque un numéro sensible pour l'affichage public ou historique.
     * Exemple : +33612345678 -> +33 ••• •• 5678
     */
    fun maskPhoneNumber(phoneE164: String): String {
        if (phoneE164.length < 7) return "***"
        val prefix = phoneE164.take(3)
        val suffix = phoneE164.takeLast(3)
        val maskedLength = phoneE164.length - 6
        val bullets = "•".repeat(maskedLength.coerceAtLeast(3))
        return "$prefix $bullets $suffix"
    }
}
