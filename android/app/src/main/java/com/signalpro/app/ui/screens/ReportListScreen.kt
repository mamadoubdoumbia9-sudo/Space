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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.data.local.CachedReport
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.UsageDto
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

class ReportListViewModel(private val container: AppContainer) : ViewModel() {

    data class ImportSummary(
        val filename: String,
        val total: Int,
        val valid: Int,
        val rejected: Int,
        val committed: Boolean,
        val created: Int,
        val errors: List<String>,
    )

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val usage: UsageDto? = null,
        val exportPath: String? = null,
        val importSummary: ImportSummary? = null,
        val importedFile: File? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val reports = container.reportRepository.refresh()
            val usage = container.reportRepository.usage()
            _state.value = _state.value.copy(
                loading = false,
                error = (reports as? ApiResult.Failure)?.error?.message
                    ?: (usage as? ApiResult.Failure)?.error?.message,
                usage = (usage as? ApiResult.Success)?.data,
            )
        }
    }

    /** Export réel : CSV produit par le serveur à partir de mes signalements. */
    fun exportCsv(context: Context) {
        viewModelScope.launch {
            when (val result = container.reportRepository.exportCsv()) {
                is ApiResult.Success -> {
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        File(dir, "mes_signalements.csv").apply { writeText(result.data) }
                    }
                    _state.value = _state.value.copy(
                        exportPath = file.absolutePath,
                        info = "Export prêt : ${file.length()} octets.",
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun shareExport(context: Context) {
        val path = _state.value.exportPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "Le fichier d'export n'existe plus.")
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mes signalements SignalPro")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Partager mes signalements"))
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Partage impossible.")
        }
    }

    fun stageImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "imports").apply { mkdirs() }
                val target = File(dir, "import_${System.currentTimeMillis()}.csv")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
            _state.value = _state.value.copy(importedFile = file, info = null, error = null)
            previewImport(file)
        }
    }

    fun previewImport(file: File) {
        viewModelScope.launch {
            when (val result = container.reportRepository.importPreview(file)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    importSummary = parseSummary(result.data, committed = false),
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun commitImport() {
        val file = _state.value.importedFile ?: return
        viewModelScope.launch {
            when (val result = container.reportRepository.importCommit(file)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        importSummary = parseSummary(result.data, committed = true),
                        info = "Import exécuté : chaque ligne valide a créé un signalement réel.",
                    )
                    refresh()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    private fun parseSummary(payload: Map<String, JsonElement>, committed: Boolean): ImportSummary {
        val root = JsonObject(payload)
        val errors = root["rows"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val line = (obj["line"] as? JsonPrimitive)?.int ?: return@mapNotNull null
            val messages = obj["errors"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            if (messages.isEmpty()) null else "Ligne $line : ${messages.joinToString(" ")}"
        }
        return ImportSummary(
            filename = (root["filename"] as? JsonPrimitive)?.content ?: "import.csv",
            total = (root["total_rows"] as? JsonPrimitive)?.int ?: 0,
            valid = (root["valid_rows"] as? JsonPrimitive)?.int ?: 0,
            rejected = (root["rejected_rows"] as? JsonPrimitive)?.int ?: 0,
            committed = (root["committed"] as? JsonPrimitive)?.content?.toBoolean() ?: committed,
            created = root["created_report_ids"]?.jsonArray?.size ?: 0,
            errors = errors,
        )
    }
}

@Composable
fun ReportListScreen(
    container: AppContainer,
    onOpen: (Int) -> Unit,
    onNew: () -> Unit,
) {
    val vm = containerViewModel<ReportListViewModel> { ReportListViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    val context = LocalContext.current
    val cached by container.reportRepository.observeCachedReports()
        .collectAsStateWithLifecycle(initialValue = emptyList<CachedReport>())

    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.stageImport(context.applicationContext, it) }
    }

    val filtered = remember(cached, filter) {
        if (filter.isBlank()) cached else cached.filter { it.status == filter }
    }

    ScreenColumn {
        Text("Mes signalements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        state.usage?.let { usage ->
            InfoCard(
                "Quotas appliqués (non désactivables)",
                "${usage.reportsLastHour}/${usage.hourlyLimit} cette heure · " +
                    "${usage.reportsLastDay}/${usage.dailyLimit} aujourd'hui · reste ${usage.remainingToday} · " +
                    "avertissements d'abus ${usage.strikes}/${usage.banThreshold}",
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNew) { Text("Nouveau") }
            OutlinedButton(onClick = { vm.refresh() }, enabled = !state.loading) { Text("Actualiser") }
            OutlinedButton(onClick = { vm.exportCsv(context.applicationContext) }) { Text("Export CSV") }
        }
        if (state.exportPath != null) {
            OutlinedButton(
                onClick = { vm.shareExport(context.applicationContext) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Partager le fichier exporté") }
        }

        Text("Import CSV / Excel", fontWeight = FontWeight.SemiBold)
        Text(
            "Colonnes attendues : numéro, catégorie, date, preuve (identifiants de message ou référence de " +
                "pièce). Toute ligne sans catégorie ou sans preuve est REJETÉE : elle n'est jamais transmise. " +
                "Plafond 5 Mo, 50 créations par import, quotas journaliers appliqués en plus.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { pickImport.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
            Text("Choisir un fichier .csv / .xlsx")
        }
        state.importSummary?.let { summary ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary.filename, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Lignes : ${summary.total} · valides : ${summary.valid} · rejetées : ${summary.rejected}" +
                            if (summary.committed) " · signalements créés : ${summary.created}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    summary.errors.take(10).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (!summary.committed) {
                        Button(
                            onClick = { vm.commitImport() },
                            enabled = summary.valid > 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Importer les ${summary.valid} lignes valides") }
                    }
                }
            }
        }

        Text("Filtrer par statut", fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "" to "Tous",
                "pending_evidence" to "Preuve manquante",
                "pending_verification" to "En relecture",
                "verified" to "Vérifiés",
                "submitted" to "Transmis",
                "rejected" to "Rejetés",
            ).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (key, label) ->
                        OutlinedButton(onClick = { filter = key }, enabled = filter != key) { Text(label) }
                    }
                }
            }
        }

        when {
            state.loading && cached.isEmpty() -> LoadingBlock("Chargement de vos signalements…")
            filtered.isEmpty() -> InfoCard(
                "Aucun signalement à afficher",
                "Vos signalements apparaissent ici dès leur enregistrement, y compris hors ligne.",
            )
            else -> filtered.forEach { report ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(report.targetMasked, fontWeight = FontWeight.SemiBold)
                            StatusPill(report.statusLabel, statusColor(report.status))
                        }
                        Text(
                            "${report.categoryLabel} · ${formatTimestamp(report.occurredAt)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "${report.publicRef} · ${report.evidenceCount} preuve(s)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { onOpen(report.id) }) { Text("Ouvrir le détail") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
