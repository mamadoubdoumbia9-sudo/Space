package com.signalpro.app.data.repo

import com.signalpro.app.data.local.AppDatabase
import com.signalpro.app.data.local.CachedAlert
import com.signalpro.app.data.local.CachedBlacklistEntry
import com.signalpro.app.data.local.CachedReport
import com.signalpro.app.data.local.CachedSignature
import com.signalpro.app.data.local.PendingReport
import com.signalpro.app.data.local.ReportMapper
import com.signalpro.app.data.prefs.SecureStore
import com.signalpro.app.data.remote.ApiClient
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.AppealRequest
import com.signalpro.app.data.remote.BlockAllRequest
import com.signalpro.app.data.remote.BusinessLinkRequest
import com.signalpro.app.data.remote.CampaignDto
import com.signalpro.app.data.remote.CampaignPreviewDto
import com.signalpro.app.data.remote.CampaignPreviewRequest
import com.signalpro.app.data.remote.ChangePasswordRequest
import com.signalpro.app.data.remote.DeleteAccountRequest
import com.signalpro.app.data.remote.DeviceDto
import com.signalpro.app.data.remote.LinkStartRequest
import com.signalpro.app.data.remote.LinkStartResponse
import com.signalpro.app.data.remote.LoginRequest
import com.signalpro.app.data.remote.RegisterRequest
import com.signalpro.app.data.remote.RegisterResponse
import com.signalpro.app.data.remote.ReportCreateRequest
import com.signalpro.app.data.remote.ReportDto
import com.signalpro.app.data.remote.SubmitResponse
import com.signalpro.app.data.remote.TokenResponse
import com.signalpro.app.data.remote.UserDto
import com.signalpro.app.data.remote.VerifyRequest
import com.signalpro.app.domain.ScamScanner
import com.signalpro.app.domain.Validation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private const val TEXT_PLAIN = "text/plain"

data class SessionState(
    val authenticated: Boolean,
    val isModerator: Boolean,
    val displayName: String,
)

class AuthRepository(
    private val apiClient: ApiClient,
    private val secureStore: SecureStore,
    private val database: AppDatabase,
) {
    private val api = apiClient.service

    suspend fun restoreSession(): SessionState {
        if (secureStore.accessToken == null) {
            return SessionState(false, false, "")
        }
        return when (val result = apiClient.call { api.me() }) {
            is ApiResult.Success -> {
                secureStore.isModerator = result.data.role != "user"
                secureStore.displayName = result.data.displayName
                secureStore.userId = result.data.id
                SessionState(result.data.isVerified, result.data.role != "user", result.data.displayName)
            }
            is ApiResult.Failure -> {
                // Jeton invalide ou serveur injoignable : on ne prétend pas être connecté.
                if (result.error.isAuth) secureStore.clear()
                SessionState(false, false, "")
            }
        }
    }

    suspend fun register(request: RegisterRequest): ApiResult<RegisterResponse> {
        val result = apiClient.call { api.register(request) }
        if (result is ApiResult.Success) {
            secureStore.displayName = request.displayName.ifBlank { request.email.substringBefore('@') }
        }
        return result
    }

    suspend fun verify(email: String, code: String): ApiResult<TokenResponse> {
        val result = apiClient.call { api.verify(VerifyRequest(email, code)) }
        if (result is ApiResult.Success) {
            secureStore.saveTokens(result.data.accessToken, result.data.refreshToken)
            loadProfile()
        }
        return result
    }

    suspend fun resendCode(email: String, password: String) =
        apiClient.call { api.resendCode(com.signalpro.app.data.remote.ResendCodeRequest(email, password)) }

    suspend fun login(email: String, password: String): ApiResult<TokenResponse> {
        val result = apiClient.call { api.login(LoginRequest(email, password)) }
        if (result is ApiResult.Success) {
            secureStore.saveTokens(result.data.accessToken, result.data.refreshToken)
            loadProfile()
        }
        return result
    }

    suspend fun loadProfile(): UserDto? = when (val result = apiClient.call { api.me() }) {
        is ApiResult.Success -> {
            secureStore.isModerator = result.data.role != "user"
            secureStore.displayName = result.data.displayName
            secureStore.userId = result.data.id
            result.data
        }
        is ApiResult.Failure -> null
    }

    suspend fun limits() = apiClient.call { api.limits() }

    suspend fun changePassword(current: String, new: String) =
        apiClient.call { api.changePassword(ChangePasswordRequest(current, new)) }

    suspend fun deleteAccount(password: String): ApiResult<Map<String, kotlinx.serialization.json.JsonElement>> {
        val result = apiClient.call { api.deleteAccount(DeleteAccountRequest(password, "SUPPRIMER")) }
        if (result is ApiResult.Success) {
            // Purge locale : rien ne doit subsister sur l'appareil.
            database.clearAllTables()
            secureStore.clear()
        }
        return result
    }

    fun logout() = secureStore.clear()
}

class DeviceRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val secureStore: SecureStore,
) {
    private val api = apiClient.service

    suspend fun gatewayStatus() = apiClient.call { api.gatewayStatus() }

    suspend fun devices() = apiClient.call { api.devices() }

    suspend fun startLink(label: String, consentVersion: String): ApiResult<LinkStartResponse> =
        apiClient.call { api.linkStart(LinkStartRequest(label = label, riskConsent = true, consentVersion = consentVersion)) }

    suspend fun confirmLink(deviceId: Int): ApiResult<DeviceDto> {
        val result = apiClient.call { api.linkConfirm(mapOf("device_id" to deviceId)) }
        // Seule une confirmation réelle côté passerelle renvoie un appareil connecté.
        if (result is ApiResult.Success) {
            apiClient.call { api.syncDevice(result.data.id) }
        }
        return result
    }

    suspend fun syncContacts(deviceId: Int) = apiClient.call { api.syncDevice(deviceId) }

    suspend fun refresh(deviceId: Int) = apiClient.call { api.refreshDevice(deviceId) }

    suspend fun revoke(deviceId: Int) = apiClient.call { api.revokeDevice(deviceId) }

    suspend fun reportSuspension(targetPhone: String, note: String) =
        apiClient.call { api.reportSuspension(targetPhone, note) }

    suspend fun connectedDevice(): DeviceDto? =
        (devices() as? ApiResult.Success)?.data?.firstOrNull { it.status == "connected" }

    /** Liaison d'un compte professionnel (WhatsApp Business Platform, API officielle). */
    suspend fun linkBusiness(wabaId: String, phoneNumberId: String, accessToken: String) =
        apiClient.call { api.linkBusiness(BusinessLinkRequest(wabaId, phoneNumberId, accessToken)) }

    suspend fun businessStatus() = apiClient.call { api.businessStatus() }
}

class ReportRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val secureStore: SecureStore,
) {
    private val api = apiClient.service

    fun observeCachedReports(): Flow<List<CachedReport>> = database.reportDao().observeAll()

    fun observePendingCount(): Flow<Int> = database.pendingReportDao().countFlow()

    suspend fun refresh(): ApiResult<Int> =
        when (val result = apiClient.call { api.reports(limit = 100) }) {
            is ApiResult.Success -> {
                database.reportDao().upsertAll(result.data.items.map(ReportMapper::toCached))
                ApiResult.Success(result.data.items.size)
            }
            // `ApiResult.Failure` est un `ApiResult<Nothing>` : l'échec est donc
            // réutilisable tel quel, sans inventer un succès.
            is ApiResult.Failure -> result
        }

    suspend fun detail(reportId: Int): ApiResult<ReportDto> {
        val result = apiClient.call { api.report(reportId) }
        if (result is ApiResult.Success) database.reportDao().upsert(ReportMapper.toCached(result.data))
        return result
    }

    suspend fun usage() = apiClient.call { api.usage() }

    suspend fun create(request: ReportCreateRequest): ApiResult<ReportDto> {
        val result = apiClient.call { api.createReport(request) }
        if (result is ApiResult.Success) database.reportDao().upsert(ReportMapper.toCached(result.data))
        return result
    }

    /** Enregistre un brouillon hors ligne : rien n'est envoyé sans décision de l'utilisateur. */
    suspend fun queueOffline(draft: PendingReport): Long = database.pendingReportDao().insert(draft)

    suspend fun pendingDrafts(): List<PendingReport> = database.pendingReportDao().all()

    suspend fun deleteDraft(id: Long) = database.pendingReportDao().delete(id)

    suspend fun markDraftFailure(id: Long, error: String) = database.pendingReportDao().markFailure(id, error)

    suspend fun attachEvidencePath(draftId: Long, paths: String) =
        database.pendingReportDao().updateEvidence(draftId, paths)

    suspend fun uploadEvidence(reportId: Int, kind: String, file: File): ApiResult<com.signalpro.app.data.remote.EvidenceDto> {
        val part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(kindToMediaType(kind)),
        )
        return apiClient.call { api.uploadEvidence(reportId, kind.toRequestBody(TEXT_PLAIN.toMediaType()), part) }
    }

    suspend fun submit(reportId: Int, adapter: String, deviceId: Int?): ApiResult<SubmitResponse> =
        apiClient.call {
            api.submitReport(
                reportId = reportId,
                adapter = adapter.toRequestBody(TEXT_PLAIN.toMediaType()),
                deviceId = deviceId?.toString()?.toRequestBody(TEXT_PLAIN.toMediaType()),
                manualAck = "true".toRequestBody(TEXT_PLAIN.toMediaType()),
            )
        }

    suspend fun markManualDone(reportId: Int, note: String) = apiClient.call {
        api.markManualDone(
            reportId = reportId,
            done = "true".toRequestBody(TEXT_PLAIN.toMediaType()),
            note = note.toRequestBody(TEXT_PLAIN.toMediaType()),
        )
    }

    suspend fun exportCsv(): ApiResult<String> {
        val result = apiClient.call { api.exportMyReports() }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.string())
            is ApiResult.Failure -> result
        }
    }

    suspend fun evidenceBytes(evidenceId: Int): ApiResult<ByteArray> {
        val result = apiClient.call { api.downloadEvidence(evidenceId) }
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.bytes())
            is ApiResult.Failure -> result
        }
    }

    suspend fun importPreview(file: File) = apiClient.call {
        api.importPreview(MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("text/csv".toMediaType())))
    }

    suspend fun importCommit(file: File) = apiClient.call {
        api.importCommit(MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("text/csv".toMediaType())))
    }

    private fun kindToMediaType(kind: String): okhttp3.MediaType = when (kind) {
        "screenshot" -> "image/*"
        "chat_export" -> "text/plain"
        "header_dump" -> "application/json"
        else -> "application/octet-stream"
    }.toMediaType()
}

class CommunityRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val secureStore: SecureStore,
) {
    private val api = apiClient.service

    fun observeCachedBlacklist() = database.communityDao().observeBlacklist()
    fun observeAlerts() = database.communityDao().observeAlerts()

    suspend fun refreshBlacklist(): ApiResult<Int> =
        when (val result = apiClient.call { api.blacklist(limit = 300) }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                database.communityDao().upsertBlacklist(
                    result.data.items.map {
                        CachedBlacklistEntry(
                            phoneMasked = it.phoneMasked,
                            categoryLabel = it.categoryLabel,
                            verifiedReports = it.verifiedReports,
                            distinctReporters = it.distinctReporters,
                            suspensionStatus = it.suspensionStatus,
                            updatedAt = now,
                        )
                    },
                )
                ApiResult.Success(result.data.items.size)
            }
            is ApiResult.Failure -> result
        }

    suspend fun exportNumbers() = apiClient.call { api.blacklistExport() }

    suspend fun blockAll(deviceId: Int, max: Int) =
        apiClient.call { api.blockAll(BlockAllRequest(deviceId = deviceId, maxNumbers = max, consentAck = true)) }

    suspend fun createAppeal(request: AppealRequest) = apiClient.call { api.createAppeal(request) }

    suspend fun stats() = apiClient.call { api.communityStats() }

    suspend fun refreshAlerts(): ApiResult<Int> =
        when (val result = apiClient.call { api.alerts() }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                database.communityDao().upsertAlerts(
                    result.data.map {
                        CachedAlert(
                            targetId = it.targetId,
                            phoneMasked = it.phoneMasked,
                            categoryLabel = it.categoryLabel,
                            verifiedReports = it.verifiedReports,
                            advice = it.advice,
                            updatedAt = now,
                        )
                    },
                )
                ApiResult.Success(result.data.size)
            }
            is ApiResult.Failure -> result
        }

    suspend fun scanAlerts() = apiClient.call { api.scanAlerts() }
}

class DetectionRepository(
    private val apiClient: ApiClient,
    private val database: AppDatabase,
    private val scanner: ScamScanner,
) {
    private val api = apiClient.service

    suspend fun signaturesCount(): Int = database.signatureDao().count()

    /** Télécharge les signatures et les stocke pour l'analyse hors ligne. */
    suspend fun refreshSignatures(currentVersion: String): ApiResult<Int> {
        val result = apiClient.call { api.signatures(since = currentVersion.ifBlank { null }) }
        return when (result) {
            is ApiResult.Success -> {
                val payload = result.data
                if (payload.signatures.isNotEmpty()) {
                    database.signatureDao().upsertAll(
                        payload.signatures.map {
                            CachedSignature(
                                key = "${payload.version}:${it.kind}:${it.valueDisplay.hashCode()}",
                                kind = it.kind,
                                value = it.valueDisplay,
                                severity = it.severity,
                                category = it.category,
                                version = payload.version,
                            )
                        },
                    )
                    database.signatureDao().pruneOldVersions(payload.version)
                }
                ApiResult.Success(payload.count)
            }
            is ApiResult.Failure -> result
        }
    }

    /** Analyse hors ligne d'un extrait fourni volontairement par l'utilisateur. */
    suspend fun scanLocal(text: String): ScamScanner.ScanResult = withContext(Dispatchers.Default) {
        val signatures = database.signatureDao().all().map { Validation.toSignature(it.kind, it.value, it.severity, it.category) }
        scanner.scan(text, signatures)
    }

    /** Analyse serveur (facultative) d'un extrait, si l'utilisateur le décide. */
    suspend fun scanServer(text: String, peerPhone: String?, deviceId: Int?) =
        apiClient.call { api.scan(com.signalpro.app.data.remote.ScanRequest(text, peerPhone, deviceId)) }

    suspend fun hits() = apiClient.call { api.hits() }

    /** Convertit une détection en signalement : le serveur réapplique toutes les règles. */
    suspend fun convertHit(hitId: Int, targetPhone: String, category: String, occurredAt: String, description: String) =
        apiClient.call { api.convertHit(hitId, targetPhone, category, occurredAt, description) }
}

class CampaignRepository(private val apiClient: ApiClient) {
    private val api = apiClient.service

    suspend fun preview(request: CampaignPreviewRequest): ApiResult<CampaignPreviewDto> =
        apiClient.call { api.campaignPreview(request) }

    suspend fun create(request: CampaignPreviewRequest): ApiResult<CampaignDto> =
        apiClient.call { api.createCampaign(request) }

    suspend fun list() = apiClient.call { api.campaigns() }

    suspend fun cancel(id: Int) = apiClient.call { api.cancelCampaign(id) }
}
