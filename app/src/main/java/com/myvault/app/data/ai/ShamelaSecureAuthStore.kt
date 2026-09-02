package com.myvault.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import net.openid.appauth.AuthState

@Singleton
class ShamelaSecureAuthStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): AuthState? {
        val encoded = preferences.getString(AuthStateKey, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IvLengthBytes)
            val cipher = Cipher.getInstance(CipherTransformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GcmTagLengthBits, payload.copyOfRange(0, IvLengthBytes)),
            )
            val json = cipher.doFinal(payload.copyOfRange(IvLengthBytes, payload.size)).decodeToString()
            AuthState.jsonDeserialize(json)
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun write(state: AuthState) {
        val cipher = Cipher.getInstance(CipherTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(state.jsonSerializeString().encodeToByteArray())
        val payload = cipher.iv + ciphertext
        preferences.edit()
            .putString(AuthStateKey, Base64.encodeToString(payload, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(AuthStateKey).apply()
    }

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

    private companion object {
        const val PreferencesName = "shamela_secure_auth"
        const val AuthStateKey = "encrypted_auth_state"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "myvault_shamela_oauth_v1"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128
        const val IvLengthBytes = 12
    }
}
