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
import com.signalpro.app.data.remote.CampaignDto
import com.signalpro.app.data.remote.CampaignPreviewDto
import com.signalpro.app.data.remote.CampaignPreviewRequest
import com.signalpro.app.domain.Validation
import com.signalpro.app.ui.DisclaimerBanner
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.StatusPill
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import com.signalpro.app.ui.statusColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CampaignViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val preview: CampaignPreviewDto? = null,
        val campaigns: List<CampaignDto> = emptyList(),
        val created: CampaignDto? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val list = container.campaignRepository.list()
            _state.value = _state.value.copy(
                campaigns = (list as? ApiResult.Success)?.data ?: emptyList(),
                error = (list as? ApiResult.Failure)?.error?.message,
            )
        }
    }

    fun preview(request: CampaignPreviewRequest) {
        val problem = validate(request)
        if (problem != null) {
            _state.value = _state.value.copy(error = problem)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, created = null)
            when (val result = container.campaignRepository.preview(request)) {
                is ApiResult.Success -> _state.value = _state.value.copy(busy = false, preview = result.data)
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun create(request: CampaignPreviewRequest) {
        val problem = validate(request)
        if (problem != null) {
            _state.value = _state.value.copy(error = problem)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            when (val result = container.campaignRepository.create(request)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        busy = false,
                        created = result.data,
                        info = "Demande enregistrée. Seuls les comptes réellement concernés exécuteront un " +
                            "signalement : ${result.data.executedCount} sur ${result.data.requestedCount} " +
                            "demandés.",
                    )
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(busy = false, error = result.error.message)
            }
        }
    }

    fun cancel(campaignId: Int) {
        viewModelScope.launch {
            when (val result = container.campaignRepository.cancel(campaignId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(info = "Campagne ${result.data.publicRef} arrêtée.")
                    load()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    private fun validate(request: CampaignPreviewRequest): String? =
        Validation.phoneError(request.targetPhone)
            ?: if (request.category.isBlank()) "Choisissez la catégorie d'infraction." else null
            ?: Validation.occurredAtError(request.occurredAt)
            ?: Validation.descriptionError(request.description)
            ?: if (!request.consentAck) {
                "Vous devez confirmer avoir lu l'avertissement (faux signalement = poursuites + bannissement)."
            } else null
}

@Composable
fun CampaignScreen(container: AppContainer) {
    val vm = containerViewModel<CampaignViewModel> { CampaignViewModel(it) }
    val state by vm.state.collectAsStateSafe()

    val defaultDate = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date()) }
    var targetPhone by remember { mutableStateOf("") }
    var requestedCount by remember { mutableStateOf("3") }
    var category by remember { mutableStateOf("") }
    var occurredAt by remember { mutableStateOf(defaultDate) }
    var description by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    fun buildRequest() = CampaignPreviewRequest(
        targetPhone = Validation.normalizePhone(targetPhone),
        requestedCount = requestedCount.toIntOrNull() ?: 0,
        category = category,
        occurredAt = Validation.toApiTimestamp(occurredAt),
        description = description.trim(),
        consentAck = consent,
    )

    ScreenColumn {
        Text(
            "Signaler avec plusieurs comptes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        InfoCard(
            "Comment fonctionne réellement un signalement groupé",
            "Vous indiquez un numéro et le nombre de signalements souhaité (1 à 500). Le serveur calcule " +
                "combien de personnes distinctes ont réellement reçu des messages de ce numéro et ont lié " +
                "leur WhatsApp : c'est le seul volume honnêtement exécutable. SignalPro ne crée jamais de " +
                "comptes ni de signalements fictifs, et n'enverra jamais un signalement « pour vous ». " +
                "Aucune suspension n'est automatique : Meta examine chaque signalement.",
        )
        DisclaimerBanner(full = true)

        OutlinedTextField(
            value = targetPhone,
            onValueChange = { targetPhone = it },
            label = { Text("Numéro à signaler") },
            supportingText = { Text("Format international, ex. +223 61 23 45 67") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = requestedCount,
            onValueChange = { requestedCount = it.filter { ch -> ch.isDigit() }.take(3) },
            label = { Text("Nombre de signalements demandé (1 à 500)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(Disclaimer.categoryLabel(category).ifBlank { "Choisir la catégorie d'infraction" })
        }
        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
            Disclaimer.CATEGORIES.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { category = key; categoryMenu = false })
            }
        }
        OutlinedTextField(
            value = occurredAt,
            onValueChange = { occurredAt = it },
            label = { Text("Date et heure de réception (jj/MM/aaaa hh:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description commune des faits") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text(
                "Je confirme avoir lu l'avertissement : un faux signalement est passible de poursuites " +
                    "judiciaires et entraîne le bannissement de mon compte SignalPro.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.preview(buildRequest()) }, enabled = !state.busy) {
                Text("Vérifier ce qui est possible")
            }
            Button(onClick = { vm.create(buildRequest()) }, enabled = !state.busy && consent) {
                Text("Créer la demande")
            }
        }

        state.preview?.let { preview ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ce qui est réellement exécutable", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Demandés : ${preview.requestedCount} · comptes éligibles : ${preview.eligibleAccounts} · " +
                            "exécutables : ${preview.executableCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Vos appareils connectés : ${preview.yourConnectedDevices} · numéro déjà connu : " +
                            if (preview.targetInDatabase) "oui" else "non",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(preview.explanation, style = MaterialTheme.typography.bodySmall)
                    preview.blockingReason?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        state.created?.let { campaign ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(campaign.publicRef, fontWeight = FontWeight.SemiBold)
                        StatusPill(campaign.status, statusColor(campaign.status))
                    }
                    Text(
                        "Exécutés : ${campaign.executedCount} · réussis : ${campaign.succeededCount} · " +
                            "éligibles : ${campaign.eligibleCount}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    campaign.eligibilityExplanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    campaign.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    campaign.lastError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Mes demandes groupées", fontWeight = FontWeight.SemiBold)
        if (state.campaigns.isEmpty()) {
            Text("Aucune demande groupée enregistrée.", style = MaterialTheme.typography.bodySmall)
        } else {
            state.campaigns.forEach { campaign ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(campaign.targetPhoneMasked, fontWeight = FontWeight.SemiBold)
                            StatusPill(campaign.status, statusColor(campaign.status))
                        }
                        Text(
                            "demandés ${campaign.requestedCount} · exécutés ${campaign.executedCount} · " +
                                "réussis ${campaign.succeededCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (campaign.status !in listOf("completed", "aborted", "cancelled")) {
                            OutlinedButton(onClick = { vm.cancel(campaign.id) }) { Text("Arrêter") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { vm.load() }, modifier = Modifier.fillMaxWidth()) { Text("Actualiser la liste") }
    }
}
