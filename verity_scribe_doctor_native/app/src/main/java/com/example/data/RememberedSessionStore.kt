package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only the rotating Supabase refresh token, encrypted with a device Keystore key. */
class RememberedSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun saveRefreshToken(refreshToken: String) {
        require(refreshToken.isNotBlank()) { "A refresh token is required to remember this session." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(refreshToken.toByteArray(StandardCharsets.UTF_8))
        val encrypted = cipher.iv + ciphertext
        check(preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not save the encrypted sign-in session on this device."
        }
    }

    @Synchronized
    fun readRefreshToken(): String? {
        val encoded = preferences.getString(TOKEN_KEY, null) ?: return null
        return runCatching {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            require(encrypted.size > IV_LENGTH) { "Stored sign-in session is invalid." }
            val iv = encrypted.copyOfRange(0, IV_LENGTH)
            val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8).takeIf(String::isNotBlank)
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(TOKEN_KEY).commit()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "verity_doctor_session"
        const val TOKEN_KEY = "encrypted_refresh_token"
        const val KEY_ALIAS = "verity_doctor_refresh_token_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
