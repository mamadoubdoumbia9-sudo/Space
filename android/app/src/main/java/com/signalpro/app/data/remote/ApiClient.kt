package com.signalpro.app.data.remote

import com.signalpro.app.data.prefs.SecureStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response as RetrofitResponse
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Erreur d'API porteuse d'un message directement exploitable par l'utilisateur. */
class ApiException(
    val statusCode: Int,
    override val message: String,
    val retryAfterSeconds: Int? = null,
) : Exception(message) {
    val isAuth: Boolean get() = statusCode == 401 || statusCode == 403
    val isRateLimit: Boolean get() = statusCode == 429
    val isNetwork: Boolean get() = statusCode == 0
}

/** Résultat d'un appel réseau : aucune exception ne remonte jusqu'à l'UI. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiException) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

fun <T> ApiResult<T>.errorOrNull(): ApiException? = (this as? ApiResult.Failure)?.error

/**
 * Client HTTP de l'application.
 *
 * * injecte le jeton d'accès dans chaque requête ;
 * * rafraîchit la session UNE fois en cas de 401, puis déconnecte proprement ;
 * * convertit les codes d'erreur en messages explicites (jamais de « succès »
 *   affiché alors que l'appel a échoué) ;
 * * ne journalise l'Authorization sous aucun niveau de log.
 */
class ApiClient(
    baseUrl: String,
    private val secureStore: SecureStore,
    private val onSessionExpired: () -> Unit,
    debug: Boolean = false,
) {
    private val root: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = secureStore.accessToken
            val request = if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("X-Client", "signalpro-android")
                    .build()
            }
            chain.proceed(request)
        }
        .apply {
            if (debug) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(root)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ApiService::class.java)

    /** Exécute un appel et normalise le résultat. */
    suspend fun <T> call(block: suspend () -> RetrofitResponse<T>): ApiResult<T> = try {
        val response = block()
        when {
            response.isSuccessful -> {
                val body = response.body()
                if (body == null) {
                    ApiResult.Failure(ApiException(500, "Le serveur a renvoyé une réponse vide."))
                } else {
                    ApiResult.Success(body)
                }
            }
            response.code() == 401 && refreshTokens() -> {
                val retried = block()
                val body = retried.body()
                if (retried.isSuccessful && body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Failure(readError(retried))
                }
            }
            else -> ApiResult.Failure(readError(response))
        }
    } catch (io: IOException) {
        ApiResult.Failure(
            ApiException(
                0,
                "Réseau indisponible : l'action n'a pas été transmise. " +
                    "Vérifiez votre connexion puis réessayez (rien n'est simulé en attendant).",
            ),
        )
    } catch (t: Throwable) {
        ApiResult.Failure(ApiException(500, t.message ?: "Erreur inattendue."))
    }

    private fun readError(response: RetrofitResponse<*>): ApiException {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val detail = raw?.let { body ->
            runCatching { json.decodeFromString<ApiErrorBody>(body).detail }.getOrNull()
        }
        val message = detail ?: when (response.code()) {
            401 -> "Session expirée : reconnectez-vous."
            403 -> "Action refusée : vérifiez votre compte et vos consentements."
            404 -> "Élément introuvable."
            409 -> "Conflit : cette action est déjà en cours ou n'est plus possible."
            413 -> "Fichier trop volumineux."
            422 -> "Données refusées par le serveur : vérifiez le numéro, la catégorie et la preuve."
            429 -> "Limite atteinte. WhatsApp impose des plafonds stricts et nous les respectons : réessayez plus tard."
            in 500..599 -> "Erreur serveur. Aucune action n'a été considérée comme réussie."
            else -> "Erreur ${response.code()}."
        }
        if (response.code() == 401 && secureStore.refreshToken == null) onSessionExpired()
        return ApiException(response.code(), message, response.headers()["Retry-After"]?.toIntOrNull())
    }

    private fun refreshTokens(): Boolean {
        val refresh = secureStore.refreshToken ?: return false
        val payload = """{"refresh_token":"$refresh"}"""
        val request = Request.Builder()
            .url("${root}api/v1/auth/refresh")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onSessionExpired()
                    false
                } else {
                    val body = response.body?.string().orEmpty()
                    val tokens = runCatching { json.decodeFromString<TokenResponse>(body) }.getOrNull()
                    if (tokens == null) {
                        onSessionExpired()
                        false
                    } else {
                        secureStore.saveTokens(tokens.accessToken, tokens.refreshToken)
                        true
                    }
                }
            }
        } catch (_: IOException) {
            false
        }
    }
}
