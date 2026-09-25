package com.signalpro.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.signalpro.app.AppContainer
import com.signalpro.app.appContainer
import com.signalpro.app.core.domain.Disclaimer
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Palette ---------------------------------------------------------------
val Primary = Color(0xFF0F2A3D)
val Accent = Color(0xFF0E7C66)
val Danger = Color(0xFFB3261E)
val Warning = Color(0xFF8A6D00)
val SurfaceTint = Color(0xFFEDF2F5)

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    error = Danger,
    background = Color(0xFFF7F9FA),
    surface = Color.White,
    surfaceVariant = SurfaceTint,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FB6CC),
    secondary = Color(0xFF64D3B4),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SignalProTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = Accent, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("SignalPro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Vérification de votre session…",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator()
        }
    }
}

// --- Composants partagés ---------------------------------------------------

/**
 * Avertissement de conformité, affiché sur l'accueil ET avant chaque envoi.
 * Il ne peut pas être fermé définitivement : c'est une condition d'usage.
 */
@Composable
fun DisclaimerBanner(compact: Boolean = false, full: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    text = if (compact) "Avertissement" else "À lire avant tout signalement",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = Warning,
                )
                Text(
                    text = if (full) Disclaimer.LONG else Disclaimer.SHORT,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4A3B00),
                )
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String, tint: Color = Accent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Conteneur d'écran défilant, marges et espacement homogènes. */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun LoadingBlock(message: String = "Traitement en cours…") {
    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    val animated by animateColorAsState(targetValue = color, label = "status")
    Box(
        modifier = Modifier
            .background(animated.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = animated, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val cleaned = raw.substringBefore('.').removeSuffix("Z")
    return runCatching {
        val date: Date? = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE).parse(cleaned)
        if (date == null) raw else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(date)
    }.getOrElse { raw }
}

fun statusColor(status: String): Color = when (status) {
    "verified", "sent", "confirmed_suspended", "suspended", "connected" -> Accent
    "rejected", "rejected_abusive", "revoked", "banned" -> Danger
    "pending_verification", "queued", "submitted", "manual_pending", "pending", "disconnected" -> Warning
    else -> Primary
}

/** Collecte d'un StateFlow liée au cycle de vie de l'écran. */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsStateWithLifecycle()

// --- Fabrique de ViewModels ------------------------------------------------
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    noinline factory: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.appContainer
    return viewModel<VM>(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                factory(container) as T
        },
    )
}
