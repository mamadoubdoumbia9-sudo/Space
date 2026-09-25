package com.whalert.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.whalert.app.data.model.UserConnectionState
import com.whalert.app.data.network.NetworkClient
import com.whalert.app.domain.validator.PhoneValidator
import kotlinx.coroutines.launch

/**
 * ÉCRAN PARAMÈTRES & CONNEXION DU COMPTE
 *
 * ÉTAPE 1 — Connexion du compte utilisateur :
 * - Configurer son propre numéro WhatsApp (validation E.164 stricte)
 * - Ne jamais demander ni stocker de code SMS/OTP WhatsApp, PIN à deux facteurs ou mot de passe
 * - Associer une adresse e-mail de contact documentaire (avec consentement explicite)
 * - Affichage de l'état : CONNECTÉ, NON CONNECTÉ, SESSION EXPIRÉE, ERREUR DE CONNEXION
 * - Test réel de connectivité Internet via HTTPS/TLS
 */
@Composable
fun SettingsScreen(
    currentPhone: String?,
    currentEmail: String?,
    currentEmailConsent: Boolean,
    connectionState: UserConnectionState,
    dailyLimit: Int,
    onSaveUserCredentials: (phone: String, email: String, consent: Boolean) -> Unit,
    onDisconnectUser: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var phoneInput by remember { mutableStateOf(currentPhone ?: "") }
    var emailInput by remember { mutableStateOf(currentEmail ?: "") }
    var emailConsent by remember { mutableStateOf(currentEmailConsent) }

    var networkTestStatus by remember { mutableStateOf<String?>(null) }
    var isTestingNetwork by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres & Compte utilisateur", style = MaterialTheme.typography.titleMedium) },
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
            // État actuel du compte
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "État du profil utilisateur",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (connectionState == UserConnectionState.CONNECTED)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = connectionState.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = if (connectionState == UserConnectionState.CONNECTED)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sécurité stricte : Cette application n'exige ni ne stocke AUCUN code SMS/OTP, mot de passe ou jeton privé.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Configuration du numéro propre
            Text("1. Votre numéro WhatsApp d'identification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Votre numéro (format international E.164)") },
                placeholder = { Text("+33612345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Configuration de l'e-mail
            Text("2. E-mail de contact documentaire (facultatif)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Adresse e-mail pour l'envoi de dossiers") },
                placeholder = { Text("mon-email@domaine.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.EmailAddress),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = emailConsent,
                    onCheckedChange = { emailConsent = it }
                )
                Text(
                    text = "J'autorise l'application à utiliser cette adresse comme expéditeur lors de l'envoi documentaire officiel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val valRes = PhoneValidator.validate(phoneInput)
                    if (!valRes.isValid) {
                        Toast.makeText(context, "Numéro invalide : ${valRes.errorMessage}", Toast.LENGTH_LONG).show()
                    } else {
                        onSaveUserCredentials(valRes.e164Format!!, emailInput.trim(), emailConsent)
                        Toast.makeText(context, "Profil utilisateur enregistré avec succès.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enregistrer les paramètres")
            }

            if (connectionState == UserConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        onDisconnectUser()
                        phoneInput = ""
                        emailInput = ""
                        emailConsent = false
                        Toast.makeText(context, "Compte déconnecté.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dissocier mon profil")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Test de connectivité HTTPS réelle
            Text("Diagnostic réseau HTTPS / TLS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    isTestingNetwork = true
                    networkTestStatus = "Test HTTPS en cours..."
                    coroutineScope.launch {
                        val res = NetworkClient.testInternetReachability()
                        isTestingNetwork = false
                        if (res.isSuccess) {
                            networkTestStatus = "Connectivité HTTPS / TLS 1.3 confirmée. Accès Internet opérationnel (Wi-Fi / Données mobiles)."
                        } else {
                            networkTestStatus = "Échec du test réseau : ${res.exceptionOrNull()?.message}"
                        }
                    }
                },
                enabled = !isTestingNetwork,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTestingNetwork) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tester la liaison Internet HTTPS")
            }

            if (networkTestStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = networkTestStatus!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (networkTestStatus!!.startsWith("Connectivité"))
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
