package com.whalert.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whalert.app.data.model.ReportDossier
import com.whalert.app.ui.components.TransparencyNoticeBanner

/**
 * ÉCRAN 3 — VÉRIFICATION & CONFIRMATION OBLIGATOIRE
 *
 * Éléments requis :
 * - Résumé complet : Numéro, Motif, Description factuelle, Pièces jointes
 * - Message obligatoire : "Cette application ne peut pas garantir une suspension du compte. La décision appartient à WhatsApp."
 * - Boîte de dialogue de confirmation explicite :
 *   "Vous êtes sur le point de transmettre un signalement réel."
 *   Question : "Confirmez-vous l'envoi ?"
 *   Boutons : [NON, ANNULER] [OUI, CONTINUER]
 * - Aperçu du texte formel généré avec boutons [MODIFIER] [COPIER] [TRANSMETTRE]
 * - Choix du canal officiel :
 *   1. Redirection officielle in-app WhatsApp (Mécanisme recommandé par WhatsApp)
 *   2. Envoi par e-mail officiel documentaire (uniquement si service e-mail configuré)
 */
@Composable
fun VerificationScreen(
    dossier: ReportDossier,
    onTransmitSuccess: (channel: String, technicalSuccess: Boolean, details: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf("WHATSAPP_OFFICIAL") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vérification du dossier", style = MaterialTheme.typography.titleMedium) },
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
            // Avertissement légal obligatoire
            TransparencyNoticeBanner(
                text = "Cette application ne peut pas garantir une suspension du compte. La décision de modération appartient exclusivement à WhatsApp.",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Carte de récapitulatif
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Récapitulatif du signalement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SummaryRow(label = "Numéro concerné :", value = dossier.targetPhoneE164)
                    SummaryRow(label = "Motif :", value = dossier.category.label)
                    SummaryRow(label = "Date constatée :", value = dossier.incidentTimestamp)
                    SummaryRow(
                        label = "Pièces jointes :",
                        value = if (dossier.evidenceList.isEmpty()) "Aucun fichier joint" else "${dossier.evidenceList.size} élément(s)"
                    )
                    SummaryRow(label = "Description :", value = dossier.description)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canal officiel de transmission
            Text("Canal officiel de transmission", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Option 1 : Mécanisme officiel WhatsApp In-App (recommandé par WhatsApp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedChannel == "WHATSAPP_OFFICIAL")
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = { selectedChannel = "WHATSAPP_OFFICIAL" }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedChannel == "WHATSAPP_OFFICIAL",
                        onClick = { selectedChannel = "WHATSAPP_OFFICIAL" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Mécanisme officiel WhatsApp", fontWeight = FontWeight.Bold)
                        Text(
                            "Ouvre WhatsApp directement pour signaler le contact via les options officielles de l'application.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option 2 : Support e-mail documentaire officiel
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedChannel == "EMAIL_SUPPORT")
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = { selectedChannel = "EMAIL_SUPPORT" }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedChannel == "EMAIL_SUPPORT",
                        onClick = { selectedChannel = "EMAIL_SUPPORT" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("E-mail documentaire officiel", fontWeight = FontWeight.Bold)
                        Text(
                            "Transmet le dossier vers android_web@support.whatsapp.com (processus externe documentaire).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Aperçu obligatoire du texte formel généré
            Text("Texte formel généré", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = dossier.generatedFormalText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 14
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Actions sur le texte : Copier / Modifier
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("WhAlert Report", dossier.generatedFormalText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Texte du dossier copié dans le presse-papier", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copier")
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modifier")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton principal pour engager la confirmation obligatoire
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Continuer vers le signalement officiel", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialogue de confirmation explicite obligatoire
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transmission d'un signalement réel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Vous êtes sur le point de transmettre un signalement réel auprès du canal officiel sélectionné.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Numéro ciblé : ${dossier.targetPhoneE164}\n• Motif : ${dossier.category.label}\n• Canal : $selectedChannel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Confirmez-vous l'envoi ?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        // Exécution de l'action réelle sur le canal officiel
                        if (selectedChannel == "WHATSAPP_OFFICIAL") {
                            try {
                                // Ouvre l'URL officielle whatsapp vers le contact pour permettre le signalement direct
                                val cleanPhone = dossier.targetPhoneE164.replace("+", "")
                                val waIntent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                                }
                                context.startActivity(waIntent)
                                onTransmitSuccess(
                                    "WHATSAPP_OFFICIAL",
                                    true,
                                    "Redirection réussie vers l'application WhatsApp pour signalement in-app officiel."
                                )
                            } catch (e: Exception) {
                                onTransmitSuccess(
                                    "WHATSAPP_OFFICIAL",
                                    false,
                                    "WhatsApp n'a pas pu être ouvert directement : ${e.message}"
                                )
                            }
                        } else {
                            try {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:android_web@support.whatsapp.com")
                                    putExtra(Intent.EXTRA_SUBJECT, "Signalement de compte suspect [${dossier.targetPhoneE164}]")
                                    putExtra(Intent.EXTRA_TEXT, dossier.generatedFormalText)
                                }
                                context.startActivity(emailIntent)
                                onTransmitSuccess(
                                    "EMAIL_SUPPORT",
                                    true,
                                    "Le message a été remis au client d'e-mail officiel configuré sur l'appareil."
                                )
                            } catch (e: Exception) {
                                onTransmitSuccess(
                                    "EMAIL_SUPPORT",
                                    false,
                                    "L'envoi par e-mail n'a pas abouti : aucun client e-mail configuré."
                                )
                            }
                        }
                    }
                ) {
                    Text("OUI, CONTINUER")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("NON, ANNULER")
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
