package com.signalpro.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.evidence.EvidenceStager
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.ReportCreateRequest
import com.signalpro.app.data.remote.ReportDto
import com.signalpro.app.data.remote.ScanResponse
import com.signalpro.app.domain.ScamScanner
import com.signalpro.app.domain.Validation
import com.signalpro.app.ui.DisclaimerBanner
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportForm(
    val targetPhone: String = "",
    val category: String = "",
    val occurredAt: String = "",
    val description: String = "",
    val messageIds: String = "",
    val proofMethod: String = "linked_device_scan",
    val storeMessages: Boolean = false,
    val excerpt: String = "",
)

data class PickedEvidence(val kind: String, val uri: Uri, val label: String)

class ReportNewViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val submitting: Boolean = false,
        val error: String? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
        val scanning: Boolean = false,
        val offlineScan: ScamScanner.ScanResult? = null,
        val serverScan: ScanResponse? = null,
        val created: ReportDto? = null,
        val uploadNotes: List<String> = emptyList(),
        val hasConnectedDevice: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            val device = container.deviceRepository.connectedDevice()
            _state.value = _state.value.copy(hasConnectedDevice = device != null)
        }
    }

    /** Analyse 100 % locale : rien ne quitte le téléphone. */
    fun analyzeOffline(text: String) {
        if (text.isBlank()) {
            _state.value = _state.value.copy(error = "Saisissez d'abord le texte à analyser.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, error = null)
            val result = container.detectionRepository.scanLocal(text)
            _state.value = _state.value.copy(scanning = false, offlineScan = result)
        }
    }

    /** Analyse serveur : uniquement si l'utilisateur l'a explicitement demandée. */
    fun analyzeServer(text: String, peerPhone: String) {
        if (text.isBlank()) {
            _state.value = _state.value.copy(error = "Saisissez d'abord le texte à analyser.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, error = null)
            val device = container.deviceRepository.connectedDevice()
            when (
                val result = container.detectionRepository.scanServer(
                    text = text,
                    peerPhone = peerPhone.takeIf { it.isNotBlank() },
                    deviceId = device?.id,
                )
            ) {
                is ApiResult.Success -> _state.value = _state.value.copy(scanning = false, serverScan = result.data)
                is ApiResult.Failure -> _state.value = _state.value.copy(scanning = false, error = result.error.message)
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null)
    }

    fun reset() {
        _state.value = UiState(hasConnectedDevice = _state.value.hasConnectedDevice)
    }

    fun submit(
        context: Context,
        form: ReportForm,
        files: List<PickedEvidence>,
    ) {
        val errors = buildMap {
            Validation.phoneError(form.targetPhone)?.let { put("phone", it) }
            if (form.category.isBlank()) put("category", "Choisissez la catégorie d'infraction.")
            Validation.occurredAtError(form.occurredAt)?.let { put("date", it) }
            Validation.descriptionError(form.description)?.let { put("description", it) }
            if (files.isEmpty() && form.messageIds.isBlank()) {
                put(
                    "evidence",
                    "Une preuve est obligatoire : capture d'écran, export de conversation ou identifiant de " +
                        "message. Sans preuve, le signalement n'est jamais transmis (règle non désactivable).",
                )
            }
        }
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(fieldErrors = errors, error = "Corrigez les champs signalés.")
            return
        }

        _state.value = _state.value.copy(submitting = true, error = null, fieldErrors = emptyMap())
        viewModelScope.launch {
            val request = ReportCreateRequest(
                targetPhone = Validation.normalizePhone(form.targetPhone),
                category = form.category,
                occurredAt = Validation.toApiTimestamp(form.occurredAt),
                description = form.description.trim(),
                messageIds = form.messageIds.split(",", ";", " ").map { it.trim() }.filter { it.isNotEmpty() },
                contactProofMethod = form.proofMethod,
                storeMessages = form.storeMessages,
                messageExcerpt = if (form.storeMessages && form.excerpt.isNotBlank()) form.excerpt else null,
            )
            val created = container.reportRepository.create(request)
            if (created is ApiResult.Failure) {
                _state.value = _state.value.copy(submitting = false, error = created.error.message)
                return@launch
            }
            val report = (created as ApiResult.Success).data
            val notes = mutableListOf<String>()

            files.forEach { picked ->
                val staged = EvidenceStager.stage(context, picked.uri, picked.kind)
                staged.fold(
                    onSuccess = { value ->
                        when (
                            val upload = container.reportRepository.uploadEvidence(report.id, picked.kind, value.file)
                        ) {
                            is ApiResult.Success -> {
                                notes += "${picked.label} transmise (${value.sizeBytes / 1024} Ko, " +
                                    "SHA-256 ${upload.data.sha256.take(12)}…)"
                                EvidenceStager.discard(value) // la preuve ne reste pas sur l'appareil
                            }
                            is ApiResult.Failure -> {
                                notes += "${picked.label} : envoi impossible — ${upload.error.message} " +
                                    "Le signalement existe et pourra recevoir la preuve depuis son écran de détail."
                                EvidenceStager.discard(value)
                            }
                        }
                    },
                    onFailure = { throwable ->
                        notes += "${picked.label} : ${throwable.message}"
                    },
                )
            }

            val refreshed = container.reportRepository.detail(report.id)
            val finalReport = (refreshed as? ApiResult.Success)?.data ?: report
            _state.value = _state.value.copy(
                submitting = false,
                created = finalReport,
                uploadNotes = notes,
            )
        }
    }
}

@Composable
fun ReportNewScreen(
    container: AppContainer,
    sharedText: String?,
    onConsumeSharedText: () -> Unit,
    onCreated: (Int) -> Unit,
) {
    val vm = containerViewModel<ReportNewViewModel> { ReportNewViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    val context = LocalContext.current
    val appContext = context.applicationContext

    val defaultDate = remember {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
    }

    var form by remember {
        mutableStateOf(
            ReportForm(
                targetPhone = sharedText?.let { extractPhone(it) } ?: "",
                occurredAt = defaultDate,
                proofMethod = "manual_declaration",
            ),
        )
    }
    val files = remember { mutableStateListOf<PickedEvidence>() }
    var categoryMenu by remember { mutableStateOf(false) }
    var acceptSend by remember { mutableStateOf(false) }

    // Sélecteurs de fichiers : capture d'écran, export de conversation, en-tête technique.
    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { files.add(PickedEvidence("screenshot", it, "Capture d'écran")) }
    }
    val pickExport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { files.add(PickedEvidence("chat_export", it, "Export de conversation")) }
    }
    val pickHeader = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { files.add(PickedEvidence("header_dump", it, "En-tête technique")) }
    }

    LaunchedEffect(Unit) {
        if (sharedText != null) onConsumeSharedText()
    }

    ScreenColumn {
        Text("Nouveau signalement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        DisclaimerBanner(full = true)
        InfoCard(
            "Ce que devient votre signalement",
            "Il est enregistré, contrôlé (preuve obligatoire, contact vérifié), relu par un modérateur humain, " +
                "puis transmis par un canal réel : votre compte WhatsApp via la passerelle, l'API Cloud si " +
                "vous êtes une entreprise, ou un parcours guidé. Honestité des états : rien n'est affiché " +
                "comme « envoyé » sans confirmation.",
        )

        OutlinedTextField(
            value = form.targetPhone,
            onValueChange = { form = form.copy(targetPhone = it); vm.clearMessages() },
            label = { Text("Numéro à signaler (format international)") },
            isError = state.fieldErrors["phone"] != null,
            supportingText = { state.fieldErrors["phone"]?.let { Text(it) } ?: Text("Ex. +223 61 23 45 67") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    Disclaimer.categoryLabel(form.category).let {
                        if (form.category.isBlank()) "Choisir la catégorie d'infraction" else it
                    },
                )
            }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                Disclaimer.CATEGORIES.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { form = form.copy(category = key); categoryMenu = false },
                    )
                }
            }
            state.fieldErrors["category"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        OutlinedTextField(
            value = form.occurredAt,
            onValueChange = { form = form.copy(occurredAt = it) },
            label = { Text("Date et heure de réception du message") },
            isError = state.fieldErrors["date"] != null,
            supportingText = {
                state.fieldErrors["date"]?.let { Text(it) } ?: Text("Format jj/MM/aaaa hh:mm (heure locale)")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.description,
            onValueChange = { form = form.copy(description = it) },
            label = { Text("Description courte des faits") },
            isError = state.fieldErrors["description"] != null,
            supportingText = { state.fieldErrors["description"]?.let { Text(it) } },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.messageIds,
            onValueChange = { form = form.copy(messageIds = it) },
            label = { Text("Identifiants de message (facultatif)") },
            supportingText = {
                Text("Copiez l'ID du message (Appuyer longuement → Détails → ID). Utilisez la virgule comme séparateur.")
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Preuve de contact", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = form.proofMethod == "linked_device_scan",
                onClick = { form = form.copy(proofMethod = "linked_device_scan") },
                enabled = state.hasConnectedDevice,
            )
            Text(
                if (state.hasConnectedDevice) {
                    "Via mon WhatsApp lié (vérification automatique que ce numéro m'a contacté)"
                } else {
                    "Via mon WhatsApp lié — indisponible : aucun appareil connecté"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = form.proofMethod == "manual_declaration",
                onClick = { form = form.copy(proofMethod = "manual_declaration") },
            )
            Text(
                "Déclaration sur l'honneur + capture d'écran de la conversation (relue par un modérateur)",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("Pièces justificatives (au moins une obligatoire)", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickScreenshot.launch("image/*") }) { Text("Capture") }
            OutlinedButton(onClick = { pickExport.launch("*/*") }) { Text("Export .txt") }
            OutlinedButton(onClick = { pickHeader.launch("*/*") }) { Text("En-tête") }
        }
        files.forEachIndexed { index, picked ->
            val preCheck = EvidenceStager.preCheck(appContext, picked.uri, picked.kind)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${picked.label}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${EvidenceStager.displayName(appContext, picked.uri)} · " +
                                "${EvidenceStager.fileSize(appContext, picked.uri) / 1024} Ko",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        preCheck?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { files.removeAt(index) }) { Text("Retirer") }
                }
            }
        }
        state.fieldErrors["evidence"]?.let { ErrorText(it) }

        Text("Détection d'arnaque (facultative)", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = form.excerpt,
            onValueChange = { form = form.copy(excerpt = it) },
            label = { Text("Extrait du message à analyser") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.analyzeOffline(form.excerpt) }, enabled = !state.scanning) {
                Text("Analyser hors ligne")
            }
            OutlinedButton(onClick = { vm.analyzeServer(form.excerpt, form.targetPhone) }, enabled = !state.scanning) {
                Text("Analyser côté serveur")
            }
        }
        state.offlineScan?.let { scan ->
            InfoCard(
                if (scan.isSuspicious) "Signaux d'arnaque détectés (score ${"%.1f".format(scan.score)})"
                else "Aucun signal fort détecté (score ${"%.1f".format(scan.score)})",
                (scan.matches.take(6).joinToString(" · ") { "${it.kind}: ${it.value}" }
                    .ifBlank { "Aucun motif connu trouvé." }) + "\n" + scan.advice.joinToString("\n"),
            )
        }
        state.serverScan?.let { scan ->
            InfoCard(
                "Analyse serveur : score ${"%.1f".format(scan.score)}" +
                    if (scan.peerKnownMalicious) " · ce numéro est déjà connu comme malveillant" else "",
                "Signalements existants : ${scan.peerReportedCount}. " + scan.advice.joinToString("\n"),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = form.storeMessages,
                onCheckedChange = { form = form.copy(storeMessages = it) },
            )
            Text(
                "Je consens explicitement à ce que l'extrait ci-dessus soit conservé comme preuve sur le " +
                    "serveur. Sans cette case, le texte n'est jamais stocké.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = acceptSend, onCheckedChange = { acceptSend = it })
            Text(
                "Je certifie que les faits sont exacts et que les pièces fournies sont authentiques. Je sais " +
                    "qu'un faux signalement est passible de poursuites et entraîne le bannissement de mon " +
                    "compte SignalPro, et que ce service ne peut pas garantir la suspension du numéro signalé.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.error?.let { ErrorText(it) }

        Button(
            onClick = { vm.submit(appContext, form, files) },
            enabled = acceptSend && !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer le signalement") }

        if (state.submitting) {
            Text("Enregistrement et envoi des preuves…", style = MaterialTheme.typography.bodySmall)
        }
        state.uploadNotes.forEach { SuccessText(it) }

        state.created?.let { report ->
            SuccessText(
                "Signalement ${report.publicRef} enregistré · statut : ${report.statusLabel} · " +
                    "preuves : ${report.evidences.size}",
            )
            Button(onClick = { onCreated(report.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir le signalement")
            }
        }

        if (form.messageIds.isNotBlank()) {
            InfoCard(
                "Identifiants fournis",
                "Le serveur vérifie chaque ID auprès de votre appareil lié : les messages réellement " +
                    "retrouvés deviennent des preuves horodatées. Un ID inventé ou introuvable est refusé.",
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.reset(); form = ReportForm(occurredAt = defaultDate) }) { Text("Réinitialiser") }
    }
}

/** Extrait un numéro d'un texte partagé (ex. depuis WhatsApp ou un SMS). */
private fun extractPhone(text: String): String {
    val match = Regex("\\+?[0-9][0-9 ()\\-.]{7,20}").find(text) ?: return ""
    return Validation.normalizePhone(match.value)
}
