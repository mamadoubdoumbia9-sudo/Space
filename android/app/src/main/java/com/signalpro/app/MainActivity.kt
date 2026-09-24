package com.signalpro.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.signalpro.app.ui.AppNavHost
import com.signalpro.app.ui.SignalProTheme
import com.signalpro.app.ui.SplashScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingSharedText: String? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
    }
    private var cameraGranted by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refusé : pas de notification */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingSharedText = extractSharedText(intent)

        // Les permissions sont demandées au moment utile, jamais au démarrage en masse :
        // la notification sert aux alertes de contact malveillant, la caméra n'est
        // demandée que lorsque l'utilisateur ouvre l'écran de liaison WhatsApp.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = applicationContext.appContainer
        setContent {
            SignalProTheme {
                var bootstrapped by remember { mutableStateOf(false) }
                var loggedIn by remember { mutableStateOf(false) }
                var isModerator by remember { mutableStateOf(false) }
                var displayName by remember { mutableStateOf("") }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val session = container.authRepository.restoreSession()
                    loggedIn = session.authenticated
                    isModerator = session.isModerator
                    displayName = session.displayName
                    bootstrapped = true
                }

                if (!bootstrapped) {
                    SplashScreen()
                } else {
                    AppNavHost(
                        container = container,
                        startLoggedIn = loggedIn,
                        isModerator = isModerator,
                        displayName = displayName,
                        cameraGranted = cameraGranted,
                        requestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
                        sharedText = pendingSharedText,
                        onConsumeSharedText = { pendingSharedText = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSharedText = extractSharedText(intent)
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.take(20_000)
    }
}
