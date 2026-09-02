package com.myvault.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.myvault.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AiProviderCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    suspend fun credential(provider: AiResearchProvider): String = withContext(Dispatchers.IO) {
        synchronized(this@AiProviderCredentialStore) {
            readOverride(provider).orEmpty().ifBlank { configuredFallback(provider) }
        }
    }

    @Synchronized
    fun writeOverride(provider: AiResearchProvider, credential: String) {
        val clean = credential.trim()
        if (clean.isBlank()) {
            clearOverride(provider)
            return
        }
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.encodeToByteArray())
        preferences.edit()
            .putString(provider.preferenceKey(), Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clearOverride(provider: AiResearchProvider) {
        preferences.edit().remove(provider.preferenceKey()).apply()
    }

    private fun readOverride(provider: AiResearchProvider): String? {
        val encoded = preferences.getString(provider.preferenceKey(), null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IvLengthBytes)
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GcmTagLengthBits, payload.copyOfRange(0, IvLengthBytes)),
            )
            cipher.doFinal(payload.copyOfRange(IvLengthBytes, payload.size)).decodeToString().trim()
        }.getOrElse {
            clearOverride(provider)
            null
        }
    }

    private fun configuredFallback(provider: AiResearchProvider): String = when (provider) {
        AiResearchProvider.ChatGpt -> BuildConfig.OPENAI_API_KEY
        AiResearchProvider.Gemini -> BuildConfig.GEMINI_API_KEY
        AiResearchProvider.Kimi -> BuildConfig.KIMI_API_KEY
    }.trim()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun AiResearchProvider.preferenceKey(): String = "credential_$id"

    private companion object {
        const val PreferencesName = "ai_provider_credentials"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "myvault_ai_provider_credentials_v1"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128
        const val IvLengthBytes = 12
    }
}
