package com.signalpro.app.data

import com.signalpro.app.data.remote.ReportCreateRequest
import com.signalpro.app.data.remote.ReportDto
import com.signalpro.app.data.local.ReportMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou de contrat : les noms de champs JSON doivent rester ceux de
 * `backend/app/schemas.py`. Une dérive silencieuse casserait la transmission
 * des signalements — ces tests l'attrapent avant la compilation du serveur.
 */
class ApiContractTest {

    private val json = Json { explicitNulls = false }

    @Test
    fun `ReportCreateRequest serialise les cles attendues par le serveur`() {
        val request = ReportCreateRequest(
            targetPhone = "+22361234567",
            category = "financial_scam",
            occurredAt = "2026-09-20T10:15:00Z",
            description = "Demande de transfert Wave pour un colis inexistant.",
            messageIds = listOf("ABCDEFGH12345678"),
            contactProofMethod = "manual_declaration",
            storeMessages = false,
            messageExcerpt = null,
        )
        val obj = json.encodeToJsonElement(ReportCreateRequest.serializer(), request).jsonObject

        assertTrue(obj.containsKey("target_phone"))
        assertTrue(obj.containsKey("category"))
        assertTrue(obj.containsKey("occurred_at"))
        assertTrue(obj.containsKey("message_ids"))
        assertTrue(obj.containsKey("contact_proof_method"))
        assertEquals("manual_declaration", obj["contact_proof_method"]?.jsonPrimitive?.content)
        // Aucun stockage de message sans consentement explicite.
        assertEquals("false", obj["store_messages"]?.jsonPrimitive?.content)
        assertFalse(obj.containsKey("message_excerpt"))
    }

    @Test
    fun `ReportDto tolere les champs inconnus et alimente le cache local`() {
        val payload = """
            {
              "id": 12,
              "public_ref": "SP-12",
              "target_phone_masked": "+223 ••• ••• 67",
              "target_phone": "+22361234567",
              "category": "financial_scam",
              "category_label": "Arnaque financière",
              "occurred_at": "2026-09-20T10:15:00",
              "description": "Description",
              "status": "pending_verification",
              "status_label": "En relecture",
              "source": "direct",
              "contact_verified": false,
              "champ_inconnu_du_serveur": "ignoré",
              "evidences": [
                {"id": 1, "kind": "screenshot", "filename": "a.png", "mime": "image/png",
                 "size_bytes": 10, "sha256": "abc", "integrity_ok": true}
              ],
              "submissions": []
            }
        """.trimIndent()

        val dto = Json { ignoreUnknownKeys = true }.decodeFromString(ReportDto.serializer(), payload)
        assertEquals(1, dto.evidences.size)
        val cached = ReportMapper.toCached(dto)
        assertEquals("SP-12", cached.publicRef)
        assertEquals(1, cached.evidenceCount)
        assertEquals("pending_verification", cached.status)
    }
}
