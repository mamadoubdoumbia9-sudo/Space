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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.LoadingBlock
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class QueueItem(
    val entityId: Int,
    val publicRef: String,
    val targetMasked: String,
    val category: String,
    val summary: String,
    val createdAt: String,
    val evidences: Int,
    val flags: List<String>,
)

data class AppealItem(
    val id: Int,
    val publicRef: String,
    val targetMasked: String,
    val targetId: Int?,
    val claimant: String,
    val statement: String,
    val evidenceNote: String?,
    val status: String,
)

class ModerationViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val queue: List<QueueItem> = emptyList(),
        val appeals: List<AppealItem> = emptyList(),
        val stats: Map<String, JsonElement> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val queue = container.apiClient.call { container.api.moderationQueue() }
            val appeals = container.apiClient.call { container.api.moderationAppeals() }
            val stats = container.apiClient.call { container.api.moderationStats() }
            _state.value = _state.value.copy(
                loading = false,
                queue = (queue as? ApiResult.Success)?.data?.map(::toQueueItem) ?: emptyList(),
                appeals = (appeals as? ApiResult.Success)?.data?.map(::toAppealItem) ?: emptyList(),
                stats = (stats as? ApiResult.Success)?.data ?: emptyMap(),
                error = (queue as? ApiResult.Failure)?.error?.message,
            )
        }
    }

    fun decideReport(reportId: Int, decision: String, reason: String) {
        if (reason.trim().length < 5) {
            _state.value = _state.value.copy(error = "Motif obligatoire (5 caractères minimum) : il est journalisé.")
            return
        }
        viewModelScope.launch {
            when (
                val result = container.apiClient.call {
                    container.api.decideReport(reportId, mapOf("decision" to decision, "reason" to reason.trim()))
                }
            ) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        info = "Décision « $decision » enregistrée sur le signalement n°$reportId.",
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun decideAppeal(appealId: Int, decision: String, reason: String) {
        if (reason.trim().length < 5) {
            _state.value = _state.value.copy(error = "Motif obligatoire (5 caractères minimum).")
            return
        }
        viewModelScope.launch {
            when (
                val result = container.apiClient.call {
                    container.api.decideAppeal(appealId, mapOf("decision" to decision, "reason" to reason.trim()))
                }
            ) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(info = "Contestation n°$appealId traitée ($decision).")
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun confirmSuspension(targetId: Int, confirmed: Boolean, evidence: String) {
        if (evidence.trim().length < 5) {
            _state.value = _state.value.copy(
                error = "Preuve de suspension obligatoire : capture du message WhatsApp/Meta ou référence du " +
                    "signalement côté Meta. Une suspension n'est jamais supposée.",
            )
            return
        }
        viewModelScope.launch {
            when (
                val result = container.apiClient.call {
                    container.api.recordSuspension(targetId, confirmed, evidence.trim())
                }
            ) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        info = if (confirmed) "Suspension confirmée et enregistrée." else "Suspension non confirmée.",
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun buildDossier(targetId: Int) {
        viewModelScope.launch {
            when (val result = container.apiClient.call { container.api.buildDossier(targetId) }) {
                is ApiResult.Success -> {
                    val ref = (result.data["public_ref"] as? JsonPrimitive)?.content
                    val reports = (result.data["reports_count"] as? JsonPrimitive)?.int
                    _state.value = _state.value.copy(
                        info = "Dossier ${ref ?: "—"} généré ($reports signalements inclus, exportable en PDF " +
                            "depuis la console web pour transmission aux autorités).",
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    private fun toQueueItem(payload: Map<String, JsonElement>): QueueItem {
        val obj = JsonObject(payload)
        return QueueItem(
            entityId = (obj["entity_id"] as? JsonPrimitive)?.int ?: 0,
            publicRef = (obj["public_ref"] as? JsonPrimitive)?.content ?: "",
            targetMasked = (obj["target_phone_masked"] as? JsonPrimitive)?.content ?: "—",
            category = (obj["category"] as? JsonPrimitive)?.content ?: "",
            summary = (obj["summary"] as? JsonPrimitive)?.content ?: "",
            createdAt = (obj["created_at"] as? JsonPrimitive)?.content ?: "",
            evidences = (obj["evidences_count"] as? JsonPrimitive)?.int ?: 0,
            flags = obj["auto_flags"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        )
    }

    private fun toAppealItem(payload: Map<String, JsonElement>): AppealItem {
        val obj = JsonObject(payload)
        return AppealItem(
            id = (obj["id"] as? JsonPrimitive)?.int ?: 0,
            publicRef = (obj["public_ref"] as? JsonPrimitive)?.content ?: "",
            targetMasked = (obj["target_phone_masked"] as? JsonPrimitive)?.content ?: "—",
            targetId = (obj["target_id"] as? JsonPrimitive)?.int,
            claimant = (obj["claimant_contact"] as? JsonPrimitive)?.content ?: "—",
            statement = (obj["statement"] as? JsonPrimitive)?.content ?: "",
            evidenceNote = (obj["evidence_note"] as? JsonPrimitive)?.content,
            status = (obj["status"] as? JsonPrimitive)?.content ?: "",
        )
    }
}

@Composable
fun ModerationScreen(container: AppContainer) {
    val vm = containerViewModel<ModerationViewModel> { ModerationViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    var reasons by remember { mutableStateOf(mapOf<Int, String>()) }
    var appealReasons by remember { mutableStateOf(mapOf<Int, String>()) }

    LaunchedEffect(Unit) { vm.load() }

    ScreenColumn {
        Text("Console de modération", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        InfoCard(
            "Rôle de la relecture humaine",
            "Chaque signalement est relu avant toute transmission : cohérence du contact, preuve lisible, " +
                "absence de doublon. Les contestations sont traitées par un humain, et une contestation " +
                "acceptée retire les signalements en cause et sanctionne leurs auteurs si les preuves étaient " +
                "fausses. Toutes les décisions sont journalisées (audit consultable pour les autorités).",
        )
        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        if (state.stats.isNotEmpty()) {
            Text(
                state.stats.entries.take(8).joinToString(" · ") { "${it.key} = ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(onClick = { vm.load() }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text("Actualiser la file")
        }

        Text("À vérifier (${state.queue.size})", fontWeight = FontWeight.SemiBold)
        if (state.loading && state.queue.isEmpty()) {
            LoadingBlock("Chargement de la file de modération…")
        } else if (state.queue.isEmpty()) {
            InfoCard("File vide", "Aucun signalement en attente de relecture.")
        } else {
            state.queue.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${item.publicRef} · ${item.targetMasked}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.category} · ${item.evidences} preuve(s) · reçu le ${item.createdAt.take(16)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(item.summary, style = MaterialTheme.typography.bodySmall)
                        if (item.flags.isNotEmpty()) {
                            Text(
                                "Signaux automatiques : ${item.flags.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedTextField(
                            value = reasons[item.entityId].orEmpty(),
                            onValueChange = { reasons = reasons + (item.entityId to it) },
                            label = { Text("Motif de la décision (journalisé)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                vm.decideReport(item.entityId, "verify", reasons[item.entityId].orEmpty())
                            }) { Text("Vérifier") }
                            OutlinedButton(onClick = {
                                vm.decideReport(item.entityId, "reject", reasons[item.entityId].orEmpty())
                            }) { Text("Rejeter") }
                            OutlinedButton(onClick = {
                                vm.decideReport(item.entityId, "reject_abusive", reasons[item.entityId].orEmpty())
                            }) { Text("Abusif") }
                        }
                    }
                }
            }
        }

        Text("Contestations (${state.appeals.size})", fontWeight = FontWeight.SemiBold)
        if (state.appeals.isEmpty()) {
            Text("Aucune contestation en attente.", style = MaterialTheme.typography.bodySmall)
        } else {
            state.appeals.forEach { appeal ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${appeal.publicRef} · ${appeal.targetMasked}", fontWeight = FontWeight.SemiBold)
                        Text("Contact du réclamant : ${appeal.claimant}", style = MaterialTheme.typography.bodySmall)
                        Text(appeal.statement, style = MaterialTheme.typography.bodySmall)
                        appeal.evidenceNote?.let { Text("Éléments fournis : $it", style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(
                            value = appealReasons[appeal.id].orEmpty(),
                            onValueChange = { appealReasons = appealReasons + (appeal.id to it) },
                            label = { Text("Motif de la décision") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                vm.decideAppeal(appeal.id, "clear_target", appealReasons[appeal.id].orEmpty())
                            }) { Text("Accepter (retirer)") }
                            OutlinedButton(onClick = {
                                vm.decideAppeal(appeal.id, "verify", appealReasons[appeal.id].orEmpty())
                            }) { Text("Rejeter (maintenir)") }
                        }
                        appeal.targetId?.let { targetId ->
                            OutlinedButton(onClick = {
                                vm.confirmSuspension(targetId, true, appealReasons[appeal.id].orEmpty())
                            }) { Text("Confirmer une suspension constatée") }
                            OutlinedButton(onClick = { vm.buildDossier(targetId) }) {
                                Text("Générer le dossier")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
