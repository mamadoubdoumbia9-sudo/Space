package com.signalpro.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.DeviceDto
import com.signalpro.app.data.remote.GatewayStatusDto
import com.signalpro.app.data.remote.LinkStartResponse
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.LoadingBlock
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.StatusPill
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import com.signalpro.app.ui.rememberQrBitmap
import com.signalpro.app.ui.statusColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LinkDeviceViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val gateway: GatewayStatusDto? = null,
        val devices: List<DeviceDto> = emptyList(),
        val link: LinkStartResponse? = null,
        val deviceStatus: String? = null,
        val secondsLeft: Int = 0,
        val error: String? = null,
        val info: String? = null,
        val connected: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val gateway = container.deviceRepository.gatewayStatus()
            val devices = container.deviceRepository.devices()
            _state.value = _state.value.copy(
                loading = false,
                gateway = (gateway as? ApiResult.Success)?.data,
                devices = (devices as? ApiResult.Success)?.data ?: emptyList(),
                error = (gateway as? ApiResult.Failure)?.error?.message,
            )
        }
    }

    fun startLink(label: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, info = null)
            when (val result = container.deviceRepository.startLink(label, Disclaimer.CONSENT_VERSION)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        link = result.data,
                        deviceStatus = "pending",
                        secondsLeft = result.data.expiresIn,
                        info = if (result.data.pairingPayload.isNullOrBlank()) {
                            "La passerelle n'a pas fourni de QR : c'est le cas quand elle n'est pas " +
                                "joignable. Aucune liaison n'est simulée — relancez la passerelle et réessayez."
                        } else null,
                    )
                    result.data.pairingPayload?.let { beginPolling(result.data.deviceId) }
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.error.message)
            }
        }
    }

    /** Interroge réellement la passerelle : seul WhatsApp peut confirmer le scan. */
    private fun beginPolling(deviceId: Int) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val left = (_state.value.secondsLeft - 3).coerceAtLeast(0)
                _state.value = _state.value.copy(secondsLeft = left)
                when (val result = container.deviceRepository.confirmLink(deviceId)) {
                    is ApiResult.Success -> {
                        val device = result.data
                        _state.value = _state.value.copy(deviceStatus = device.status)
                        if (device.status == "connected" && device.waNumber != null) {
                            _state.value = _state.value.copy(
                                connected = true,
                                info = "Appareil lié (${device.waNumber}) · ${device.contactsCount} contacts " +
                                    "indexés. Les signalements peuvent désormais vérifier que le numéro vous a " +
                                    "bien contacté.",
                            )
                            refresh()
                            return@launch
                        }
                    }
                    is ApiResult.Failure -> {
                        if (result.error.statusCode != 0) {
                            _state.value = _state.value.copy(error = result.error.message)
                        }
                    }
                }
                if (left <= 0) {
                    _state.value = _state.value.copy(
                        error = "Le QR a expiré sans être scanné. Aucune liaison n'a été créée : relancez " +
                            "l'opération depuis WhatsApp (Appareils connectés).",
                    )
                    return@launch
                }
            }
        }
    }

    fun cancelLink() {
        pollJob?.cancel()
        val deviceId = _state.value.link?.deviceId
        _state.value = _state.value.copy(link = null, deviceStatus = null, secondsLeft = 0)
        if (deviceId != null) {
            viewModelScope.launch {
                container.deviceRepository.revoke(deviceId)
                refresh()
            }
        }
    }

    fun revoke(deviceId: Int) {
        viewModelScope.launch {
            when (val result = container.deviceRepository.revoke(deviceId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(info = "Appareil révoqué. Révoquez-le aussi dans " +
                        "WhatsApp → Appareils connectés : c'est WhatsApp qui détient la session.")
                    refresh()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }

    fun syncContacts(deviceId: Int) {
        viewModelScope.launch {
            when (val result = container.deviceRepository.syncContacts(deviceId)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(info = "Empreintes de conversations synchronisées " +
                        "(aucun contenu de message n'est transmis).")
                    refresh()
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }
}

@Composable
fun LinkDeviceScreen(
    container: AppContainer,
    cameraGranted: Boolean,
    requestCamera: () -> Unit,
    onLinked: () -> Unit,
) {
    val vm = containerViewModel<LinkDeviceViewModel> { LinkDeviceViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    var consent by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("Mon téléphone") }

    ScreenColumn {
        Text("Connexion WhatsApp", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Deux parcours possibles, tous deux réels :",
            style = MaterialTheme.typography.bodyMedium,
        )
        InfoCard(
            "1. Particulier — appareil lié (WhatsApp Web)",
            "Vous scannez un QR code depuis WhatsApp → Appareils connectés. SignalPro lit alors ce que " +
                "WhatsApp expose à un appareil lié (présence des conversations, envoi du signalement par " +
                "votre compte, blocage). " + Disclaimer.WA_WEB_RISK,
        )
        InfoCard(
            "2. Entreprise — WhatsApp Business Platform (API officielle)",
            "Un compte professionnel vérifié peut confirmer ses conversations et l'état de ses messages " +
                "via l'API Cloud. Aucune suspension n'est jamais demandée par cette API : Meta examine les " +
                "signalements et décide seul. Voir la section professionnelle dans les réglages.",
        )

        state.error?.let { ErrorText(it) }
        state.info?.let { SuccessText(it) }

        val gateway = state.gateway
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Passerelle locale", fontWeight = FontWeight.SemiBold)
                    StatusPill(
                        when {
                            gateway == null -> "état inconnu"
                            !gateway.configured -> "non configurée"
                            gateway.reachable -> "joignable"
                            else -> "injoignable"
                        },
                        statusColor(if (gateway?.reachable == true) "connected" else "pending"),
                    )
                }
                Text(
                    gateway?.reason ?: "La passerelle (dossier gateway/) tourne chez vous et parle à WhatsApp " +
                        "depuis votre propre connexion. Aucun identifiant WhatsApp n'est envoyé au serveur " +
                        "SignalPro : seules des empreintes HMAC le sont.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (state.loading && state.link == null && state.devices.isEmpty()) {
            LoadingBlock("Vérification de la passerelle…")
        }

        if (state.link?.pairingPayload.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Text(
                    "Je comprends et j'accepte le risque : utiliser un appareil lié peut entraîner une " +
                        "restriction de mon compte WhatsApp, même en respectant les limites.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Nom de l'appareil (visible dans WhatsApp)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.startLink(label) },
                enabled = consent && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Démarrer la liaison") }
            Text(
                "Sans appareil lié, vous pouvez tout de même signaler : la vérification « ce numéro m'a " +
                    "contacté » se fait alors par capture d'écran de la conversation, contrôlée par nos " +
                    "modérateurs.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val payload = state.link?.pairingPayload.orEmpty()
            val qr = rememberQrBitmap(payload)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Scannez ce QR dans WhatsApp", fontWeight = FontWeight.SemiBold)
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = "QR de couplage WhatsApp", modifier = Modifier.size(260.dp))
                    } else {
                        Text("QR indisponible : la charge de couplage est vide.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "WhatsApp → Réglages → Appareils connectés → Lier un appareil",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    StatusPill(
                        "statut : ${state.deviceStatus ?: "pending"} · ${state.secondsLeft}s restantes",
                        statusColor(state.deviceStatus ?: "pending"),
                    )
                    Text(
                        "Le statut ci-dessus provient de la passerelle : il ne passe à « connecté » que " +
                            "lorsque WhatsApp a réellement confirmé la session.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!cameraGranted) {
                            TextButton(onClick = requestCamera) { Text("Autoriser la caméra") }
                        }
                        TextButton(onClick = { vm.cancelLink() }) { Text("Annuler") }
                    }
                }
            }
            if (state.deviceStatus == "connected") {
                Button(onClick = onLinked, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
            }
        }

        if (state.devices.isNotEmpty()) {
            Text("Mes appareils", fontWeight = FontWeight.SemiBold)
            state.devices.forEach { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(device.label, fontWeight = FontWeight.SemiBold)
                            StatusPill(device.status, statusColor(device.status))
                        }
                        Text(
                            "Numéro : ${device.waNumber ?: "—"} · contacts indexés : ${device.contactsCount} · " +
                                "lié le ${device.linkedAt?.take(16) ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.syncContacts(device.id) },
                                enabled = device.status == "connected",
                            ) { Text("Synchroniser les conversations") }
                            TextButton(onClick = { vm.revoke(device.id) }) { Text("Révoquer") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoCard(
            "Ce que la passerelle fait et ne fait pas",
            "Elle expose uniquement : état de la session, empreintes de vos conversations, envoi d'un " +
                "signalement par votre compte (si WhatsApp l'autorise sur votre version), blocage/déblocage " +
                "d'un contact. Elle ne lit ni ne stocke le contenu de vos messages, et elle n'a aucun moyen " +
                "de « faire bannir » quelqu'un : cela n'existe pas.",
        )
    }
}
