package com.signalpro.app.domain

import com.signalpro.app.data.local.SignatureDao
import java.text.Normalizer

/**
 * Détection locale de spam et d'arnaques — fonctionne SANS INTERNET.
 *
 * L'analyse se fait intégralement sur le téléphone, sur le texte que
 * l'utilisateur choisit de soumettre. Aucune conversation n'est transmise, aucun
 * message n'est stocké : c'est la condition pour que la détection soit acceptable
 * du point de vue de la vie privée.
 *
 * Le moteur reproduit exactement la logique du serveur (`backend/app/services/spam.py`)
 * afin que les résultats soient cohérents en ligne et hors ligne.
 */
class ScamScanner(private val signatureDao: SignatureDao) {

    data class Match(
        val kind: String,
        val value: String,
        val severity: Int,
        val category: String,
        val weight: Double,
    )

    data class ScanResult(
        val score: Double,
        val isSuspicious: Boolean,
        val matches: List<Match>,
        val advice: List<String>,
        val analyzedOffline: Boolean = true,
    )

    private val compiledRegexes = mutableMapOf<String, Regex?>()

    /**
     * Analyse un extrait de conversation.
     *
     * Les signatures de référence sont celles stockées par le serveur et mises en
     * cache localement ; `extraSignatures` permet d'ajouter des règles ponctuelles
     * (tests, signatures utilisateur) sans perdre les règles de base.
     */
    suspend fun scan(text: String, extraSignatures: List<Signature> = emptyList()): ScanResult {
        val normalized = normalize(text)
        val matches = mutableListOf<Match>()
        var score = 0.0

        val signatures = (
            signatureDao.all().map { Validation.toSignature(it.kind, it.value, it.severity, it.category) } +
                extraSignatures
            ).distinctBy { "${it.kind}\u0000${it.value}" }

        signatures.forEach { signature ->
            when (signature.kind) {
                "keyword" -> {
                    val needle = normalize(signature.value)
                    if (needle.isNotBlank() && normalized.contains(needle)) {
                        matches += Match("keyword", signature.value, signature.severity, signature.category, 2.0 * signature.severity)
                        score += 2.0 * signature.severity
                    }
                }
                "domain" -> {
                    if (text.lowercase().contains(signature.value.lowercase())) {
                        matches += Match("domain", signature.value, signature.severity, signature.category, 3.0 * signature.severity)
                        score += 3.0 * signature.severity
                    }
                }
                "regex" -> {
                    val regex = compiledRegexes.getOrPut(signature.value) {
                        runCatching { Regex(signature.value, RegexOption.IGNORE_CASE) }.getOrNull()
                    }
                    if (regex != null && regex.containsMatchIn(text)) {
                        matches += Match("pattern", signature.value, signature.severity, signature.category, 2.5 * signature.severity)
                        score += 2.5 * signature.severity
                    }
                }
            }
        }

        // Signaux structurels indépendants des listes : demandes d'argent, de
        // codes, liens raccourcis, numéros non locaux, promesses de gain.
        STRUCTURAL_RULES.forEach { rule ->
            if (rule.regex.containsMatchIn(text)) {
                matches += Match("structure", rule.label, rule.severity, rule.category, rule.weight)
                score += rule.weight
            }
        }

        val isSuspicious = score >= 4.0
        return ScanResult(
            score = (score * 100).toInt() / 100.0,
            isSuspicious = isSuspicious,
            matches = matches.sortedByDescending { it.weight },
            advice = buildAdvice(isSuspicious),
        )
    }

    private fun buildAdvice(suspicious: Boolean): List<String> = if (suspicious) {
        listOf(
            "Ne cliquez sur aucun lien reçu dans ce message.",
            "Ne communiquez jamais un code de vérification, un code PIN ou un mot de passe.",
            "N'envoyez pas d'argent, même si l'interlocuteur se présente comme un proche ou une institution.",
            "Conservez la conversation : elle constitue la preuve si vous signalez ce numéro.",
        )
    } else {
        listOf(
            "Aucun signal d'arnaque connu détecté dans cet extrait.",
            "Restez prudent : une arnaque inédite peut ne correspondre à aucune signature.",
        )
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class StructuralRule(
        val label: String,
        val regex: Regex,
        val severity: Int,
        val category: String,
        val weight: Double,
    )

    private companion object {
        val STRUCTURAL_RULES = listOf(
            StructuralRule(
                "demande de paiement mobile money",
                Regex("(wave|orange money|moov money|mobile money|momo)\\s*(transfert|paiement|envoi)?", RegexOption.IGNORE_CASE),
                2, "financial_scam", 4.0,
            ),
            StructuralRule(
                "demande d'un code de vérification",
                Regex(
                    "(code|mot de passe|pin|otp)[^\\n]{0,25}(envoy|communic|communiqu|donn|transmet|partage|revele)" +
                        "|(envoie|envoi|donne|donnez|communique|communiquez|transmet|transmettez|partage|partagez|revele|revez)[^\\n]{0,25}(code|mot de passe|pin|otp)",
                    RegexOption.IGNORE_CASE,
                ),
                3, "impersonation", 6.0,
            ),
            StructuralRule(
                "lien raccourci ou domaine à risque",
                Regex("https?://[^\\s]*(bit\\.ly|tinyurl|cutt\\.ly|t\\.ly|shorturl|\\.(tk|ml|ga|cf|gq|xyz|top|sbs|cfd))", RegexOption.IGNORE_CASE),
                3, "financial_scam", 5.0,
            ),
            StructuralRule(
                "promesse de gain ou d'investissement garanti",
                Regex("(gagn|gagnez|profit|rendement|investiss)[^\\n]{0,30}(garanti|assur|\\d+\\s*%|double)", RegexOption.IGNORE_CASE),
                3, "financial_scam", 5.0,
            ),
            StructuralRule(
                "menace explicite",
                Regex("(je vais te (tuer|frapper|detruire)|tu vas le regretter|je connais ta famille|je publierai? tes)", RegexOption.IGNORE_CASE),
                3, "harassment", 6.0,
            ),
            StructuralRule(
                "urgence artificielle et pression au paiement",
                Regex("(dernier avertissement|aujourd'hui seulement|compte suspendu|verification immediate)", RegexOption.IGNORE_CASE),
                2, "impersonation", 3.0,
            ),
        )
    }
}
