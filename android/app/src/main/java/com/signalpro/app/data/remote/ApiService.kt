package com.signalpro.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interface de l'API SignalPro (FastAPI). Aucune méthode ne « simule » un
 * résultat : chaque appel correspond à un traitement réel côté serveur.
 */
interface ApiService {

    // --- Santé / configuration -------------------------------------------
    @GET("health") suspend fun health(): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/v1/auth/limits") suspend fun limits(): Response<LimitsDto>

    // --- Authentification --------------------------------------------------
    @POST("api/v1/auth/register") suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("api/v1/auth/verify") suspend fun verify(@Body body: VerifyRequest): Response<TokenResponse>

    @POST("api/v1/auth/resend-code") suspend fun resendCode(@Body body: ResendCodeRequest): Response<ResendCodeResponse>

    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @GET("api/v1/auth/me") suspend fun me(): Response<UserDto>

    @POST("api/v1/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): Response<SimpleOk>

    @POST("api/v1/auth/consents")
    suspend fun recordConsent(@Query("kind") kind: String, @Query("accepted") accepted: Boolean): Response<SimpleOk>

    @DELETE("api/v1/auth/account")
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    // --- Appareils WhatsApp ------------------------------------------------
    @GET("api/v1/devices/gateway/status") suspend fun gatewayStatus(): Response<GatewayStatusDto>

    @GET("api/v1/devices") suspend fun devices(): Response<List<DeviceDto>>

    @POST("api/v1/devices/link/start") suspend fun linkStart(@Body body: LinkStartRequest): Response<LinkStartResponse>

    @POST("api/v1/devices/link/confirm")
    suspend fun linkConfirm(@Body body: Map<String, Int>): Response<DeviceDto>

    @POST("api/v1/devices/{id}/sync")
    suspend fun syncDevice(@Path("id") deviceId: Int): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @POST("api/v1/devices/{id}/refresh") suspend fun refreshDevice(@Path("id") deviceId: Int): Response<DeviceDto>

    @DELETE("api/v1/devices/{id}") suspend fun revokeDevice(@Path("id") deviceId: Int): Response<SimpleOk>

    @POST("api/v1/devices/business/link")
    suspend fun linkBusiness(@Body body: BusinessLinkRequest): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/v1/devices/business/status")
    suspend fun businessStatus(): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @POST("api/v1/devices/report-suspension")
    suspend fun reportSuspension(
        @Query("target_phone") targetPhone: String,
        @Query("note") note: String? = null,
    ): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    // --- Signalements ------------------------------------------------------
    @GET("api/v1/reports/usage") suspend fun usage(): Response<UsageDto>

    @GET("api/v1/reports") suspend fun reports(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("status_filter") status: String? = null,
    ): Response<ReportListDto>

    @GET("api/v1/reports/{id}") suspend fun report(@Path("id") id: Int): Response<ReportDto>

    @POST("api/v1/reports") suspend fun createReport(@Body body: ReportCreateRequest): Response<ReportDto>

    @Multipart
    @POST("api/v1/reports/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") reportId: Int,
        @Part("kind") kind: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<EvidenceDto>

    @Multipart
    @POST("api/v1/reports/{id}/submit")
    suspend fun submitReport(
        @Path("id") reportId: Int,
        @Part("adapter") adapter: RequestBody,
        @Part("device_id") deviceId: RequestBody?,
        @Part("manual_ack") manualAck: RequestBody,
    ): Response<SubmitResponse>

    @Multipart
    @POST("api/v1/reports/{id}/mark-manual-done")
    suspend fun markManualDone(
        @Path("id") reportId: Int,
        @Part("done") done: RequestBody,
        @Part("note") note: RequestBody?,
    ): Response<SimpleOk>

    @GET("api/v1/reports/{id}/export") suspend fun exportReport(@Path("id") id: Int): Response<ResponseBody>

    @GET("api/v1/reports/import/template") suspend fun importTemplate(): Response<ResponseBody>

    @Multipart
    @POST("api/v1/reports/import/preview")
    suspend fun importPreview(@Part file: MultipartBody.Part): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @Multipart
    @POST("api/v1/reports/import/commit")
    suspend fun importCommit(@Part file: MultipartBody.Part): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/v1/reports/evidence/{id}/download")
    suspend fun downloadEvidence(@Path("id") evidenceId: Int): Response<ResponseBody>

    // --- Communauté --------------------------------------------------------
    @GET("api/v1/community/blacklist") suspend fun blacklist(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): Response<BlacklistDto>

    @GET("api/v1/community/blacklist/export") suspend fun blacklistExport(): Response<BlacklistExportDto>

    @POST("api/v1/community/block-all") suspend fun blockAll(@Body body: BlockAllRequest): Response<BlockAllResponse>

    @POST("api/v1/community/appeals") suspend fun createAppeal(@Body body: AppealRequest): Response<AppealDto>

    @GET("api/v1/community/stats")
    suspend fun communityStats(): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    // --- Détection ---------------------------------------------------------
    @GET("api/v1/detect/signatures") suspend fun signatures(@Query("since_version") since: String? = null): Response<SignaturesDto>

    @POST("api/v1/detect/scan") suspend fun scan(@Body body: ScanRequest): Response<ScanResponse>

    @GET("api/v1/detect/hits") suspend fun hits(): Response<List<Map<String, kotlinx.serialization.json.JsonElement>>>

    @POST("api/v1/detect/hits/{id}/convert")
    suspend fun convertHit(
        @Path("id") hitId: Int,
        @Query("target_phone") targetPhone: String,
        @Query("category") category: String,
        @Query("occurred_at") occurredAt: String,
        @Query("description") description: String,
    ): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    // --- Campagnes ---------------------------------------------------------
    @POST("api/v1/campaigns/preview") suspend fun campaignPreview(@Body body: CampaignPreviewRequest): Response<CampaignPreviewDto>

    @POST("api/v1/campaigns") suspend fun createCampaign(@Body body: CampaignPreviewRequest): Response<CampaignDto>

    @GET("api/v1/campaigns") suspend fun campaigns(): Response<List<CampaignDto>>

    @POST("api/v1/campaigns/{id}/cancel") suspend fun cancelCampaign(@Path("id") id: Int): Response<CampaignDto>

    // --- Tableau de bord ---------------------------------------------------
    @GET("api/v1/dashboard/overview") suspend fun overview(): Response<OverviewDto>

    @GET("api/v1/dashboard/notifications") suspend fun notifications(): Response<List<NotificationDto>>

    @POST("api/v1/dashboard/notifications/read") suspend fun markNotificationsRead(): Response<SimpleOk>

    @GET("api/v1/dashboard/export/reports") suspend fun exportMyReports(): Response<ResponseBody>

    @GET("api/v1/dashboard/alerts") suspend fun alerts(): Response<List<AlertDto>>

    @POST("api/v1/dashboard/alerts/scan") suspend fun scanAlerts(): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    // --- Modération --------------------------------------------------------
    @GET("api/v1/moderation/queue") suspend fun moderationQueue(
        @Query("status_filter") status: String = "pending_verification",
    ): Response<List<Map<String, kotlinx.serialization.json.JsonElement>>>

    @POST("api/v1/moderation/reports/{id}/decision")
    suspend fun decideReport(@Path("id") reportId: Int, @Body body: Map<String, String>): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/v1/moderation/appeals") suspend fun moderationAppeals(): Response<List<Map<String, kotlinx.serialization.json.JsonElement>>>

    @POST("api/v1/moderation/appeals/{id}/decision")
    suspend fun decideAppeal(
        @Path("id") appealId: Int,
        @Body body: Map<String, String>,
    ): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @POST("api/v1/moderation/targets/{id}/suspension")
    suspend fun recordSuspension(
        @Path("id") targetId: Int,
        @Query("confirmed") confirmed: Boolean,
        @Query("evidence") evidence: String,
    ): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @POST("api/v1/moderation/targets/{id}/dossier")
    suspend fun buildDossier(
        @Path("id") targetId: Int,
    ): Response<Map<String, kotlinx.serialization.json.JsonElement>>

    @GET("api/v1/moderation/stats") suspend fun moderationStats(): Response<Map<String, kotlinx.serialization.json.JsonElement>>
}
