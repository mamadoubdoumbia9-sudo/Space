package com.signalpro.app.core.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Adresse du serveur SignalPro saisie par l'utilisateur.
 *
 * L'application est auto-hébergeable : elle ne peut pas deviner où tourne le serveur.
 * Sans cette étape, un APK livré avec une adresse d'exemple échoue partout avec un
 * message incompréhensible (« réseau indisponible ») alors que la connexion Internet
 * fonctionne très bien. Une adresse est donc validée AVANT d'être utilisée, avec un
 * message exploitable par la personne qui la saisit.
 */
object ServerUrl {

    /**
     * Valeur compilée par défaut : un domaine d'exemple qui n'existe pas.
     * Tant qu'elle est en place, l'application affiche clairement qu'aucun serveur
     * n'est configuré au lieu d'accuser la connexion de l'utilisateur.
     */
    const val PLACEHOLDER = "https://api.signalpro.example/"

    /** Adresse normalisée, prête à être utilisée comme base Retrofit. */
    data class Parsed(
        val url: String,
        val host: String,
        val port: Int,
        val cleartext: Boolean,
    )

    sealed interface Result {
        data class Valid(val parsed: Parsed) : Result
        data class Invalid(val reason: String) : Result
    }

    /** Vrai si aucune adresse réelle n'a été configurée (valeur d'exemple ou vide). */
    fun isPlaceholder(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        return url.trim().trimEnd('/').equals(PLACEHOLDER.trimEnd('/'), ignoreCase = true)
    }

    fun parse(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) {
            return Result.Invalid(
                "Adresse vide. Indiquez l'adresse de votre serveur, par exemple " +
                    "http://192.168.1.20:8000/ (ordinateur sur le même réseau) ou https://api.mon-domaine.tld/.",
            )
        }
        if (raw.any { it.isWhitespace() }) {
            return Result.Invalid("L'adresse ne doit contenir aucun espace.")
        }
        // Sans schéma explicite, on suppose HTTP : c'est le cas d'un serveur auto-hébergé.
        val withScheme =
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                raw
            } else {
                "http://$raw"
            }
        val parsed = withScheme.toHttpUrlOrNull()
            ?: return Result.Invalid(
                "Adresse invalide. Format attendu : http://hôte:port/ ou https://hôte/ " +
                    "(exemple : http://192.168.1.20:8000/).",
            )
        if (parsed.host.isBlank()) {
            return Result.Invalid("Adresse invalide : nom d'hôte manquant.")
        }
        return Result.Valid(
            Parsed(
                url = parsed.toString(),
                host = parsed.host,
                port = parsed.port,
                cleartext = !parsed.isHttps,
            ),
        )
    }

    /** « hôte:port », utilisable dans les messages d'erreur. */
    fun hostAndPort(url: String): String = when (val parsed = parse(url)) {
        is Result.Valid -> "${parsed.parsed.host}:${parsed.parsed.port}"
        is Result.Invalid -> url.trim()
    }
}
