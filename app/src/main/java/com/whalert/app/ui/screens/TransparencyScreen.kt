package com.whalert.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ÉCRAN 14 — TRANSPARENCE ABSOLUE
 *
 * Page : "Ce que cette application peut et ne peut pas faire"
 *
 * CAN :
 * - Préparer un dossier factuel et horodaté
 * - Organiser et chiffrer les preuves au repos
 * - Générer un texte formel de signalement conforme aux critères d'évaluation
 * - Demander une confirmation utilisateur explicite avant tout envoi
 * - Transmettre via un canal officiellement supporté (In-App WhatsApp, e-mail support officiel)
 * - Conserver l'historique de l'utilisateur avec masquage des données sensibles
 * - Afficher les confirmations techniques réellement reçues
 *
 * CANNOT :
 * - Garantir un bannissement ou une suspension de compte
 * - Connaître les décisions internes de WhatsApp sans source officielle
 * - Envoyer de faux signalements
 * - Multiplier artificiellement les signalements (anti-raid, anti-spam)
 * - Contourner les protections WhatsApp (CAPTCHA, reverse engineering d'API privées)
 * - Forcer WhatsApp à suspendre un compte
 */
@Composable
fun TransparencyScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transparence & Déontologie", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
            Text(
                text = "Ce que cette application PEUT et NE PEUT PAS faire",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Conformité éthique, technique et légale vis-à-vis des conditions de service WhatsApp.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Ce que l'application PEUT faire
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CE QUE L'APPLICATION PEUT FAIRE :",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val canItems = listOf(
                        "Préparer un dossier factuel, chronologique et circonstancié.",
                        "Organiser les preuves (captures d'écran, relevés de dates/heures, contexte).",
                        "Générer automatiquement un texte de signalement professionnel sans insultes ni diffamation.",
                        "Exiger une confirmation explicite de l'utilisateur avant toute transmission.",
                        "Transmettre via un canal officiellement supporté (redirection in-app WhatsApp ou e-mail officiel).",
                        "Conserver localement et de manière chiffrée (AES-256 GCM) l'état et l'historique du dossier.",
                        "Afficher honnêtement les confirmations techniques réellement obtenues."
                    )

                    canItems.forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("✔ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ce que l'application NE PEUT PAS faire
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CE QUE L'APPLICATION NE PEUT PAS FAIRE :",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val cannotItems = listOf(
                        "Garantir un bannissement ou une suspension de compte.",
                        "Connaître les décisions internes de WhatsApp sans source officielle disponible.",
                        "Envoyer de faux signalements ou générer des déclarations mensongères.",
                        "Multiplier artificiellement les signalements (fonction 100/1000 signalements interdite).",
                        "Contourner les protections, CAPTCHA ou limites de WhatsApp.",
                        "Forcer WhatsApp à suspendre un compte ou manipuler des API privées non autorisées."
                    )

                    cannotItems.forEach { item ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("✖ ", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
