package com.whalert.app.domain.generator

import com.whalert.app.data.model.ReportCategory
import com.whalert.app.data.model.ReportDossier

/**
 * Générateur de texte formel de signalement factuel et chronologique.
 *
 * RÈGLE ABSOLUE :
 * - Factuel, précis, chronologique
 * - Sans accusation non étayée
 * - Sans menace, sans invective
 * - Sans informations inventées
 * - Ne jamais présenter comme un « script de bannissement forcé »
 */
object ReportTextGenerator {

    fun generateFormalDossierText(
        targetPhoneE164: String,
        category: ReportCategory,
        incidentTimestamp: String,
        description: String,
        contextNotes: String,
        evidenceCount: Int,
        dossierId: String
    ): String {
        val evidenceSummary = if (evidenceCount > 0) {
            "$evidenceCount pièce(s) justificative(s) documentée(s) (captures d'écran horodatées et journaux d'échange archivés)."
        } else {
            "Aucun fichier annexe joint pour le moment. Récit textuel détaillé ci-dessous."
        }

        val contextSection = if (contextNotes.isNotBlank()) {
            "\n\nContexte additionnel vérifié :\n$contextNotes"
        } else ""

        return """
Objet : Signalement circonstancié d'un compte WhatsApp suspect [Dossier ref : $dossierId]

Numéro concerné :
$targetPhoneE164

Motif du signalement :
${category.label} (${category.description})

Date(s) et chronologie des événements :
$incidentTimestamp

Résumé factuel des faits constatés :
$description$contextSection

Éléments matériels et preuves disponibles :
$evidenceSummary

Demande formelle :
« En tant qu'utilisateur ayant directement constaté ces agissements, je transmets respectueusement ces éléments objectifs aux équipes de modération et de confiance & sécurité de WhatsApp afin qu'un examen soit conduit conformément aux Conditions d'utilisation et aux Politiques de sécurité de la plateforme. »

Note de transparence :
Ce dossier a été formalisé via l'outil WhAlert à des fins de traçabilité légitime et de conservation probatoire par la personne signalante.
        """.trimIndent()
    }

    fun updateDossierWithGeneratedText(dossier: ReportDossier): ReportDossier {
        val text = generateFormalDossierText(
            targetPhoneE164 = dossier.targetPhoneE164,
            category = dossier.category,
            incidentTimestamp = dossier.incidentTimestamp,
            description = dossier.description,
            contextNotes = dossier.contextNotes,
            evidenceCount = dossier.evidenceList.size,
            dossierId = dossier.id
        )
        return dossier.copy(generatedFormalText = text)
    }
}
