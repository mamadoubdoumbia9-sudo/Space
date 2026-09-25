package com.signalpro.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou de régression sur le bug le plus visible de l'application : un APK livré
 * avec l'adresse d'exemple échouait partout (« réseau indisponible ») alors que la
 * connexion de l'utilisateur était parfaite. Ces tests verrouillent la détection de
 * cette situation et la validation des adresses réellement saisies.
 */
class ServerUrlTest {

    @Test
    fun `l'adresse d'exemple est reconnue comme non configuree`() {
        assertTrue(ServerUrl.isPlaceholder(ServerUrl.PLACEHOLDER))
        assertTrue(ServerUrl.isPlaceholder("https://api.signalpro.example"))
        assertTrue(ServerUrl.isPlaceholder(""))
        assertTrue(ServerUrl.isPlaceholder(null))
        assertFalse(ServerUrl.isPlaceholder("http://192.168.1.20:8000/"))
    }

    @Test
    fun `une adresse sans schema est acceptee en http`() {
        val result = ServerUrl.parse("192.168.1.20:8000")
        assertTrue(result is ServerUrl.Result.Valid)
        val parsed = (result as ServerUrl.Result.Valid).parsed
        assertEquals("http://192.168.1.20:8000/", parsed.url)
        assertEquals("192.168.1.20", parsed.host)
        assertEquals(8000, parsed.port)
        assertTrue(parsed.cleartext)
    }

    @Test
    fun `une adresse https est normalisee avec un slash final`() {
        val parsed = (ServerUrl.parse("https://api.mon-domaine.tld") as ServerUrl.Result.Valid).parsed
        assertEquals("https://api.mon-domaine.tld/", parsed.url)
        assertFalse(parsed.cleartext)
    }

    @Test
    fun `les adresses invalides sont refusees avec une raison exploitable`() {
        for (saisie in listOf("", "   ", "http://", "pas une adresse", "http://192.168.1.20:8000/a b")) {
            val result = ServerUrl.parse(saisie)
            assertTrue("« $saisie » devrait être refusée", result is ServerUrl.Result.Invalid)
            val raison = (result as ServerUrl.Result.Invalid).reason
            assertTrue("la raison doit être lisible : $raison", raison.length > 20)
        }
    }

    @Test
    fun `hostAndPort cite l'hote reel dans les messages d'erreur`() {
        assertEquals("192.168.1.20:8000", ServerUrl.hostAndPort("http://192.168.1.20:8000"))
        assertEquals("api.mon-domaine.tld:443", ServerUrl.hostAndPort("https://api.mon-domaine.tld/"))
    }
}
