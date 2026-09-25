package com.whalert.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.whalert.app.data.model.DossierStatus
import com.whalert.app.data.model.ModerationDecision
import com.whalert.app.data.model.ReportCategory
import com.whalert.app.data.model.ReportDossier
import com.whalert.app.data.model.UserConnectionState
import com.whalert.app.data.repository.ReportRepository
import com.whalert.app.data.security.SecureStorageManager
import com.whalert.app.ui.screens.*
import com.whalert.app.ui.theme.WhAlertTheme

/**
 * Activité Android principale de WhAlert.
 * Gère le routage Jetpack Compose Navigation, l'état global et les interactions de sécurité.
 */
class MainActivity : ComponentActivity() {

    private lateinit var secureStorage: SecureStorageManager
    private lateinit var reportRepository: ReportRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        secureStorage = SecureStorageManager(applicationContext)
        reportRepository = ReportRepository(secureStorage)

        setContent {
            WhAlertTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhAlertApp(
                        secureStorage = secureStorage,
                        repository = reportRepository
                    )
                }
            }
        }
    }
}

@Composable
fun WhAlertApp(
    secureStorage: SecureStorageManager,
    repository: ReportRepository
) {
    val navController = rememberNavController()

    // États observés
    var userPhone by remember { mutableStateOf(secureStorage.getUserPhone()) }
    var userEmail by remember { mutableStateOf(secureStorage.getUserEmail()) }
    var emailConsent by remember { mutableStateOf(secureStorage.hasUserEmailConsent()) }
    var connectionState by remember { mutableStateOf(secureStorage.getUserConnectionState()) }
    var dailyLimit by remember { mutableStateOf(secureStorage.getDailyLimit()) }

    var dossiersList by remember { mutableStateOf(repository.getAllDossiers()) }
    var auditLogsList by remember { mutableStateOf(repository.getAllAuditLogs()) }

    // Dossier actuellement en cours de visualisation/vérification
    var activeDossier by remember { mutableStateOf<ReportDossier?>(null) }

    fun refreshData() {
        dossiersList = repository.getAllDossiers()
        auditLogsList = repository.getAllAuditLogs()
        userPhone = secureStorage.getUserPhone()
        userEmail = secureStorage.getUserEmail()
        emailConsent = secureStorage.hasUserEmailConsent()
        connectionState = secureStorage.getUserConnectionState()
        dailyLimit = secureStorage.getDailyLimit()
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        // 1. Écran Accueil
        composable("home") {
            val (_, quotaMsg) = repository.checkDailyLimitReached()
            HomeScreen(
                userPhone = userPhone,
                connectionState = connectionState,
                remainingReportsMsg = quotaMsg,
                onNavigateNewReport = {
                    navController.navigate("new_report")
                },
                onNavigateHistory = {
                    refreshData()
                    navController.navigate("history")
                },
                onNavigateGuide = {
                    navController.navigate("guide")
                },
                onNavigateSettings = {
                    navController.navigate("settings")
                },
                onNavigateAdmin = {
                    refreshData()
                    navController.navigate("admin")
                },
                onNavigateTransparency = {
                    navController.navigate("transparency")
                }
            )
        }

        // 2. Écran Nouveau Signalement
        composable("new_report") {
            NewReportScreen(
                onDossierCreated = { targetRaw, targetE164, country, category, date, desc, contextNotes ->
                    val result = repository.createDossier(
                        targetPhoneRaw = targetRaw,
                        targetPhoneE164 = targetE164,
                        targetCountryCode = country,
                        category = category,
                        incidentTimestamp = date,
                        description = desc,
                        contextNotes = contextNotes
                    )
                    if (result.isSuccess) {
                        activeDossier = result.getOrNull()
                        refreshData()
                        navController.navigate("verification")
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 3. Écran Vérification & Confirmation obligatoire
        composable("verification") {
            activeDossier?.let { dossier ->
                VerificationScreen(
                    dossier = dossier,
                    onTransmitSuccess = { channel, technicalSuccess, details ->
                        val updated = dossier.copy(
                            status = if (technicalSuccess) DossierStatus.CONFIRMATION_OBTAINED else DossierStatus.SUBMITTED,
                            transmissionChannel = channel,
                            technicalConfirmationReceived = technicalSuccess,
                            technicalConfirmationDetails = details,
                            moderationDecision = ModerationDecision.UNKNOWN,
                            userConfirmed = true
                        )
                        repository.updateDossier(updated)
                        activeDossier = updated
                        refreshData()
                        navController.navigate("status") {
                            popUpTo("home")
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // 4. Écran État & Timeline
        composable("status") {
            activeDossier?.let { dossier ->
                StatusScreen(
                    dossier = dossier,
                    onBackToHome = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    },
                    onViewAllDossiers = {
                        refreshData()
                        navController.navigate("history")
                    }
                )
            }
        }

        // 5. Écran Historique
        composable("history") {
            HistoryScreen(
                dossiers = dossiersList,
                onSelectDossier = { selected ->
                    activeDossier = selected
                    navController.navigate("status")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 6. Écran Guide de sécurité
        composable("guide") {
            GuideScreen(onBack = { navController.popBackStack() })
        }

        // 7. Écran Paramètres
        composable("settings") {
            SettingsScreen(
                currentPhone = userPhone,
                currentEmail = userEmail,
                currentEmailConsent = emailConsent,
                connectionState = connectionState,
                dailyLimit = dailyLimit,
                onSaveUserCredentials = { phone, email, consent ->
                    secureStorage.saveUserPhone(phone)
                    secureStorage.saveUserEmail(email, consent)
                    refreshData()
                },
                onDisconnectUser = {
                    secureStorage.disconnectUser()
                    refreshData()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 8. Console Privée (Admin)
        composable("admin") {
            AdminConsoleScreen(
                dossiers = dossiersList,
                auditLogs = auditLogsList,
                dailyLimit = dailyLimit,
                onUpdateDailyLimit = { newLimit ->
                    secureStorage.setDailyLimit(newLimit)
                    refreshData()
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 9. Page de transparence
        composable("transparency") {
            TransparencyScreen(onBack = { navController.popBackStack() })
        }
    }
}
