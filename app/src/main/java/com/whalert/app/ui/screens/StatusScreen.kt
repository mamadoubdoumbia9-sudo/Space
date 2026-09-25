package com.whalert.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whalert.app.data.model.ReportDossier
import com.whalert.app.ui.components.DossierTimeline
import com.whalert.app.ui.components.TransparencyNoticeBanner

/**
 * ÉCRAN 4 — ÉTAT DU DOSSIER & TIMELINE
 *
 * Utilise une timeline :
 * Préparation -> Signalement transmis -> Confirmation disponible / indisponible -> Suivi
 *
 * Distingue rigoureusement :
 * - "Signalement envoyé depuis cette application"
 * - "Signalement transmis au canal officiel"
 * - "Aucune confirmation disponible"
 * - "Décision de WhatsApp inconnue"
 *
 * Ne prétend JAMAIS savoir qu'un compte a été suspendu sans source officielle.
 */
@Composable
fun StatusScreen(
    dossier: ReportDossier,
    onBackToHome: () -> Unit,
    onViewAllDossiers: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suivi du dossier ${dossier.id}", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.Home, contentDescription = "Accueil")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Statut de transmission
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Signalement transmis au canal officiel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (dossier.technicalConfirmationReceived)
                                "Le message a été remis au service configuré."
                            else
                                "Redirection effectuée vers l'interface officielle WhatsApp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Avertissement de modération WhatsApp
            TransparencyNoticeBanner(
                text = "WhatsApp ne fournit pas de confirmation publique permettant à cette application de vérifier directement si ce compte a été suspendu.",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timeline visuelle
            DossierTimeline(
                status = dossier.status,
                moderationDecision = dossier.moderationDecision,
                technicalConfirmation = dossier.technicalConfirmationReceived
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cartouche explicatif sur les décisions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Statut de modération : ${dossier.moderationDecision.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Un signalement auprès de WhatsApp initie une analyse par leurs systèmes internes et équipes spécialisées. Aucun tiers n'a accès à leurs décisions internes en temps réel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBackToHome,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accueil")
                }

                Button(
                    onClick = onViewAllDossiers,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Historique")
                }
            }
        }
    }
}
