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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.ScanResponse
import com.signalpro.app.domain.ScamScanner
import com.signalpro.app.domain.Validation
import com.signalpro.app.ui.InfoCard
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

data class HitRow(
    val id: Int,
    val category: String,
    val matchKind: String,
    val score: Double,
    val convertedReportId: Int?,
    val createdAt: String,
)

class DetectViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val text: String = "",
        val scanning: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val localScan: ScamScanner.ScanResult? = null,
        val serverScan: ScanResponse? = null,
        val hits: List<HitRow> = emptyList(),
        val signatures: Int = 0,
        val signaturesVersion: String = "",
        val syncMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        loadHits()
    }

    fun onText(value: String) {
        _state.value = _state.value.copy(text = value)
    }

    fun analyzeOffline() {
        val text = _state.value.text
        if (text.isBlank()) {
            _state.value = _state.value.copy(error = "Collez un extrait de conversation à analyser.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, error = null, serverScan = null)
            val result = container.detectionRepository.scanLocal(text)
            _state.value = _state.value.copy(scanning = false, localScan = result)
        }
    }

    /** Analyse serveur : uniquement sur demande explicite (l'extrait quitte l'appareil). */
    fun analyzeServer() {
        val text = _state.value.text
        if (text.isBlank()) {
            _state.value = _state.value.copy(error = "Collez un extrait de conversation à analyser.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, error = null)
            when (val result = container.detectionRepository.scanServer(text, null, null)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(scanning = false, serverScan = result.data)
                    loadHits()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(scanning = false, error = result.error.message)
            }
        }
    }

    fun syncSignatures() {
        viewModelScope.launch {
            when (
                val result = container.detectionRepository.refreshSignatures(_state.value.signaturesVersion)
            ) {
                is ApiResult.Success -> {
                    container.settingsStore.setSignatureVersion(_state.value.signaturesVersion)
                    _state.value = _state.value.copy(
                        signatures = container.detectionRepository.signaturesCount(),
                        syncMessage = "Signatures à jour : ${result.data} entrées traitées.",
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun loadHits() {
        viewModelScope.launch {
            val count = container.detectionRepository.signaturesCount()
            val version = container.settingsStore.currentSignatureVersion()
            val hits = container.detectionRepository.hits()
            _state.value = _state.value.copy(
                signatures = count,
                signaturesVersion = version,
                hits = (hits as? ApiResult.Success)?.data?.map(::toHitRow) ?: _state.value.hits,
                error = (hits as? ApiResult.Failure)?.error?.message,
            )
        }
    }

    fun convertHit(
        hitId: Int,
        targetPhone: String,
        category: String,
        occurredAt: String,
        description: String,
    ) {
        val phoneError = Validation.phoneError(targetPhone)
        val dateError = Validation.occurredAtError(occurredAt)
        val descriptionError = Validation.descriptionError(description)
        val problem = phoneError ?: dateError ?: descriptionError
        if (problem != null) {
            _state.value = _state.value.copy(error = problem)
            return
        }
        viewModelScope.launch {
            val result = container.detectionRepository.convertHit(
                hitId = hitId,
                targetPhone = Validation.normalizePhone(targetPhone),
                category = category,
                occurredAt = Validation.toApiTimestamp(occurredAt),
                description = description.trim(),
            )
            when (result) {
                is ApiResult.Success -> {
                    val reportId = (result.data["report_id"] as? JsonPrimitive)?.int
                    _state.value = _state.value.copy(
                        info = "Signalement créé depuis la détection (id $reportId). Il doit encore être " +
                            "documenté par une preuve puis relu par un modérateur.",
                    )
                    loadHits()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    private fun toHitRow(element: Map<String, JsonElement>): HitRow {
        val obj = JsonObject(element)
        return HitRow(
            id = (obj["id"] as? JsonPrimitive)?.int ?: 0,
            category = (obj["category"] as? JsonPrimitive)?.content ?: "",
            matchKind = (obj["match_kind"] as? JsonPrimitive)?.content ?: "",
            score = (obj["score"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0,
            convertedReportId = (obj["converted_report_id"] as? JsonPrimitive)?.int,
            createdAt = (obj["created_at"] as? JsonPrimitive)?.content ?: "",
        )
    }
}

@Composable
fun DetectScreen(container: AppContainer) {
    val vm = containerViewModel<DetectViewModel> { DetectViewModel(it) }
    val state by vm.state.collectAsStateSafe()

    var convertFor by remember { mutableStateOf<HitRow?>(null) }
    var targetPhone by remember { mutableStateOf("") }
    var convertCategory by remember { mutableStateOf("") }
    var convertDate by remember { mutableStateOf("") }
    var convertDescription by remember { mutableStateOf("") }
    var categoryMenu by remember { mutableStateOf(false) }
    var serverConsent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadHits() }

    ScreenColumn {
        Text("Détection de spam et d'arnaque", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        InfoCard(
            "Analyse hors ligne par défaut",
            "Les signatures (mots-clés, domaines, expressions) sont téléchargées dans votre compte puis " +
                "stockées sur le téléphone. L'analyse qui suit se fait localement : aucun message n'est " +
                "envoyé. Vous pouvez aussi demander une analyse serveur, qui transmet l'extrait que vous " +
                "avez collé — c'est votre choix, jamais un réglage par défaut.",
        )
        Text(
            "Signatures disponibles : ${state.signatures}" +
                if (state.signaturesVersion.isNotBlank()) " (version ${state.signaturesVersion.take(12)}…)" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { vm.syncSignatures() }, modifier = Modifier.fillMaxWidth()) {
            Text("Actualiser les signatures")
        }
        state.syncMessage?.let { SuccessText(it) }

        OutlinedTextField(
            value = state.text,
            onValueChange = { vm.onText(it) },
            label = { Text("Extrait de conversation à analyser") },
            supportingText = { Text("Collez uniquement les messages suspects. Rien n'est stocké sans votre consentement.") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.analyzeOffline() }, enabled = !state.scanning) { Text("Analyser hors ligne") }
            OutlinedButton(onClick = { vm.analyzeServer() }, enabled = !state.scanning && serverConsent) {
                Text("Analyser côté serveur")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = serverConsent, onCheckedChange = { serverConsent = it })
            Text(
                "J'autorise l'envoi de cet extrait au serveur pour analyse (aucun stockage du texte, mais il " +
                    "quitte l'appareil).",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        state.localScan?.let { scan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Analyse locale", fontWeight = FontWeight.SemiBold)
                        StatusPill(
                            if (scan.isSuspicious) "suspect · score %.1f".format(scan.score)
                            else "rien de flagrant · score %.1f".format(scan.score),
                            statusColor(if (scan.isSuspicious) "rejected" else "verified"),
                        )
                    }
                    scan.matches.forEach { match ->
                        Text(
                            "${match.kind} : ${match.value} (poids ${match.weight})",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (scan.matches.isEmpty()) {
                        Text("Aucun motif connu détecté.", style = MaterialTheme.typography.bodySmall)
                    }
                    scan.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        state.serverScan?.let { scan ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Analyse serveur", fontWeight = FontWeight.SemiBold)
                        StatusPill(
                            if (scan.isSuspicious) "suspect · score %.1f".format(scan.score)
                            else "score %.1f".format(scan.score),
                            statusColor(if (scan.isSuspicious) "rejected" else "verified"),
                        )
                    }
                    if (scan.peerKnownMalicious) {
                        Text(
                            "Ce numéro figure déjà parmi les numéros malveillants confirmés " +
                                "(${scan.peerReportedCount} signalements).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    scan.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        Text("Mes détections (${state.hits.size})", fontWeight = FontWeight.SemiBold)
        Text(
            "Aucun extrait de message n'est conservé côté serveur : ces détections n'enregistrent que la " +
                "catégorie, le type de motif et le score.",
            style = MaterialTheme.typography.bodySmall,
        )
        state.hits.take(20).forEach { hit ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${hit.matchKind} · ${hit.category}", fontWeight = FontWeight.SemiBold)
                        StatusPill("score %.1f".format(hit.score), statusColor("pending"))
                    }
                    Text(
                        hit.createdAt.take(16) +
                            if (hit.convertedReportId != null) " · signalement n°${hit.convertedReportId}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (hit.convertedReportId == null) {
                        OutlinedButton(onClick = {
                            convertFor = hit
                            convertCategory = hit.category
                        }) { Text("Créer un signalement depuis cette détection") }
                    }
                }
            }
        }

        convertFor?.let { hit ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transformer la détection n°${hit.id} en signalement", fontWeight = FontWeight.SemiBold)
                    Text(
                        "La détection ne connaît pas le numéro (aucune donnée personnelle n'est stockée). " +
                            "Saisissez-le : le serveur applique les mêmes règles que pour tout signalement " +
                            "(preuve obligatoire, contact vérifié, quotas).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = targetPhone,
                        onValueChange = { targetPhone = it },
                        label = { Text("Numéro (format international)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(Disclaimer.categoryLabel(convertCategory).ifBlank { "Choisir la catégorie" })
                    }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        Disclaimer.CATEGORIES.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                convertCategory = key
                                categoryMenu = false
                            })
                        }
                    }
                    OutlinedTextField(
                        value = convertDate,
                        onValueChange = { convertDate = it },
                        label = { Text("Date et heure de réception (jj/MM/aaaa hh:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = convertDescription,
                        onValueChange = { convertDescription = it },
                        label = { Text("Description des faits") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.convertHit(hit.id, targetPhone, convertCategory, convertDate, convertDescription)
                            convertFor = null
                        }) { Text("Créer le signalement") }
                        OutlinedButton(onClick = { convertFor = null }) { Text("Annuler") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
