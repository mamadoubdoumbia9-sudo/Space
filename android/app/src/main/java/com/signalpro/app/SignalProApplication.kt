package com.signalpro.app

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.signalpro.app.data.local.AppDatabase
import com.signalpro.app.data.prefs.SecureStore
import com.signalpro.app.data.prefs.SettingsStore
import com.signalpro.app.data.remote.ApiClient
import com.signalpro.app.data.remote.ApiService
import com.signalpro.app.data.repo.CampaignRepository
import com.signalpro.app.data.repo.CommunityRepository
import com.signalpro.app.data.repo.DetectionRepository
import com.signalpro.app.data.repo.DeviceRepository
import com.signalpro.app.data.repo.ReportRepository
import com.signalpro.app.data.repo.AuthRepository
import com.signalpro.app.domain.ScamScanner
import com.signalpro.app.notifications.Notifier
import com.signalpro.app.work.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Conteneur de dépendances explicite (pas de framework d'injection : le graphe
 * est petit et lisible, ce qui facilite l'audit de sécurité).
 */
class AppContainer(private val context: Context) {

    val secureStore = SecureStore(context)
    val settingsStore = SettingsStore(context)
    val database: AppDatabase = AppDatabase.build(context)
    val notifier = Notifier(context)

    val apiClient = ApiClient(
        baseUrl = BuildConfig.API_BASE_URL,
        secureStore = secureStore,
        // Le jeton est rafraîchi par ApiClient ; en cas d'échec l'utilisateur
        // est déconnecté et l'UI le redirige vers l'écran de connexion.
        onSessionExpired = { secureStore.clear() },
    )
    val api: ApiService = apiClient.service

    val scanner = ScamScanner(database.signatureDao())

    // Les dépôts reçoivent le client (et non le service Retrofit nu) : c'est lui qui
    // injecte le jeton, rafraîchit la session une fois sur 401 et convertit les codes
    // d'erreur en messages exploitables.
    val authRepository = AuthRepository(apiClient, secureStore, database)
    val deviceRepository = DeviceRepository(apiClient, database, secureStore)
    val reportRepository = ReportRepository(apiClient, database, secureStore)
    val communityRepository = CommunityRepository(apiClient, database, secureStore)
    val detectionRepository = DetectionRepository(apiClient, database, scanner)
    val campaignRepository = CampaignRepository(apiClient)

    fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SyncWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class SignalProApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        container.scheduleBackgroundSync()
    }
}

/** Accès pratique au conteneur depuis les composables et les workers. */
val Context.appContainer: AppContainer
    get() = (applicationContext as SignalProApplication).container
