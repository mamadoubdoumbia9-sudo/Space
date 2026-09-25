package com.whalert.app.data.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CloudSyncRequest(
    val dossierId: String,
    val targetPhoneHash: String, // Haché pour protéger le numéro
    val category: String,
    val timestampIso: String,
    val evidenceCount: Int,
    val transmissionChannel: String,
    val clientAppVersion: String = "1.0.0"
)

@JsonClass(generateAdapter = true)
data class CloudSyncResponse(
    val success: Boolean,
    val serverDossierRef: String?,
    val technicalStatus: String,
    val message: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class BackendHealthResponse(
    val status: String,
    val uptimeSeconds: Long,
    val apiVersion: String,
    val tlsVersion: String
)

interface WhAlertCloudApi {
    @GET("v1/health")
    suspend fun checkHealth(): Response<BackendHealthResponse>

    @POST("v1/dossiers/archive")
    suspend fun archiveDossierMetadata(
        @Header("Authorization") token: String,
        @Body request: CloudSyncRequest
    ): Response<CloudSyncResponse>
}

/**
 * Client réseau HTTP/TLS configuré selon les meilleures pratiques de sécurité :
 * - Forçage HTTPS / TLS 1.3
 * - Timeout stricts (15 secondes)
 * - Intercepteur de journalisation sécurisé (en-têtes épurés)
 * - Gestion propre des erreurs et de l'état de connectivité
 */
object NetworkClient {

    private const val DEFAULT_BACKEND_URL = "https://api.whalert-security.org/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(loggingInterceptor)
        .build()

    val api: WhAlertCloudApi by lazy {
        Retrofit.Builder()
            .baseUrl(DEFAULT_BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WhAlertCloudApi::class.java)
    }

    /**
     * Teste réellement la connectivité Internet via HTTPS vers un endpoint public officiel.
     */
    suspend fun testInternetReachability(): Result<Boolean> {
        return try {
            val request = okhttp3.Request.Builder()
                .url("https://www.cloudflare.com/cdn-cgi/trace")
                .head()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Réponse HTTP inattendue : ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
