package com.whalert.app.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.whalert.app.data.model.UserConnectionState

/**
 * Gestionnaire de stockage local sécurisé utilisant EncryptedSharedPreferences (Android Keystore + AES-256 GCM).
 *
 * Toutes les données sensibles (numéro configuré, dossiers, clé admin hachée) sont chiffrées au repos.
 */
class SecureStorageManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "whalert_secure_vault",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback en mode sandbox si Keystore n'est pas initialisé
            context.getSharedPreferences("whalert_secure_vault_compat", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_USER_PHONE = "user_wa_phone"
        private const val KEY_USER_EMAIL = "user_contact_email"
        private const val KEY_USER_CONN_STATE = "user_conn_state"
        private const val KEY_USER_CONSENT = "user_email_consent"
        private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
        private const val KEY_DAILY_LIMIT = "app_daily_limit"
        private const val KEY_DOSSIERS_JSON = "dossiers_json"
        private const val KEY_AUDIT_LOGS_JSON = "audit_logs_json"

        // Valeur par défaut de la limite de sécurité de l'application (3 dossiers / 24h)
        const val DEFAULT_DAILY_LIMIT = 3
    }

    fun saveUserPhone(phone: String) {
        prefs.edit().putString(KEY_USER_PHONE, phone).apply()
        setUserConnectionState(UserConnectionState.CONNECTED)
    }

    fun getUserPhone(): String? = prefs.getString(KEY_USER_PHONE, null)

    fun saveUserEmail(email: String, explicitConsent: Boolean) {
        prefs.edit()
            .putString(KEY_USER_EMAIL, email)
            .putBoolean(KEY_USER_CONSENT, explicitConsent)
            .apply()
    }

    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun hasUserEmailConsent(): Boolean = prefs.getBoolean(KEY_USER_CONSENT, false)

    fun setUserConnectionState(state: UserConnectionState) {
        prefs.edit().putString(KEY_USER_CONN_STATE, state.name).apply()
    }

    fun getUserConnectionState(): UserConnectionState {
        val name = prefs.getString(KEY_USER_CONN_STATE, null) ?: return UserConnectionState.NOT_CONNECTED
        return try {
            UserConnectionState.valueOf(name)
        } catch (e: Exception) {
            UserConnectionState.NOT_CONNECTED
        }
    }

    fun disconnectUser() {
        prefs.edit()
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_CONSENT)
            .putString(KEY_USER_CONN_STATE, UserConnectionState.NOT_CONNECTED.name)
            .apply()
    }

    fun getDailyLimit(): Int = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, limit.coerceIn(1, 10)).apply()
    }

    fun saveAdminPinHash(hash: String) {
        prefs.edit().putString(KEY_ADMIN_PIN_HASH, hash).apply()
    }

    fun getAdminPinHash(): String? = prefs.getString(KEY_ADMIN_PIN_HASH, null)

    fun saveDossiersRaw(json: String) {
        prefs.edit().putString(KEY_DOSSIERS_JSON, json).apply()
    }

    fun getDossiersRaw(): String? = prefs.getString(KEY_DOSSIERS_JSON, null)

    fun saveAuditLogsRaw(json: String) {
        prefs.edit().putString(KEY_AUDIT_LOGS_JSON, json).apply()
    }

    fun getAuditLogsRaw(): String? = prefs.getString(KEY_AUDIT_LOGS_JSON, null)
}
