package com.signalpro.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.evidence.EvidenceStager
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.UserDto
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.ServerUrlCard
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val profile: UserDto? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val exports: List<String> = emptyList(),
        val businessStatus: Map<String, JsonElement> = emptyMap(),
        val deleted: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val profile = container.authRepository.loadProfile()
            val business = container.deviceRepository.businessStatus()
            _state.value = _state.value.copy(
                profile = profile,
                businessStatus = (business as? ApiResult.Success)?.data ?: emptyMap(),
            )
        }
    }

    fun changePassword(current: String, new: String) {
        if (new.length < 10) {
            _state.value = _state.value.copy(error = "Nouveau mot de passe : 10 caractères minimum.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (val result = container.authRepository.changePassword(current, new)) {
                is ApiResult.Success -> _state.value = _state.value.copy(busy = false, info = "Mot de passe modifié.")
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun linkBusiness(wabaId: String, phoneNumberId: String, accessToken: String) {
        if (wabaId.isBlank() || phoneNumberId.isBlank() || accessToken.isBlank()) {
            _state.value = _state.value.copy(error = "WABA ID, Phone number ID et jeton sont obligatoires.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (
                val result = container.deviceRepository.linkBusiness(wabaId.trim(), phoneNumberId.trim(), accessToken.trim())
            ) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        info = "Compte professionnel enregistré : le jeton est chiffré côté serveur et n'est " +
                            "jamais renvoyé à l'application.",
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    /** Export complet de mes données (CSV signalements + copie locale de mes preuves). */
    fun exportEverything(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            val exported = mutableListOf<String>()
            when (val result = container.reportRepository.exportCsv()) {
                is ApiResult.Success -> {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(dir, "mes_signalements.csv").apply { writeText(result.data) }
                    }
                    exported += file.absolutePath
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(busy = false, error = result.error.message)
                    return@launch
                }
            }
            _state.value = _state.value.copy(
                busy = false,
                exports = exported,
                info = "Export prêt (${exported.size} fichier). Vos preuves restent téléchargeables depuis " +
                    "chaque signalement : elles ne sont pas dupliquées ailleurs.",
            )
        }
    }

    fun shareExport(context: Context) {
        val path = _state.value.exports.firstOrNull() ?: return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Exporter mes données"))
        }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    /** Suppression réelle : compte, signalements, preuves, cache local et jetons. */
    fun deleteAccount(context: Context, password: String, confirmation: String) {
        if (confirmation.trim().uppercase() != "SUPPRIMER") {
            _state.value = _state.value.copy(error = "Saisissez SUPPRIMER pour confirmer la suppression définitive.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (val result = container.authRepository.deleteAccount(password)) {
                is ApiResult.Success -> {
                    EvidenceStager.purge(context)
                    withContext(Dispatchers.IO) {
                        File(context.cacheDir, "exports").deleteRecursively()
                        File(context.cacheDir, "imports").deleteRecursively()
                    }
                    _state.value = _state.value.copy(busy = false, deleted = true, info = "Compte supprimé.")
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun logout() {
        container.secureStore.clear()
    }
}

@Composable
fun SettingsScreen(container: AppContainer, onLoggedOut: () -> Unit) {
    val vm = containerViewModel<SettingsViewModel> { SettingsViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    val context = LocalContext.current
    val alertsEnabled by container.settingsStore.alertsEnabled.collectAsStateWithLifecycle(initialValue = true)
    val wifiOnly by container.settingsStore.wifiOnlySync.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var deleteConfirmation by remember { mutableStateOf("") }
    var wabaId by remember { mutableStateOf("") }
    var phoneNumberId by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            vm.logout()
            onLoggedOut()
        }
    }

    ScreenColumn {
        Text("Réglages", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        state.profile?.let { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.displayName.ifBlank { profile.email ?: "Compte" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Email : ${profile.email ?: "—"} · Téléphone : ${profile.phone ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Rôle : ${profile.role} · vérifié : ${if (profile.isVerified) "oui" else "non"} · " +
                            "avertissements : ${profile.strikes}/3 · compte professionnel : " +
                            (if (profile.isBusiness) "oui" else "non"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        ServerUrlCard(container)

        Text("Alertes et synchronisation", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("M'alerter si un numéro malveillant confirmé me contacte", style = MaterialTheme.typography.bodySmall)
            Switch(checked = alertsEnabled, onCheckedChange = { enabled ->
                scope.launch { container.settingsStore.setAlertsEnabled(enabled) }
            })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Synchroniser uniquement en Wi-Fi", style = MaterialTheme.typography.bodySmall)
            Switch(checked = wifiOnly, onCheckedChange = { enabled ->
                scope.launch { container.settingsStore.setWifiOnlySync(enabled) }
            })
        }

        Text("Changer mon mot de passe", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = currentPassword,
            onValueChange = { currentPassword = it },
            label = { Text("Mot de passe actuel") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Nouveau mot de passe (10 caractères minimum)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { vm.changePassword(currentPassword, newPassword) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Modifier le mot de passe") }

        Text("Compte professionnel (WhatsApp Business Platform)", fontWeight = FontWeight.SemiBold)
        Text(
            "Pour les entreprises : la liaison utilise l'API officielle (jeton système, identifiants WABA). " +
                "Le canal Cloud API ne permet pas de suspendre un compte — il sert à prouver l'existence de " +
                "la conversation et l'état des messages. Le jeton est stocké chiffré côté serveur et n'est " +
                "jamais renvoyé à l'application.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (state.businessStatus.isNotEmpty()) {
            val linked = (state.businessStatus["linked"] as? JsonPrimitive)?.content
            Text(
                "État : lié = $linked · " +
                    (state.businessStatus["reason"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(value = wabaId, onValueChange = { wabaId = it }, label = { Text("WABA ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = phoneNumberId,
            onValueChange = { phoneNumberId = it },
            label = { Text("Phone number ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = accessToken,
            onValueChange = { accessToken = it },
            label = { Text("Jeton système (permissions messaging)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { vm.linkBusiness(wabaId, phoneNumberId, accessToken) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer le compte professionnel") }

        Text("Mes données", fontWeight = FontWeight.SemiBold)
        InfoCard(
            "Aucune revente, aucun partage",
            "Vos signalements, numéros et preuves ne sont jamais vendus ni transmis à des tiers en dehors " +
                "des canaux nécessaires au traitement (Meta, uniquement pour les signalements que vous " +
                "transmettez). Les journaux d'actions sont conservés pour répondre aux réquisitions " +
                "judiciaires, et sont accessibles depuis la console de modération.",
        )
        OutlinedButton(onClick = { vm.exportEverything(context.applicationContext) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Exporter mes données (CSV)")
        }
        if (state.exports.isNotEmpty()) {
            OutlinedButton(onClick = { vm.shareExport(context.applicationContext) }, modifier = Modifier.fillMaxWidth()) {
                Text("Partager l'export")
            }
        }

        Text("Suppression du compte", fontWeight = FontWeight.SemiBold)
        Text(
            "La suppression est immédiate et définitive : compte, signalements, preuves téléversées et " +
                "cache local. Un délai technique de purge des sauvegardes serveur peut subsister " +
                "(documenté dans la politique de confidentialité).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { showDelete = !showDelete }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showDelete) "Annuler la suppression" else "Supprimer mon compte et mes données")
        }
        if (showDelete) {
            OutlinedTextField(
                value = deletePassword,
                onValueChange = { deletePassword = it },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = deleteConfirmation,
                onValueChange = { deleteConfirmation = it },
                label = { Text("Saisissez SUPPRIMER pour confirmer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.deleteAccount(context.applicationContext, deletePassword, deleteConfirmation) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirmer la suppression définitive") }
        }

        Spacer(Modifier.height(4.dp))
        InfoCard("Limites de l'outil — à lire", Disclaimer.LONG)
        OutlinedButton(onClick = { vm.logout(); onLoggedOut() }, modifier = Modifier.fillMaxWidth()) {
            Text("Se déconnecter")
        }
        Spacer(Modifier.height(8.dp))
    }
}
