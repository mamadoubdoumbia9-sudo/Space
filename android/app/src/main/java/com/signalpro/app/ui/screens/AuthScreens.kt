package com.signalpro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.signalpro.app.AppContainer
import com.signalpro.app.core.domain.Disclaimer
import com.signalpro.app.data.remote.ApiResult
import com.signalpro.app.data.remote.RegisterRequest
import com.signalpro.app.domain.Validation
import com.signalpro.app.ui.DisclaimerBanner
import com.signalpro.app.ui.InfoCard
import com.signalpro.app.ui.ScreenColumn
import com.signalpro.app.ui.ServerUrlCard
import com.signalpro.app.ui.collectAsStateSafe
import com.signalpro.app.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Connexion
// ---------------------------------------------------------------------------
class LoginViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(val loading: Boolean = false, val error: String? = null, val success: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = UiState(error = "Email et mot de passe obligatoires.")
            return
        }
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            when (val result = container.authRepository.login(email.trim(), password)) {
                is ApiResult.Success -> {
                    // Les signatures de détection sont téléchargées dès l'ouverture de session :
                    // l'analyse hors ligne est ainsi disponible immédiatement.
                    container.detectionRepository.refreshSignatures("")
                    container.settingsStore.setSignatureVersion("")
                    _state.value = UiState(success = true)
                }
                is ApiResult.Failure -> _state.value = UiState(error = result.error.message)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

@Composable
fun LoginScreen(
    container: AppContainer,
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
) {
    val vm = containerViewModel<LoginViewModel> { LoginViewModel(it) }
    val state by vm.state.collectAsStateSafe()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.success) { if (state.success) onLoggedIn() }

    ScreenColumn {
        Text("SignalPro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Signalement documenté d'arnaques, de spam et de harcèlement sur WhatsApp",
            style = MaterialTheme.typography.bodyMedium,
        )
        DisclaimerBanner()

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; vm.clearError() },
            label = { Text("Adresse email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; vm.clearError() },
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { ErrorText(it) }

        Button(
            onClick = { vm.login(email, password) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("Se connecter")
        }
        OutlinedButton(onClick = onGoRegister, modifier = Modifier.fillMaxWidth()) {
            Text("Créer un compte")
        }
        // Placé avant les identifiants : si l'utilisateur arrive avec une adresse de
        // serveur absente ou erronée, c'est ici — et non après un échec obscur — qu'il
        // doit pouvoir la corriger.
        ServerUrlCard(container)

        InfoCard(
            "Vérification obligatoire",
            "Chaque compte doit être vérifié par email ou par SMS : c'est ce qui empêche la création de " +
                "comptes destinés à de faux signalements. Au-delà de 2 signalements jugés abusifs après " +
                "vérification, le compte est définitivement banni de l'application.",
        )
    }
}

// ---------------------------------------------------------------------------
// Inscription
// ---------------------------------------------------------------------------
class RegisterViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val registeredEmail: String? = null,
        val deliveryMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun register(email: String, phone: String, password: String, channel: String, accepted: Boolean) {
        val normalizedPhone = Validation.normalizePhone(phone)
        val phoneError = Validation.phoneError(phone)
        when {
            email.isBlank() || !email.contains('@') -> {
                _state.value = UiState(error = "Email invalide.")
                return
            }
            phoneError != null -> {
                _state.value = UiState(error = phoneError)
                return
            }
            password.length < 10 -> {
                _state.value = UiState(
                    error = "Mot de passe : 10 caractères minimum, avec au moins une lettre et un chiffre.",
                )
                return
            }
            !accepted -> {
                _state.value = UiState(error = "Vous devez accepter les conditions et l'avertissement légal.")
                return
            }
        }
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            val request = RegisterRequest(
                email = email.trim(),
                phone = normalizedPhone,
                password = password,
                channel = channel,
            )
            when (val result = container.authRepository.register(request)) {
                is ApiResult.Success -> _state.value = UiState(
                    registeredEmail = email.trim(),
                    deliveryMessage = result.data.deliveryDetail,
                )
                is ApiResult.Failure -> _state.value = UiState(error = result.error.message)
            }
        }
    }
}

@Composable
fun RegisterScreen(
    container: AppContainer,
    onRegistered: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm = containerViewModel<RegisterViewModel> { RegisterViewModel(it) }
    val state by vm.state.collectAsStateSafe()

    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+223") }
    var password by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("email") }
    var accepted by remember { mutableStateOf(false) }

    LaunchedEffect(state.registeredEmail) { state.registeredEmail?.let(onRegistered) }

    ScreenColumn {
        Text("Créer un compte", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Un email ET un numéro de téléphone sont exigés, tous deux vérifiés : c'est la première " +
                "protection contre les faux signalements en série.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Adresse email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Numéro de téléphone (format international)") },
            supportingText = { Text("Ex. +223 61 23 45 67 — le numéro doit être joignable par WhatsApp.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe (10 caractères minimum)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Recevoir le code de vérification :", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { channel = "email" }, enabled = channel != "email") { Text("Par email") }
            OutlinedButton(onClick = { channel = "sms" }, enabled = channel != "sms") { Text("Par SMS") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text(
                "J'accepte les conditions d'utilisation et la politique de confidentialité, et je comprends " +
                    "qu'un faux signalement est passible de poursuites judiciaires et du bannissement de mon " +
                    "propre compte WhatsApp.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.error?.let { ErrorText(it) }
        // Rappel de l'adresse réellement contactée : le message d'erreur réseau cite
        // l'hôte, cet encart permet de le corriger immédiatement.
        if (state.error != null) ServerUrlCard(container, title = "Serveur contacté")
        state.deliveryMessage?.let { InfoCard("Code de vérification", it) }

        Button(
            onClick = { vm.register(email, phone, password, channel, accepted) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("Créer mon compte")
        }
        TextButton(onClick = onBack) { Text("J'ai déjà un compte") }
        Spacer(Modifier.height(4.dp))
        DisclaimerBanner(full = true)
    }
}

// ---------------------------------------------------------------------------
// Vérification (email ou SMS) — étape bloquante avant tout accès
// ---------------------------------------------------------------------------
class VerifyViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val info: String? = null,
        val verified: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun verify(email: String, code: String) {
        if (code.trim().length < 4) {
            _state.value = UiState(error = "Saisissez le code complet reçu.")
            return
        }
        _state.value = UiState(loading = true)
        viewModelScope.launch {
            when (val result = container.authRepository.verify(email, code.trim())) {
                is ApiResult.Success -> {
                    container.detectionRepository.refreshSignatures("")
                    val profile = container.authRepository.loadProfile()
                    _state.value = UiState(
                        verified = true,
                        info = if (profile?.isVerified == false) {
                            "Code accepté pour un autre canal : la vérification complète (email + téléphone) " +
                                "reste nécessaire avant l'envoi de signalements."
                        } else null,
                    )
                }
                is ApiResult.Failure -> _state.value = UiState(error = result.error.message)
            }
        }
    }

    fun resend(email: String, password: String) {
        if (password.isBlank()) {
            _state.value = UiState(error = "Indiquez votre mot de passe pour renvoyer un code.")
            return
        }
        viewModelScope.launch {
            when (val result = container.authRepository.resendCode(email, password)) {
                is ApiResult.Success -> _state.value = UiState(
                    info = "Nouveau code envoyé. " + (result.data.detail ?: ""),
                )
                is ApiResult.Failure -> _state.value = UiState(error = result.error.message)
            }
        }
    }
}

@Composable
fun VerifyScreen(container: AppContainer, email: String, onVerified: () -> Unit) {
    val vm = containerViewModel<VerifyViewModel> { VerifyViewModel(it) }
    val state by vm.state.collectAsStateSafe()
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.verified) { if (state.verified) onVerified() }

    ScreenColumn {
        Text("Vérification obligatoire", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Compte à vérifier : $email", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Code à 6 chiffres") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        state.error?.let { ErrorText(it) }
        state.info?.let { InfoCard("Information", it) }
        Button(
            onClick = { vm.verify(email, code) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("Vérifier")
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe (pour renvoyer un code)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.resend(email, password) }, enabled = password.isNotBlank()) {
            Text("Renvoyer un code")
        }
        InfoCard("Pourquoi cette étape est obligatoire", Disclaimer.SHORT)
    }
}

// ---------------------------------------------------------------------------
// Composants partagés par les écrans
// ---------------------------------------------------------------------------
@Composable
fun ErrorText(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun SuccessText(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
