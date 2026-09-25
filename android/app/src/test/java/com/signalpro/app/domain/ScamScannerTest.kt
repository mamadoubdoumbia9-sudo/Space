package com.signalpro.app.domain

import com.signalpro.app.data.local.CachedSignature
import com.signalpro.app.data.local.SignatureDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le scanner hors ligne est le même moteur que `backend/app/services/spam.py` :
 * ces tests verrouillent le seuil (4.0) et le poids des familles de signatures.
 */
class ScamScannerTest {

    private class FakeSignatureDao(private val rows: List<CachedSignature>) : SignatureDao {
        override suspend fun all(): List<CachedSignature> = rows
        override suspend fun currentVersion(): String? = rows.firstOrNull()?.version
        override suspend fun upsertAll(signatures: List<CachedSignature>) = Unit
        override suspend fun pruneOldVersions(version: String) = Unit
        override suspend fun count(): Int = rows.size
    }

    private val signatures = listOf(
        CachedSignature("1", "keyword", "compte bloqué", 2, "impersonation", "v1"),
        CachedSignature("2", "keyword", "wave", 1, "financial_scam", "v1"),
        CachedSignature("3", "domain", "arnaque-exemple.tk", 3, "financial_scam", "v1"),
    )

    private fun scanner() = ScamScanner(FakeSignatureDao(signatures))

    @Test
    fun `un message legitime n'est pas suspect`() = runTest {
        val result = scanner().scan("Salut, on se voit demain à 18h devant la pharmacie ?", emptyList())
        assertFalse(result.isSuspicious)
        assertTrue(result.score < 4.0)
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `un mot-cle connu suffit a alerter`() = runTest {
        val result = scanner().scan("Votre compte bloqué, confirmez vos informations", emptyList())
        assertTrue(result.score >= 4.0)
        assertTrue(result.isSuspicious)
        assertTrue(result.matches.any { it.kind == "keyword" })
    }

    @Test
    fun `une demande de code de verification est fortement suspecte`() = runTest {
        val result = scanner().scan("Envoie-moi le code reçu par SMS pour valider ton paiement", emptyList())
        assertTrue(result.isSuspicious)
        assertTrue(result.matches.any { it.category == "impersonation" })
    }

    @Test
    fun `un lien raccourci est detecte comme risque`() = runTest {
        val result = scanner().scan("Gagnez 500 000 FCFA : http://bit.ly/xyz123", emptyList())
        assertTrue(result.isSuspicious)
        assertTrue(result.matches.any { it.kind == "structure" })
    }

    @Test
    fun `une menace explicite declenche le signalement`() = runTest {
        val result = scanner().scan("Je vais te frapper si tu ne réponds pas", emptyList())
        assertTrue(result.isSuspicious)
        assertTrue(result.matches.any { it.category == "harassment" })
    }

    @Test
    fun `les signatures personnalisees sont bien prises en compte`() = runTest {
        val dao = FakeSignatureDao(signatures)
        val engine = ScamScanner(dao)
        val result = engine.scan("message neutre", listOf(Signature("keyword", "message neutre", 3, "spam")))
        assertTrue(result.matches.any { it.kind == "keyword" })
        assertTrue(result.score >= 4.0)
    }
}
