package com.signalpro.app.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.data.evidence.EvidenceStager
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.ReportDto
import com.signalpro.app.ui.DisclaimerBanner
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.LoadingBlock
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.StatusPill
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import com.signalpro.app.ui.formatTimestamp
import com.signalpro.app.ui.statusColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReportDetailViewModel(
    private val container: AppContainer,
    private val reportId: Int,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val report: ReportDto? = null,
        val error: String? = null,
        val info: String? = null,
        val hasConnectedDevice: Boolean = false,
        val isBusiness: Boolean = false,
        val busy: Boolean = false,
        val guidedSteps: List<String> = emptyList(),
        val submissionId: Int? = null,
        val exportPath: String? = null,
        val evidenceChecks: List<String> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val detail = container.reportRepository.detail(reportId)
            val device = container.deviceRepository.connectedDevice()
            val profile = container.authRepository.loadProfile()
            _state.value = _state.value.copy(
                loading = false,
                report = (detail as? ApiResult.Success)?.data,
                error = (detail as? ApiResult.Failure)?.error?.message,
                hasConnectedDevice = device != null,
                isBusiness = profile?.isBusiness == true,
            )
        }
    }

    fun addEvidence(context: Context, kind: String, uri: Uri, label: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            val staged = EvidenceStager.stage(context, uri, kind)
            staged.fold(
                onSuccess = { value ->
                    when (val upload = container.reportRepository.uploadEvidence(reportId, kind, value.file)) {
                        is ApiResult.Success -> {
                            _state.value = _state.value.copy(
                                busy = false,
                                info = "$label transmise · SHA-256 ${upload.data.sha256.take(16)}… · " +
                                    "intégrité vérifiée : ${if (upload.data.integrityOk) "oui" else "non"}",
                            )
                            EvidenceStager.discard(value)
                            load()
                        }
                        is ApiResult.Failure -> {
                            EvidenceStager.discard(value)
                            _state.value = _state.value.copy(busy = false, error = upload.error.message)
                        }
                    }
                },
                onFailure = { throwable ->
                    _state.value = _state.value.copy(busy = false, error = throwable.message)
                },
            )
        }
    }

    fun submit(adapter: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            val device = container.deviceRepository.connectedDevice()
            when (val result = container.reportRepository.submit(reportId, adapter, device?.id)) {
                is ApiResult.Success -> {
                    val payload = result.data
                    _state.value = _state.value.copy(
                        busy = false,
                        guidedSteps = payload.steps,
                        submissionId = payload.submissionId,
                        info = payload.detail.ifBlank {
                            if (payload.queued) "Transmission mise en file : le résultat réel sera enregistré." else null
                        },
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun markManualDone(note: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            when (val result = container.reportRepository.markManualDone(reportId, note)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        info = "Parcours guidé enregistré comme effectué par vous. Cela sera vérifié avant " +
                            "tout comptage : nous ne déclarons jamais un envoi à votre place.",
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun export(context: Context) {
        viewModelScope.launch {
            val report = _state.value.report ?: return@launch
            _state.value = _state.value.copy(busy = true, error = null)
            val response = runCatching {
                container.apiClient.call { container.api.exportReport(reportId) }
            }.getOrNull()
            when (response) {
                is ApiResult.Success -> {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(dir, "signalement_${report.publicRef}.txt").apply { writeText(response.data.string()) }
                    }
                    _state.value = _state.value.copy(busy = false, exportPath = file.absolutePath)
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = response.error.message)
                else -> _state.value = _state.value.copy(busy = false, error = "Export impossible.")
            }
        }
    }

    fun shareExport(context: Context) {
        val path = _state.value.exportPath ?: return
        val file = File(path)
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager le récapitulatif"))
        }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    fun downloadEvidence(context: Context, evidenceId: Int, filename: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (val result = container.reportRepository.evidenceBytes(evidenceId)) {
                is ApiResult.Success -> {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(dir, filename.ifBlank { "preuve_$evidenceId" }).apply { writeBytes(result.data) }
                    }
                    _state.value = _state.value.copy(
                        busy = false,
                        info = "Preuve récupérée : ${file.name} (${file.length()} octets).",
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }
}

@Composable
fun ReportDetailScreen(container: AppContainer, reportId: Int, onBack: () -> Unit) {
    val vm = containerViewModel<ReportDetailViewModel>(key = "report-$reportId") {
        ReportDetailViewModel(it, reportId)
    }
    val state by vm.state.collectAsStateSafe()
    val context = LocalContext.current

    var manualAck by remember { mutableStateOf(false) }
    var adapter by remember { mutableStateOf("") }
    var manualNote by remember { mutableStateOf("") }

    LaunchedEffect(reportId) { vm.load() }

    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.addEvidence(context.applicationContext, "screenshot", it, "Capture d'écran") }
    }
    val pickExport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.addEvidence(context.applicationContext, "chat_export", it, "Export de conversation") }
    }
    val pickHeader = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.addEvidence(context.applicationContext, "header_dump", it, "En-tête technique") }
    }

    ScreenColumn {
        TextButton(onClick = onBack) { Text("← Retour") }
        if (state.loading && state.report == null) {
            LoadingBlock("Chargement du signalement…")
            return@ScreenColumn
        }
        val report = state.report
        if (report == null) {
            ErrorText(state.error ?: "Signalement indisponible.")
            return@ScreenColumn
        }

        Text(report.targetPhoneMasked, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(report.statusLabel, statusColor(report.status))
            Text(report.publicRef, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(report.categoryLabel, fontWeight = FontWeight.SemiBold)
                Text("Reçu le ${formatTimestamp(report.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                Text("Description : ${report.description}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Preuve de contact : ${if (report.contactVerified) "vérifiée" else "à vérifier par un modérateur"} " +
                        "· source : ${report.source}",
                    style = MaterialTheme.typography.bodySmall,
                )
                report.decisionReason?.let {
                    Text("Motif de la décision : $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Preuves (${report.evidences.size})", fontWeight = FontWeight.SemiBold)
        if (report.evidences.isEmpty()) {
            InfoCard(
                "Aucune preuve pour l'instant",
                "Un signalement sans preuve valide n'est jamais transmis à WhatsApp : il reste bloqué à l'état " +
                    "« preuve manquante ». Ajoutez une capture, un export de conversation ou des identifiants " +
                    "de message.",
            )
        } else {
            report.evidences.forEach { evidence ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${evidence.kind} · ${evidence.filename}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${evidence.sizeBytes} octets · SHA-256 ${evidence.sha256.take(16)}… · " +
                                "intégrité : ${if (evidence.integrityOk) "vérifiée" else "NON vérifiée"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        evidence.validationDetail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { vm.downloadEvidence(context.applicationContext, evidence.id, evidence.filename) },
                        ) { Text("Récupérer le fichier") }
                    }
                }
            }
        }

        if (report.status == "pending_evidence") {
            Text("Ajouter la preuve manquante", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickScreenshot.launch("image/*") }) { Text("Capture") }
                OutlinedButton(onClick = { pickExport.launch("*/*") }) { Text("Export") }
                OutlinedButton(onClick = { pickHeader.launch("*/*") }) { Text("En-tête") }
            }
        }

        Text("Transmission à WhatsApp", fontWeight = FontWeight.SemiBold)
        when (report.status) {
            "pending_evidence" -> InfoCard(
                "Transmission impossible",
                "Ajoutez au moins une preuve : les signalements sans preuve sont automatiquement refusés, " +
                    "par conception et sans possibilité de désactivation.",
            )
            "pending_verification" -> InfoCard(
                "En cours de relecture",
                "Un modérateur humain vérifie la cohérence (contact réel, preuve, absence de doublon) avant " +
                    "toute transmission. C'est aussi ce qui protège les personnes injustement signalées.",
            )
            "rejected", "rejected_abusive" -> InfoCard(
                "Signalement refusé",
                "Il n'a pas été transmis. " + (report.decisionReason
                    ?: "Un signalement abusif confirmé est sanctionné : au-delà de 2, le compte est banni."),
            )
            else -> DisclaimerBanner(compact = true)
        }

        if (report.status == "verified" || report.status == "queued") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = adapter == "user_native" || adapter.isBlank(),
                    onClick = { adapter = "user_native" },
                    enabled = state.hasConnectedDevice,
                )
                Text(
                    if (state.hasConnectedDevice) "Depuis mon compte WhatsApp (appareil lié)"
                    else "Depuis mon compte WhatsApp — indisponible : aucun appareil connecté",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = adapter == "manual_guided",
                    onClick = { adapter = "manual_guided" },
                )
                Text(
                    "Parcours guidé : je reproduis le signalement dans WhatsApp, étape par étape",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.isBusiness) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = adapter == "cloud_api", onClick = { adapter = "cloud_api" })
                    Text(
                        "Via mon compte professionnel (WhatsApp Business Platform, API officielle)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = manualAck, onCheckedChange = { manualAck = it })
                Text(
                    "Je confirme avoir lu l'avertissement : tout faux signalement est passible de poursuites et " +
                        "d'un bannissement de mon compte WhatsApp ; ce service ne garantit aucune suspension.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = { vm.submit(adapter.ifBlank { "user_native" }) },
                enabled = manualAck && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Transmettre le signalement") }
        }

        if (state.guidedSteps.isNotEmpty()) {
            InfoCard(
                "Parcours guidé — étapes à suivre dans WhatsApp",
                state.guidedSteps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"),
            )
            OutlinedTextField(
                value = manualNote,
                onValueChange = { manualNote = it },
                label = { Text("Note (facultatif) : ce que vous avez observé") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { vm.markManualDone(manualNote) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("J'ai effectué le signalement dans WhatsApp") }
        }

        Text("Historique des transmissions", fontWeight = FontWeight.SemiBold)
        if (report.submissions.isEmpty()) {
            Text(
                "Aucune transmission enregistrée pour ce signalement.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            report.submissions.forEach { submission ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(submission.adapter, fontWeight = FontWeight.SemiBold)
                            StatusPill(submission.status, statusColor(submission.status))
                        }
                        Text(
                            "Tentatives : ${submission.attempts} · ${submission.responseSummary ?: submission.error ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        OutlinedButton(onClick = { vm.export(context.applicationContext) }, modifier = Modifier.fillMaxWidth()) {
            Text("Générer le récapitulatif (usage judiciaire)")
        }
        if (state.exportPath != null) {
            OutlinedButton(
                onClick = { vm.shareExport(context.applicationContext) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Partager le récapitulatif") }
        }
        Spacer(Modifier.height(8.dp))
        InfoCard(
            "Rappel",
            "SignalPro ne suspend aucun compte et ne peut pas le faire. Meta décide seule. Toute promesse " +
                "contraire serait mensongère.",
        )
    }
}
