package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.verityDataStore: DataStore<Preferences> by preferencesDataStore(name = "verity_prefs")

data class AuthSessionState(
    val isLoggedIn: Boolean = false,
    val rememberMe: Boolean = false,
    val email: String = "",
    val displayName: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null
)

class VerityPreferences(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val TAKEN_DOSE_IDS = stringSetPreferencesKey("taken_dose_ids")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val REMEMBER_ME = booleanPreferencesKey("remember_me")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_NAME = stringPreferencesKey("user_name")
        val ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("encrypted_access_token")
        val ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("encrypted_refresh_token")
        val PROFILE_PHOTO_PATH = stringPreferencesKey("profile_photo_path")
        val GOOGLE_FIT_AUTO_SYNC = booleanPreferencesKey("google_fit_auto_sync")
        val UPLOADED_REPORTS_JSON = stringPreferencesKey("uploaded_reports_json")
        val AI_CONSULTATION_SESSION_MAP = stringPreferencesKey("ai_consultation_session_map")
    }

    val darkModeFlow: Flow<Boolean> = context.verityDataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: true
    }

    val takenDoseIdsFlow: Flow<Set<String>> = context.verityDataStore.data.map { prefs ->
        prefs[Keys.TAKEN_DOSE_IDS] ?: emptySet()
    }

    val authSessionFlow: Flow<AuthSessionState> = context.verityDataStore.data.map { prefs ->
        val remember = prefs[Keys.REMEMBER_ME] ?: false
        val loggedIn = (prefs[Keys.IS_LOGGED_IN] ?: false) && remember
        AuthSessionState(
            isLoggedIn = loggedIn,
            rememberMe = remember,
            email = prefs[Keys.USER_EMAIL].orEmpty(),
            displayName = prefs[Keys.USER_NAME].orEmpty(),
            accessToken = if (loggedIn) decryptToken(prefs[Keys.ENCRYPTED_ACCESS_TOKEN]) else null,
            refreshToken = if (loggedIn) decryptToken(prefs[Keys.ENCRYPTED_REFRESH_TOKEN]) else null
        )
    }

    val profilePhotoPathFlow: Flow<String?> = context.verityDataStore.data.map { prefs ->
        prefs[Keys.PROFILE_PHOTO_PATH]?.takeIf { it.isNotBlank() }
    }

    val googleFitAutoSyncFlow: Flow<Boolean> = context.verityDataStore.data.map { prefs ->
        prefs[Keys.GOOGLE_FIT_AUTO_SYNC] ?: true
    }

    val uploadedReportsJsonFlow: Flow<String?> = context.verityDataStore.data.map { prefs ->
        prefs[Keys.UPLOADED_REPORTS_JSON]?.takeIf { it.isNotBlank() }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun setTakenDoseIds(ids: Set<String>) {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.TAKEN_DOSE_IDS] = ids
        }
    }

    suspend fun setProfilePhotoPath(path: String?) {
        context.verityDataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(Keys.PROFILE_PHOTO_PATH)
            } else {
                prefs[Keys.PROFILE_PHOTO_PATH] = path
            }
        }
    }

    suspend fun setGoogleFitAutoSync(enabled: Boolean) {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_FIT_AUTO_SYNC] = enabled
        }
    }

    suspend fun setUploadedReportsJson(json: String) {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.UPLOADED_REPORTS_JSON] = json
        }
    }

    suspend fun aiConsultationSession(appointmentId: String): String? {
        val json = context.verityDataStore.data.first()[Keys.AI_CONSULTATION_SESSION_MAP]
            ?: return null
        return runCatching { org.json.JSONObject(json).optString(appointmentId).takeIf(String::isNotBlank) }
            .getOrNull()
    }

    suspend fun saveAiConsultationSession(appointmentId: String, sessionId: String?) {
        context.verityDataStore.edit { prefs ->
            val sessions = runCatching {
                org.json.JSONObject(prefs[Keys.AI_CONSULTATION_SESSION_MAP].orEmpty())
            }.getOrElse { org.json.JSONObject() }
            if (sessionId.isNullOrBlank()) sessions.remove(appointmentId) else sessions.put(appointmentId, sessionId)
            if (sessions.length() == 0) prefs.remove(Keys.AI_CONSULTATION_SESSION_MAP)
            else prefs[Keys.AI_CONSULTATION_SESSION_MAP] = sessions.toString()
        }
    }

    suspend fun saveAuthenticatedSession(name: String, email: String, accessToken: String, refreshToken: String?, rememberMe: Boolean) {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
            prefs[Keys.USER_EMAIL] = email
            prefs[Keys.REMEMBER_ME] = rememberMe
            prefs[Keys.IS_LOGGED_IN] = true
            if (rememberMe) {
                prefs[Keys.ENCRYPTED_ACCESS_TOKEN] = encryptToken(accessToken)
                if (!refreshToken.isNullOrBlank()) prefs[Keys.ENCRYPTED_REFRESH_TOKEN] = encryptToken(refreshToken)
                else prefs.remove(Keys.ENCRYPTED_REFRESH_TOKEN)
            } else {
                prefs.remove(Keys.ENCRYPTED_ACCESS_TOKEN)
                prefs.remove(Keys.ENCRYPTED_REFRESH_TOKEN)
            }
        }
    }

    suspend fun updateRememberedTokens(accessToken: String, refreshToken: String?) {
        context.verityDataStore.edit { prefs ->
            if (prefs[Keys.REMEMBER_ME] == true && prefs[Keys.IS_LOGGED_IN] == true) {
                prefs[Keys.ENCRYPTED_ACCESS_TOKEN] = encryptToken(accessToken)
                if (!refreshToken.isNullOrBlank()) prefs[Keys.ENCRYPTED_REFRESH_TOKEN] = encryptToken(refreshToken)
            }
        }
    }

    suspend fun logout() {
        context.verityDataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = false
            prefs[Keys.REMEMBER_ME] = false
            prefs.remove(Keys.ENCRYPTED_ACCESS_TOKEN)
            prefs.remove(Keys.ENCRYPTED_REFRESH_TOKEN)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.USER_NAME)
            prefs.remove(Keys.AI_CONSULTATION_SESSION_MAP)
        }
    }

    private fun encryptToken(token: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptToken(encryptedValue: String?): String? = runCatching {
        if (encryptedValue.isNullOrBlank()) return null
        val payload = Base64.decode(encryptedValue, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, 12)
        val cipherText = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object { const val KEY_ALIAS = "verity_patient_session_key" }
}
