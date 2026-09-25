package com.signalpro.app.core.net

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Traduction des pannes réseau en messages actionnables.
 *
 * Un « réseau indisponible, vérifiez votre connexion » affiché alors que la
 * connexion fonctionne parfaitement envoie l'utilisateur sur une fausse piste.
 * Chaque cause technique a donc son message, et l'adresse réellement contactée est
 * toujours citée : c'est ce qui permet de comprendre qu'il faut changer de serveur.
 */
object NetworkErrors {

    fun describe(error: Throwable, baseUrl: String): String {
        val target = ServerUrl.hostAndPort(baseUrl)
        val detail = error.message.orEmpty()

        return when {
            error is UnknownHostException || error.cause is UnknownHostException ->
                "Serveur introuvable : « $target » n'existe pas ou n'est pas joignable sur ce réseau. " +
                    "Vérifiez l'adresse du serveur dans Réglages → Serveur (ou avant l'inscription). " +
                    "Aucune donnée n'a été transmise."

            detail.contains("CLEARTEXT", ignoreCase = true) ->
                "Le serveur $target a été contacté en HTTP non chiffré et Android a bloqué la connexion. " +
                    "Utilisez https://, ou une version de l'application autorisant le réseau local. " +
                    "Aucune donnée n'a été transmise."

            error is SSLException || error.cause is SSLException ->
                "Connexion chiffrée refusée par $target (certificat invalide, expiré ou non reconnu). " +
                    "Aucune donnée n'a été transmise."

            error is SocketTimeoutException || error.cause is SocketTimeoutException ->
                "Le serveur $target ne répond pas (délai dépassé). Vérifiez qu'il est démarré et joignable " +
                    "depuis ce téléphone. Aucune donnée n'a été transmise."

            error is ConnectException || error.cause is ConnectException ->
                "Connexion refusée par $target : le serveur n'écoute pas sur ce port. Vérifiez qu'il tourne " +
                    "et qu'il écoute sur 0.0.0.0. Aucune donnée n'a été transmise."

            error is NoRouteToHostException ->
                "Aucune route vers $target : le téléphone et le serveur ne sont probablement pas sur le même réseau. " +
                    "Aucune donnée n'a été transmise."

            error is SocketException ->
                "Connexion interrompue avec $target (le serveur a coupé ou le réseau a changé). " +
                    "Aucune donnée n'a été transmise."

            else ->
                "Échange impossible avec $target. Vérifiez l'adresse du serveur dans Réglages → Serveur, " +
                    "puis votre connexion. Aucune donnée n'a été transmise."
        }
    }
}
