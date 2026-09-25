package com.whalert.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whalert.app.data.model.UserConnectionState
import com.whalert.app.ui.components.TransparencyNoticeBanner

/**
 * ÉCRAN 1 — ACCUEIL
 * Comprend : Logo, nom, explication, bouton "Signaler un compte", "Mes signalements", "Guide de sécurité", "Paramètres".
 * Animation d'apparition progressive et microanimations fluides.
 */
@Composable
fun HomeScreen(
    userPhone: String?,
    connectionState: UserConnectionState,
    remainingReportsMsg: String,
    onNavigateNewReport: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateGuide: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateAdmin: () -> Unit,
    onNavigateTransparency: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 40 })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo & Badge
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = "Logo WhAlert",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "WhAlert",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Documentation légitime & signalement de comptes suspects",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Statut de connexion officiel
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = when (connectionState) {
                    UserConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                    UserConnectionState.NOT_CONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionState == UserConnectionState.CONNECTED)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Statut : ${connectionState.label}" + (if (userPhone != null) " ($userPhone)" else ""),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quota de sécurité info
            TransparencyNoticeBanner(
                text = remainingReportsMsg,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Menu principal sous forme de cartes d'action
            ActionMenuCard(
                title = "Signaler un compte suspect",
                subtitle = "Constituer un dossier circonstancié et préparer la transmission officielle",
                icon = Icons.Default.AddAlert,
                isPrimary = true,
                onClick = onNavigateNewReport
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionMenuCard(
                title = "Mes signalements",
                subtitle = "Consulter l'historique de vos dossiers et les preuves conservées",
                icon = Icons.Default.Folder,
                onClick = onNavigateHistory
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionMenuCard(
                title = "Guide de sécurité",
                subtitle = "Reconnaître les fraudes, arnaques et préserver légalement les preuves",
                icon = Icons.Default.MenuBook,
                onClick = onNavigateGuide
            )

            Spacer(modifier = Modifier.height(12.dp))

            ActionMenuCard(
                title = "Paramètres & Compte",
                subtitle = "Configuration de votre numéro, e-mail et diagnostics réseau",
                icon = Icons.Default.Settings,
                onClick = onNavigateSettings
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Liens footer vers Transparence et Console Privée
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onNavigateTransparency) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ce que l'application peut / ne peut pas faire", fontSize = 12.sp)
                }
                TextButton(onClick = onNavigateAdmin) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Console Privée", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        shape = RoundedCornerShape(16.dp),
        colors = if (isPrimary) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPrimary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
