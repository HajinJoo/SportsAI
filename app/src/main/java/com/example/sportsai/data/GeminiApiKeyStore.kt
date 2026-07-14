package com.example.sportsai.data

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

/**
 * Stores a user-provided Gemini API key encrypted with a non-exportable Android Keystore key.
 * The encrypted preferences file is excluded from cloud backup and device transfer.
 */
class GeminiApiKeyStore(
    context: Context,
    private val preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(apiKey: String) {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "API key cannot be empty" }

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        val encrypted = cipher.doFinal(normalized.toByteArray(StandardCharsets.UTF_8))
        val saved = preferences.edit()
            .putString(ENCRYPTED_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(INITIALIZATION_VECTOR, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        check(saved) { "The API key could not be saved on this device" }
    }

    @Synchronized
    fun read(): String? {
        val encryptedValue = preferences.getString(ENCRYPTED_KEY, null) ?: return null
        val initializationVector = preferences.getString(INITIALIZATION_VECTOR, null) ?: run {
            clear()
            return null
        }

        return try {
            val keyStore = loadKeyStore()
            val secretKey = keyStore.getKey(keyAlias, null) as? SecretKey ?: run {
                clear()
                return null
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(AUTHENTICATION_TAG_BITS, Base64.decode(initializationVector, Base64.NO_WRAP))
                )
            }
            String(
                cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            ).trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            // A restored/corrupted ciphertext cannot be decrypted without its device Keystore key.
            clear()
            null
        }
    }

    fun hasKey(): Boolean = read() != null

    fun maskedKey(): String? = read()?.let { key ->
        "•••••••• ${key.takeLast(4)}"
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
        runCatching {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }

    companion object {
        const val PREFERENCES_NAME = "gemini_secure_settings"
        private const val KEY_ALIAS = "sportsai_gemini_api_key_v1"
        private const val ENCRYPTED_KEY = "encrypted_api_key"
        private const val INITIALIZATION_VECTOR = "initialization_vector"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AUTHENTICATION_TAG_BITS = 128
    }
}
