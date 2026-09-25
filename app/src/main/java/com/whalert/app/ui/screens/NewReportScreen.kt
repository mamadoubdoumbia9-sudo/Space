package com.whalert.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whalert.app.data.model.ReportCategory
import com.whalert.app.domain.validator.PhoneValidator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ÉCRAN 2 — NOUVEAU SIGNALEMENT
 *
 * Éléments requis :
 * - Zone numéro avec validation stricte en temps réel (E.164)
 * - Message obligatoire : "Utilisez uniquement un numéro correspondant à un compte que vous souhaitez réellement signaler."
 * - Mention honnête : "Impossible de confirmer publiquement l'état de ce compte avec les informations disponibles."
 * - Sélection catégorie (cartes modernes)
 * - Description détaillée (multiligne)
 * - Date et heure de l'incident
 * - Ajout de preuves (captures ou documents)
 * - Protection anti-multiplication (si l'utilisateur veut envoyer N fois, explication stricte)
 */
@Composable
fun NewReportScreen(
    onDossierCreated: (targetRaw: String, targetE164: String, country: String, cat: ReportCategory, date: String, desc: String, contextNotes: String) -> Unit,
    onBack: () -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ReportCategory.SCAM) }
    var descriptionInput by remember { mutableStateOf("") }
    var contextInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("1") }
    var quantityWarning by remember { mutableStateOf<String?>(null) }

    val currentDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    }
    var incidentDate by remember { mutableStateOf(currentDateStr) }

    // Validation du numéro en temps réel
    val validationResult = remember(phoneInput) {
        if (phoneInput.isBlank()) null else PhoneValidator.validate(phoneInput)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nouveau dossier de signalement", style = MaterialTheme.typography.titleMedium) },
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
            // Avertissement d'authenticité obligatoire
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Utilisez uniquement un numéro correspondant à un compte que vous souhaitez réellement signaler.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Zone Numéro
            Text("1. Numéro WhatsApp cible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Numéro au format international (ex: +33612345678)") },
                placeholder = { Text("+33 6 12 34 56 78") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                isError = validationResult != null && !validationResult.isValid,
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
            )

            // Statut de validation en temps réel
            if (validationResult != null) {
                if (validationResult.isValid) {
                    Text(
                        text = "Format valide : ${validationResult.e164Format} (Région : ${validationResult.countryCode})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Impossible de confirmer publiquement l'état de ce compte avec les informations disponibles (WhatsApp ne fournit pas d'API publique de statut).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = validationResult.errorMessage ?: "Format invalide",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Règle anti-abus : Nombre de signalements
            Text("Nombre de signalements à transmettre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = quantityInput,
                onValueChange = {
                    quantityInput = it
                    val num = it.toIntOrNull() ?: 1
                    if (num > 1) {
                        quantityWarning = "Les signalements doivent correspondre à des événements ou expériences réels. Cette application ne peut pas multiplier artificiellement un même signalement."
                    } else {
                        quantityWarning = null
                    }
                },
                label = { Text("Quantité (stricte)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (quantityWarning != null) {
                Text(
                    text = quantityWarning!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Motif / Catégorie
            Text("2. Motif du signalement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = category },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedCategory = category }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(category.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Date et heure des événements
            Text("3. Date et heure constatées", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = incidentDate,
                onValueChange = { incidentDate = it },
                label = { Text("Date et heure (AAAA-MM-JJ HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Description détaillée
            Text("4. Description factuelle des faits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = descriptionInput,
                onValueChange = { descriptionInput = it },
                label = { Text("Décrivez objectivement ce qui s'est passé") },
                placeholder = { Text("Exemple : Réception d'un message non sollicité prétendant provenir d'une banque et incitant à communiquer des coordonnées confidentielles...") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Contexte additionnel
            OutlinedTextField(
                value = contextInput,
                onValueChange = { contextInput = it },
                label = { Text("Contexte additionnel vérifié (facultatif)") },
                placeholder = { Text("Lien transmis, prétention d'identité, nom d'entreprise usurpée...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton de validation vers l'écran de récapitulatif
            val canSubmit = validationResult?.isValid == true && descriptionInput.isNotBlank() && (quantityInput == "1")

            Button(
                onClick = {
                    if (canSubmit && validationResult != null) {
                        onDossierCreated(
                            phoneInput,
                            validationResult.e164Format!!,
                            validationResult.countryCode ?: "XX",
                            selectedCategory,
                            incidentDate,
                            descriptionInput,
                            contextInput
                        )
                    }
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Checklist, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vérifier le dossier avant envoi", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
