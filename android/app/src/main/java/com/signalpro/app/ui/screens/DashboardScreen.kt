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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.signalpro.app.data.local.CachedAlert
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.DeviceDto
import com.signalpro.app.data.remote.GatewayStatusDto
import com.signalpro.app.data.remote.OverviewDto
import com.signalpro.app.ui.Accent
import com.signalpro.app.ui.DisclaimerBanner
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.LoadingBlock
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.StatusPill
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import com.signalpro.app.ui.statusColor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val overview: OverviewDto? = null,
        val devices: List<DeviceDto> = emptyList(),
        val alerts: List<CachedAlert> = emptyList(),
        val pendingDrafts: Int = 0,
        val signatureCount: Int = 0,
        val gateway: GatewayStatusDto? = null,
        val info: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        load()
        viewModelScope.launch {
            container.communityRepository.observeAlerts().collect { alerts ->
                _state.value = _state.value.copy(alerts = alerts)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val overview = async { container.apiClient.call { container.api.overview() } }
            val devices = async { container.deviceRepository.devices() }
            val gateway = async { container.apiClient.call { container.api.gatewayStatus() } }

            val overviewResult = overview.await()
            val deviceResult = devices.await()

            var error: String? = null
            if (overviewResult is ApiResult.Failure) error = overviewResult.error.message
            else if (deviceResult is ApiResult.Failure) error = deviceResult.error.message

            _state.value = _state.value.copy(
                loading = false,
                error = error,
                overview = (overviewResult as? ApiResult.Success)?.data,
                devices = (deviceResult as? ApiResult.Success)?.data ?: emptyList(),
                gateway = (gateway.await() as? ApiResult.Success)?.data,
                pendingDrafts = container.reportRepository.pendingDrafts().size,
                signatureCount = container.detectionRepository.signaturesCount(),
            )
        }
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            when (val result = container.communityRepository.scanAlerts()) {
                is ApiResult.Success -> {
                    container.communityRepository.refreshAlerts()
                    _state.value = _state.value.copy(info = "Analyse terminée : les correspondances sont listées ci-dessous.")
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    /** Déclaration honnête : l'utilisateur a lui-même vérifié la suspension dans WhatsApp. */
    fun declareSuspension(phone: String, note: String) {
        viewModelScope.launch {
            when (val result = container.deviceRepository.reportSuspension(phone, note)) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    info = "Déclaration enregistrée : elle sera vérifiée par un modérateur avant d'être comptée.",
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    container: AppContainer,
    displayName: String,
    onOpenLink: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenCampaign: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = containerViewModel<DashboardViewModel> { DashboardViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    var suspensionPhone by remember { mutableStateOf("") }
    var suspensionNote by remember { mutableStateOf("") }

    ScreenColumn {
        Text(
            "Bonjour ${displayName.ifBlank { "—" }}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("Tableau de bord de vos signalements", style = MaterialTheme.typography.bodySmall)
        DisclaimerBanner()

        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        if (state.loading && state.overview == null) {
            LoadingBlock("Chargement de votre tableau de bord…")
        } else {
            val usage = state.overview?.usage
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Limites et état du compte", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Signalements dans l'heure : ${usage?.reportsLastHour ?: 0}/${usage?.hourlyLimit ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Signalements aujourd'hui : ${usage?.reportsLastDay ?: 0}/${usage?.dailyLimit ?: "—"} " +
                            "· reste ${usage?.remainingToday ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Actions : ${usage?.let { "10 par minute maximum" } ?: "—"} · avertissements d'abus : " +
                            "${usage?.strikes ?: 0}/${usage?.banThreshold ?: 3}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            usage?.status ?: "—",
                            statusColor(usage?.status ?: ""),
                        )
                        StatusPill("${state.pendingDrafts} brouillon(s)", statusColor("pending"))
                    }
                }
            }

            val totals = state.overview?.reports ?: emptyMap()
            InfoCard(
                "Mes signalements",
                "Total : ${totals["total"] ?: 0} · vérifiés : ${totals["verified"] ?: 0} · " +
                    "transmis : ${totals["submitted"] ?: 0} · refusés : ${totals["rejected"] ?: 0}",
            )

            val suspended = state.overview?.suspensions?.get("confirmed_from_my_reports")?.jsonPrimitive?.int ?: 0
            InfoCard(
                "Comptes suspendus confirmés : $suspended",
                state.overview?.suspensions?.get("note")?.jsonPrimitive?.content
                    ?: "Seules les suspensions confirmées par Meta ou constatées par un modérateur sont comptées.",
                tint = Accent,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Appareil WhatsApp lié", fontWeight = FontWeight.SemiBold)
                    val device = state.devices.firstOrNull { it.status == "connected" }
                    if (device == null) {
                        Text(
                            "Aucun appareil lié. Sans appareil lié, la vérification « ce numéro vous a bien " +
                                "contacté » se fait manuellement (capture d'écran de la conversation) et les " +
                                "signalements sont transmis par le parcours guidé WhatsApp.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenLink) { Text("Lier mon WhatsApp") }
                    } else {
                        Text(
                            "Lié : ${device.label} (${device.waNumber ?: "numéro masqué"}) · " +
                                "${device.contactsCount} contacts indexés",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onOpenLink) { Text("Gérer l'appareil lié") }
                    }
                    state.gateway?.let { gateway ->
                        Text(
                            if (!gateway.configured) {
                                "Passerelle locale non configurée sur le serveur : le mode appareil lié est " +
                                    "indisponible. Le reste de l'application fonctionne normalement."
                            } else {
                                "Passerelle : ${if (gateway.reachable) "joignable" else "non joignable"} " +
                                    "(v${gateway.version ?: "?"}) · signalement natif : " +
                                    "${if (gateway.capabilities?.reportNative == true) "activé" else "désactivé"}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Text("Actions", fontWeight = FontWeight.SemiBold)
            Button(onClick = onOpenReport, modifier = Modifier.fillMaxWidth()) {
                Text("Signaler un numéro (preuve obligatoire)")
            }
            OutlinedButton(onClick = onOpenCampaign, modifier = Modifier.fillMaxWidth()) {
                Text("Signalement groupé : choisir le nombre de signalements")
            }
            OutlinedButton(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                Text("Mes signalements et leur état")
            }
            OutlinedButton(onClick = { vm.refreshAlerts() }, modifier = Modifier.fillMaxWidth()) {
                Text("Vérifier si un numéro malveillant me contacte")
            }

            if (state.alerts.isNotEmpty()) {
                Text("Alertes : numéros malveillants en contact avec vous", fontWeight = FontWeight.SemiBold)
                state.alerts.forEach { alert ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(alert.phoneMasked, fontWeight = FontWeight.SemiBold)
                                StatusPill("${alert.verifiedReports} signalements vérifiés", statusColor("verified"))
                            }
                            Text("${alert.categoryLabel} · ${alert.advice}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Conseil : ne répondez pas, n'ouvrez aucun lien, conservez les messages, " +
                                    "puis bloquez ce numéro (onglet Communauté).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Déclarer une suspension constatée", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Si WhatsApp vous a informé qu'un numéro que vous avez signalé a été suspendu, " +
                            "déclarez-le ici. La déclaration est journalisée puis vérifiée : elle n'est jamais " +
                            "comptée automatiquement comme une suspension confirmée.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = suspensionPhone,
                        onValueChange = { suspensionPhone = it },
                        label = { Text("Numéro concerné (+223…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = suspensionNote,
                        onValueChange = { suspensionNote = it },
                        label = { Text("Ce que WhatsApp vous a indiqué") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = { vm.declareSuspension(suspensionPhone, suspensionNote) },
                        enabled = suspensionPhone.isNotBlank(),
                    ) { Text("Enregistrer la déclaration") }
                }
            }

            InfoCard(
                "Signatures de détection : ${state.signatureCount}",
                "Elles sont téléchargées depuis votre compte SignalPro et permettent l'analyse hors ligne " +
                    "des extraits que vous soumettez volontairement. Aucune conversation n'est envoyée ni stockée.",
            )

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Réglages, confidentialité et suppression du compte")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
