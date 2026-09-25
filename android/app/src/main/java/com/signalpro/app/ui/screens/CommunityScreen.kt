package com.signalpro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.local.CachedBlacklistEntry
import com.signalpro.app.data.remote.AppealRequest
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.BlacklistExportDto
import com.signalpro.app.domain.Validation
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.LoadingBlock
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.StatusPill
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import com.signalpro.app.ui.statusColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class CommunityViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val export: BlacklistExportDto? = null,
        val stats: Map<String, JsonElement> = emptyMap(),
        val blockedSummary: String? = null,
        val connectedDevice: Boolean = false,
        val activity: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val list = container.communityRepository.refreshBlacklist()
            val stats = container.communityRepository.stats()
            val device = container.deviceRepository.connectedDevice()
            _state.value = _state.value.copy(
                loading = false,
                error = (list as? ApiResult.Failure)?.error?.message,
                stats = (stats as? ApiResult.Success)?.data ?: emptyMap(),
                connectedDevice = device != null,
            )
        }
    }

    /** Export réel de la liste : renvoie les numéros complets pour votre propre protection. */
    fun exportNumbers() {
        viewModelScope.launch {
            when (val result = container.communityRepository.exportNumbers()) {
                is ApiResult.Success -> _state.value = _state.value.copy(export = result.data)
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    /**
     * Blocage en masse — mesure de protection personnelle.
     * Aucun rapport avec une suspension : le texte le rappelle avant l'action.
     */
    fun blockAll(consent: Boolean, max: Int = 100) {
        if (!consent) {
            _state.value = _state.value.copy(error = Disclaimer.BLOCK_ALL_ACK)
            return
        }
        viewModelScope.launch {
            val device = container.deviceRepository.connectedDevice()
            if (device == null) {
                _state.value = _state.value.copy(
                    error = "Aucun appareil WhatsApp connecté : le blocage ne peut pas être exécuté. " +
                        "Lie ton WhatsApp pour agir réellement, ou bloque ces numéros manuellement (le nombre " +
                        "est trop élevé pour être fait « à la main » de façon fiable).",
                )
                return@launch
            }
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = container.communityRepository.blockAll(device.id, max)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    loading = false,
                    blockedSummary = "${result.data.blocked} numéro(s) bloqué(s) sur ${result.data.requested} " +
                        "demandé(s) · échecs : ${result.data.failed} · appareil : ${result.data.deviceStatus}",
                    info = "Le blocage protège votre compte. Cela ne provoque ni ne garantit aucune suspension.",
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.error.message)
            }
        }
    }

    fun createAppeal(targetPhone: String, claimantContact: String, statement: String, evidenceNote: String) {
        val phoneError = Validation.phoneError(targetPhone)
        if (phoneError != null) {
            _state.value = _state.value.copy(error = phoneError)
            return
        }
        if (claimantContact.isBlank()) {
            _state.value = _state.value.copy(error = "Indiquez un contact pour être joint (email ou téléphone).")
            return
        }
        if (statement.trim().length < 20) {
            _state.value = _state.value.copy(error = "Expliquez la situation en au moins 20 caractères.")
            return
        }
        viewModelScope.launch {
            val request = AppealRequest(
                targetPhone = Validation.normalizePhone(targetPhone),
                claimantContact = claimantContact.trim(),
                statement = statement.trim(),
                evidenceNote = evidenceNote.trim().ifBlank { null },
            )
            when (val result = container.communityRepository.createAppeal(request)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    info = "Contestation ${result.data.publicRef} enregistrée : un modérateur humain l'examinera. " +
                        "Si les preuves s'avèrent fausses, les signalements concernés sont retirés et les " +
                        "auteurs sanctionnés.",
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun loadActivity() {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            when (val result = container.communityRepository.scanAlerts()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    activity = "Vérification effectuée : vos conversations indexées ont été comparées aux " +
                        "numéros malveillants confirmés.",
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }
}

@Composable
fun CommunityScreen(container: AppContainer) {
    val vm = containerViewModel<CommunityViewModel> { CommunityViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    val entries by container.communityRepository.observeCachedBlacklist()
        .collectAsStateWithLifecycle(initialValue = emptyList<CachedBlacklistEntry>())

    var blockConsent by remember { mutableStateOf(false) }
    var appealPhone by remember { mutableStateOf("") }
    var appealContact by remember { mutableStateOf("") }
    var appealStatement by remember { mutableStateOf("") }
    var appealEvidence by remember { mutableStateOf("") }
    var blockMax by remember { mutableStateOf("100") }

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenColumn {
        Text("Numéros malveillants confirmés", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        InfoCard(
            "Règle d'inscription à cette liste",
            "Un numéro est listé après au moins 3 signalements vérifiés venant de personnes distinctes, avec " +
                "preuves. La liste est visible par la communauté ; chaque entrée indique le statut de " +
                "suspension, qui n'est jamais supposé : il vient d'une confirmation Meta ou d'un modérateur.",
        )

        val total = (state.stats["targets"] as? JsonPrimitive)?.int
            ?: (state.stats["confirmed_targets"] as? JsonPrimitive)?.int
            ?: entries.size
        Text(
            "Numéros listés : $total · entrées chargées : ${entries.size}" +
                " · appareil lié : ${if (state.connectedDevice) "oui" else "non"}",
            style = MaterialTheme.typography.bodySmall,
        )

        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }
        state.activity?.let { InfoCard("Bilan", it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.refresh() }, enabled = !state.loading) { Text("Actualiser") }
            OutlinedButton(onClick = { vm.exportNumbers() }) { Text("Obtenir la liste complète") }
        }

        state.export?.let { export ->
            InfoCard(
                "Liste exportable : ${export.count} numéros",
                export.disclaimer,
            )
            Text(
                export.numbers.take(20).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
            )
            if (export.numbers.size > 20) {
                Text("… ${export.numbers.size - 20} autres numéros", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Bloquer en masse (protection personnelle)", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = blockMax,
            onValueChange = { blockMax = it.filter { ch -> ch.isDigit() } },
            label = { Text("Nombre maximum de numéros à bloquer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = blockConsent, onCheckedChange = { blockConsent = it })
            Text(Disclaimer.BLOCK_ALL_ACK, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.blockAll(blockConsent, blockMax.toIntOrNull() ?: 100) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bloquer ces numéros sur mon WhatsApp") }
        state.blockedSummary?.let { SuccessText(it) }
        OutlinedButton(onClick = { vm.loadActivity() }, modifier = Modifier.fillMaxWidth()) {
            Text("Vérifier si ces numéros me contactent")
        }

        Text("Contester un signalement qui me concerne", fontWeight = FontWeight.SemiBold)
        Text(
            "Si vous êtes le propriétaire d'un numéro listé, contestez : un modérateur humain examine chaque " +
                "recours. Si les preuves sont fausses, les signalements sont retirés et leurs auteurs " +
                "sanctionnés (bannissement au-delà de 2 signalements abusifs confirmés).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = appealPhone,
            onValueChange = { appealPhone = it },
            label = { Text("Numéro concerné (format international)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appealContact,
            onValueChange = { appealContact = it },
            label = { Text("Votre contact (email ou téléphone)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appealStatement,
            onValueChange = { appealStatement = it },
            label = { Text("Votre explication (20 caractères minimum)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = appealEvidence,
            onValueChange = { appealEvidence = it },
            label = { Text("Éléments que vous pouvez fournir (facultatif)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.createAppeal(appealPhone, appealContact, appealStatement, appealEvidence) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Envoyer ma contestation") }

        if (state.loading && entries.isEmpty()) {
            LoadingBlock("Chargement de la liste communautaire…")
        } else if (entries.isEmpty()) {
            InfoCard(
                "Liste vide",
                "Aucun numéro n'atteint encore le seuil de 3 signalements vérifiés. C'est le fonctionnement " +
                    "normal : la liste ne se remplit pas artificiellement.",
            )
        } else {
            Text("Entrées les plus signalées", fontWeight = FontWeight.SemiBold)
            entries.take(40).forEach { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.phoneMasked, fontWeight = FontWeight.SemiBold)
                            StatusPill(entry.suspensionStatus, statusColor(entry.suspensionStatus))
                        }
                        Text(
                            "${entry.categoryLabel} · ${entry.verifiedReports} signalements vérifiés par " +
                                "${entry.distinctReporters} personnes",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
