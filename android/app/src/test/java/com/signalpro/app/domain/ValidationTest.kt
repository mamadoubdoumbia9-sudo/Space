package com.signalpro.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Les validations locales doivent rejeter exactement ce que le serveur rejette,
 * sans jamais accepter un signalement incomplet : une preuve, une catégorie, une
 * date et un numéro valides sont obligatoires.
 */
class ValidationTest {

    @Test
    fun `normalise un numero avec espaces et tirets`() {
        assertEquals("+22361234567", Validation.normalizePhone("  +223 61-23-45-67 "))
        assertEquals("+22361234567", Validation.normalizePhone("(223) 61 23 45 67"))
    }

    @Test
    fun `accepte un numero international valide`() {
        assertNull(Validation.phoneError("+22361234567"))
        assertNull(Validation.phoneError("+33612345678"))
    }

    @Test
    fun `rejette un numero trop court ou absent`() {
        assertNotNull(Validation.phoneError(""))
        assertNotNull(Validation.phoneError("12345"))
        assertTrue(Validation.phoneError("12345")!!.contains("8 à 15"))
    }

    @Test
    fun `la description est obligatoire et suffisamment precise`() {
        assertNotNull(Validation.descriptionError(""))
        assertNotNull(Validation.descriptionError("arnaque"))
        assertNull(Validation.descriptionError("Ce contact m'a demandé un transfert Wave pour un colis fictif."))
    }

    @Test
    fun `la date de l'infraction ne peut pas etre dans le futur`() {
        val future = Instant.now().plus(2, ChronoUnit.DAYS)
        assertNotNull(Validation.occurredAtError(future.toString()))
    }

    @Test
    fun `une date passee au format humain est acceptee`() {
        val past = Instant.now().minus(3, ChronoUnit.DAYS)
        val formatted = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
            .format(past)
        assertNull(Validation.occurredAtError(formatted))
        // La conversion vers l'API doit produire un instant ISO exploitable par le serveur.
        assertTrue(Validation.toApiTimestamp(formatted).startsWith("20"))
    }

    @Test
    fun `une date illisible est refusee`() {
        assertNotNull(Validation.occurredAtError("hier soir"))
    }

    @Test
    fun `les preuves sont controlees avant tout envoi`() {
        assertNotNull(Validation.evidencePreCheck("capture.pdf", 1024, "screenshot"))
        assertNull(Validation.evidencePreCheck("capture.png", 1024, "screenshot"))
        assertNull(Validation.evidencePreCheck("discussion.txt", 1024, "chat_export"))
        assertNotNull(Validation.evidencePreCheck("fichier.txt", 13L * 1024 * 1024, "chat_export"))
        assertNotNull(Validation.evidencePreCheck("vide.txt", 0, "chat_export"))
    }
}
