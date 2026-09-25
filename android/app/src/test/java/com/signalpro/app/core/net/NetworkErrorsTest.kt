package com.signalpro.app.core.net

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un « vérifiez votre connexion » affiché alors que la connexion fonctionne envoie
 * l'utilisateur sur une fausse piste. Chaque panne doit donc être nommée, située, et
 * accompagnée de l'affirmation qu'aucune donnée n'a été transmise.
 */
class NetworkErrorsTest {

    private val url = "http://192.168.1.20:8000/"

    private fun describe(error: Throwable) = NetworkErrors.describe(error, url)

    @Test
    fun `un nom d'hote introuvable designe le serveur, pas la connexion`() {
        val message = describe(UnknownHostException("api.signalpro.example"))
        assertTrue(message, message.contains("api.signalpro.example"))
        assertTrue(message, message.contains("Serveur introuvable"))
        assertTrue(message, message.contains("Aucune donnée n'a été transmise"))
        assertTrue(message, message.contains("Réglages"))
    }

    @Test
    fun `un refus de connexion indique le port et la piste du serveur eteint`() {
        val message = describe(ConnectException("Connection refused"))
        assertTrue(message, message.contains("192.168.1.20:8000"))
        assertTrue(message, message.contains("refusée"))
    }

    @Test
    fun `un delai depasse est distingue d'une erreur d'adresse`() {
        val message = describe(SocketTimeoutException("timeout"))
        assertTrue(message, message.contains("ne répond pas"))
        assertTrue(message, message.contains("192.168.1.20:8000"))
    }

    @Test
    fun `un certificat refuse est signale comme tel`() {
        val message = describe(SSLHandshakeException("PKIX path building failed"))
        assertTrue(message, message.contains("certificat"))
    }

    @Test
    fun `le blocage du trafic en clair est explique`() {
        val message = describe(IOException("CLEARTEXT communication to 192.168.1.20 not permitted"))
        assertTrue(message, message.contains("non chiffré"))
    }

    @Test
    fun `toute panne inconnue cite le serveur et rassure sur l'absence d'envoi`() {
        val message = describe(IOException("boom"))
        assertTrue(message, message.contains("192.168.1.20:8000"))
        assertTrue(message, message.contains("Aucune donnée n'a été transmise"))
    }
}
