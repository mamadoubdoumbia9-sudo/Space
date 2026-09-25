package com.whalert.app.domain.generator

import com.whalert.app.data.model.ReportCategory
import org.junit.Assert.*
import org.junit.Test

class ReportTextGeneratorTest {

    @Test
    fun testGeneratedTextStructure() {
        val text = ReportTextGenerator.generateFormalDossierText(
            targetPhoneE164 = "+33612345678",
            category = ReportCategory.SCAM,
            incidentTimestamp = "2026-09-25 14:30",
            description = "Message suspect reçu prétendant une livraison en attente avec demande de paiement.",
            contextNotes = "Lien fourni : https://fake-delivery-service.com",
            evidenceCount = 2,
            dossierId = "DOS-12345678"
        )

        assertTrue(text.contains("+33612345678"))
        assertTrue(text.contains("DOS-12345678"))
        assertTrue(text.contains("Arnaque financière"))
        assertTrue(text.contains("2026-09-25 14:30"))
        assertTrue(text.contains("2 pièce(s) justificative(s)"))
        assertTrue(text.contains("Conditions d'utilisation"))
        // Doit être factuel et ne jamais prétendre forcer un bannissement
        assertFalse(text.contains("bannir immédiatement"))
        assertFalse(text.contains("force WhatsApp"))
    }
}
