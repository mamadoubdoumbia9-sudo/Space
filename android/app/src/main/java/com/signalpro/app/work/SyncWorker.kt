package com.signalpro.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.signalpro.app.appContainer
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.ReportCreateRequest
import kotlinx.coroutines.flow.first

/**
 * Synchronisation de fond : réellement utile et strictement encadrée.
 *
 * Ce qu'elle fait :
 *  1. rafraîchit la liste de mes signalements ;
 *  2. télécharge les nouvelles signatures de détection (analyse hors ligne) ;
 *  3. rafraîchit la liste communautaire et les alertes de contact malveillant ;
 *  4. tente d'envoyer les brouillons créés hors ligne — UNIQUEMENT ceux que
 *     l'utilisateur a explicitement marqués « en attente d'envoi ».
 *
 * Ce qu'elle ne fait jamais : créer ou transmettre un signalement que
 * l'utilisateur n'a pas lui-même enregistré, ni contourner une limite serveur.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (container.secureStore.accessToken.isNullOrBlank()) {
            return Result.success() // rien à synchroniser : utilisateur déconnecté
        }

        val wifiOnly = container.settingsStore.wifiOnlySync.first()
        if (wifiOnly && !isOnWifi()) {
            return Result.retry()
        }

        var failures = 0

        when (val reports = container.reportRepository.refresh()) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> {
                failures++
                if (reports.error.isAuth) return Result.success()
            }
        }

        val currentVersion = container.settingsStore.currentSignatureVersion()
        when (val signatures = container.detectionRepository.refreshSignatures(currentVersion)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> failures++
        }
        container.database.signatureDao().currentVersion()?.let { version ->
            container.settingsStore.setSignatureVersion(version)
        }

        when (container.communityRepository.refreshBlacklist()) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> failures++
        }

        val alertsBefore = container.communityRepository.observeAlerts().first().map { it.targetId }.toSet()
        val alertsResult = container.communityRepository.refreshAlerts()
        if (alertsResult is ApiResult.Success) {
            container.communityRepository.observeAlerts().first()
                .filter { it.targetId !in alertsBefore }
                .forEach { alert ->
                    container.notifier.notifyMaliciousContact(
                        targetId = alert.targetId,
                        maskedPhone = alert.phoneMasked,
                        category = alert.categoryLabel,
                        reportCount = alert.verifiedReports,
                    )
                }
        } else {
            failures++
        }

        val pending = container.reportRepository.pendingDrafts()
        var synced = 0
        pending.forEach { draft ->
            val result = container.reportRepository.create(
                ReportCreateRequest(
                    targetPhone = draft.targetPhone,
                    category = draft.category,
                    occurredAt = draft.occurredAt,
                    description = draft.description,
                    messageIds = parseIds(draft.messageIds),
                ),
            )
            when (result) {
                is ApiResult.Success -> {
                    container.reportRepository.deleteDraft(draft.localId)
                    synced++
                    container.notifier.notifyReportUpdate(result.data.publicRef, "enregistré hors ligne puis transmis")
                }
                is ApiResult.Failure -> {
                    if (result.error.isAuth) return Result.success()
                    container.reportRepository.markDraftFailure(draft.localId, result.error.message)
                }
            }
        }

        container.notifier.notifySyncSummary(
            pending = container.reportRepository.pendingDrafts().size,
            synced = synced,
        )
        // Un échec réseau déclenche une nouvelle tentative (backoff exponentiel de WorkManager).
        return if (failures == 0) Result.success() else Result.retry()
    }

    private fun parseIds(raw: String): List<String> =
        raw.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }

    private fun isOnWifi(): Boolean {
        val cm = applicationContext.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        const val NAME = "signalpro-sync"
    }
}
