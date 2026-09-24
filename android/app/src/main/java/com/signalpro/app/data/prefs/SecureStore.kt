package com.signalpro.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Jetons et données sensibles : chiffrés par Android Keystore
 * (EncryptedSharedPreferences, AES-256-GCM, clé non exportable).
 *
 * Rien n'est écrit en clair, ni sauvegardé dans le cloud (voir
 * data_extraction_rules.xml : sauvegarde désactivée).
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "signalpro_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var userId: Int
        get() = prefs.getInt(KEY_USER_ID, 0)
        set(value) = prefs.edit().putInt(KEY_USER_ID, value).apply()

    var isModerator: Boolean
        get() = prefs.getBoolean(KEY_MODERATOR, false)
        set(value) = prefs.edit().putBoolean(KEY_MODERATOR, value).apply()

    var displayName: String?
        get() = prefs.getString(KEY_DISPLAY_NAME, null)
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    /** Numéro de la cible en cours de signalement, partagé depuis une autre app. */
    var pendingSharedText: String?
        get() = prefs.getString(KEY_SHARED, null)
        set(value) = prefs.edit().putString(KEY_SHARED, value).apply()

    fun saveTokens(access: String, refresh: String) {
        prefs.edit().putString(KEY_ACCESS, access).putString(KEY_REFRESH, refresh).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_MODERATOR = "is_moderator"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_SHARED = "pending_shared_text"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "signalpro_settings")

/** Préférences non sensibles (affichage, alertes, dernière version de signatures). */
class SettingsStore(private val context: Context) {

    val alertsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALERTS] ?: true }
    val wifiOnlySync: Flow<Boolean> = context.dataStore.data.map { it[KEY_WIFI_ONLY] ?: false }
    val signatureVersion: Flow<String> = context.dataStore.data.map { it[KEY_SIG_VERSION] ?: "" }

    suspend fun setAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALERTS] = enabled }
    }

    suspend fun setWifiOnlySync(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    suspend fun setSignatureVersion(version: String) {
        context.dataStore.edit { it[KEY_SIG_VERSION] = version }
    }

    suspend fun currentSignatureVersion(): String = signatureVersion.first()

    private companion object {
        val KEY_ALERTS = booleanPreferencesKey("alerts_enabled")
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only_sync")
        val KEY_SIG_VERSION = stringPreferencesKey("signature_version")
    }
}
