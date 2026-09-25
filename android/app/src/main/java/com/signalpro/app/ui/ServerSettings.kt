package com.signalpro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.signalpro.app.AppContainer
import com.signalpro.app.core.net.ServerUrl
import com.signalpro.app.ui.screens.ErrorText
import com.signalpro.app.ui.screens.SuccessText
import com.signalpro.app.data.remote.ApiResult
import kotlinx.coroutines.launch

/**
 * Choix du serveur SignalPro — étape indispensable, pas un réglage avancé.
 *
 * L'application est auto-hébergeable : aucun serveur n'existe « par défaut ». Sans
 * cette adresse, tous les appels échouent. L'écran ne dit jamais « votre connexion
 * est en cause » : il indique l'adresse réellement contactée et teste celle que
 * l'utilisateur propose, avant de l'enregistrer.
 */
@Composable
fun ServerUrlCard(
    container: AppContainer,
    title: String = "Serveur SignalPro",
    startExpanded: Boolean? = null,
) {
    val configured = container.serverConfigured
    var editing by remember { mutableStateOf(startExpanded ?: !configured) }
    var value by remember { mutableStateOf(container.apiClient.baseUrl) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                "Adresse actuelle : ${container.apiClient.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (!configured && !editing) {
                Text(
                    "Aucun serveur n'est configuré : l'application ne peut rien envoyer tant que cette " +
                        "adresse n'est pas renseignée. Sans serveur de votre part, aucune fonction " +
                        "réseau ne peut aboutir.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (editing) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; message = null },
                    label = { Text("Adresse du serveur (ex. http://192.168.1.20:8000/)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Serveur sur votre ordinateur : lancez l'API (voir README, section « Serveur ») puis " +
                        "indiquez ici l'adresse IP de cet ordinateur et le port 8000. Le téléphone doit " +
                        "être sur le même réseau Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                )

                message?.let { text ->
                    if (isError) ErrorText(text) else SuccessText(text)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                when (val result = container.apiClient.testConnection(value)) {
                                    is ApiResult.Success -> {
                                        isError = false
                                        message = result.data
                                    }
                                    is ApiResult.Failure -> {
                                        isError = true
                                        message = result.error.message
                                    }
                                }
                                busy = false
                            }
                        },
                    ) { Text(if (busy) "Test en cours…" else "Tester la connexion") }

                    Button(
                        enabled = !busy,
                        onClick = {
                            when (val result = container.configureServer(value)) {
                                is ServerUrl.Result.Invalid -> {
                                    isError = true
                                    message = result.reason
                                }
                                is ServerUrl.Result.Valid -> {
                                    isError = false
                                    message = "Adresse enregistrée : ${result.parsed.url}" +
                                        if (result.parsed.cleartext) {
                                            " — connexion NON chiffrée : utilisez https:// dès que possible."
                                        } else {
                                            " — connexion chiffrée."
                                        }
                                    editing = false
                                }
                            }
                        },
                    ) { Text("Enregistrer") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = true; message = null }) { Text("Modifier l'adresse") }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = null
                                when (val result = container.apiClient.testConnection(container.apiClient.baseUrl)) {
                                    is ApiResult.Success -> {
                                        isError = false
                                        message = result.data
                                    }
                                    is ApiResult.Failure -> {
                                        isError = true
                                        message = result.error.message
                                    }
                                }
                                busy = false
                            }
                        },
                    ) { Text(if (busy) "Test en cours…" else "Tester la connexion") }
                }
                message?.let { text -> if (isError) ErrorText(text) else SuccessText(text) }
            }
        }
    }
}
