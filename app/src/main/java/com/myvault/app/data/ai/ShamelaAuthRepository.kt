package com.myvault.app.data.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

@Singleton
class ShamelaAuthRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStore: ShamelaSecureAuthStore,
) {
    private val service = AuthorizationService(context)
    private var authState = secureStore.read() ?: AuthState(ServiceConfiguration)
    private val _connection = MutableStateFlow(authState.toConnectionState())
    val connection: StateFlow<ShamelaConnectionState> = _connection.asStateFlow()

    fun createAuthorizationIntent(): Intent {
        _connection.value = ShamelaConnectionState.Connecting
        val request = AuthorizationRequest.Builder(
            ServiceConfiguration,
            ClientId,
            ResponseTypeValues.CODE,
            RedirectUri,
        )
            .setScope(Scope)
            .setAdditionalParameters(mapOf("resource" to ResourceEndpoint))
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun completeAuthorization(data: Intent?): Result<Unit> {
        if (data == null) {
            _connection.value = ShamelaConnectionState.Disconnected
            return Result.failure(IllegalStateException("Shamela sign-in was cancelled."))
        }
        val response = AuthorizationResponse.fromIntent(data)
        val authorizationError = AuthorizationException.fromIntent(data)
        authState.update(response, authorizationError)
        if (response == null) {
            val message = authorizationError?.errorDescription ?: "Shamela sign-in was cancelled."
            _connection.value = ShamelaConnectionState.Error(message)
            return Result.failure(IllegalStateException(message))
        }

        return suspendCancellableCoroutine { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
                authState.update(tokenResponse, tokenError)
                if (tokenResponse != null && authState.isAuthorized) {
                    secureStore.write(authState)
                    _connection.value = ShamelaConnectionState.Connected
                    continuation.resume(Result.success(Unit))
                } else {
                    val message = tokenError?.errorDescription ?: "Shamela did not return an access token."
                    _connection.value = ShamelaConnectionState.Error(message)
                    continuation.resume(Result.failure(IllegalStateException(message)))
                }
            }
        }
    }

    suspend fun freshAccessToken(): String = suspendCancellableCoroutine { continuation ->
        authState.performActionWithFreshTokens(service) { accessToken, _, error ->
            if (error != null || accessToken.isNullOrBlank()) {
                val message = error?.errorDescription ?: "Shamela sign-in has expired."
                _connection.value = ShamelaConnectionState.Error(message)
                continuation.resumeWith(Result.failure(IllegalStateException(message)))
            } else {
                secureStore.write(authState)
                _connection.value = ShamelaConnectionState.Connected
                continuation.resume(accessToken)
            }
        }
    }

    suspend fun disconnect() {
        val token = authState.refreshToken ?: authState.accessToken
        if (!token.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val body = "token=${token.formEncode()}&client_id=${ClientId.formEncode()}"
                    (URL(RevocationEndpoint).openConnection() as HttpURLConnection).run {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                        outputStream.use { it.write(body.encodeToByteArray()) }
                        inputStream.use { it.readBytes() }
                        disconnect()
                    }
                }
            }
        }
        secureStore.clear()
        authState = AuthState(ServiceConfiguration)
        _connection.value = ShamelaConnectionState.Disconnected
    }

    fun invalidateLocalSession(message: String) {
        secureStore.clear()
        authState = AuthState(ServiceConfiguration)
        _connection.value = ShamelaConnectionState.Error(message)
    }

    private fun AuthState.toConnectionState(): ShamelaConnectionState =
        if (isAuthorized) ShamelaConnectionState.Connected else ShamelaConnectionState.Disconnected

    companion object {
        const val ResourceEndpoint = "https://shamela.link/mcp"
        const val Issuer = "https://shamela.link/api/auth"
        const val AuthorizationEndpoint = "https://shamela.link/api/auth/oauth2/authorize"
        const val TokenEndpoint = "https://shamela.link/api/auth/oauth2/token"
        const val RevocationEndpoint = "https://shamela.link/api/auth/oauth2/revoke"
        const val ClientId = "MxDRIXnInfGlrgkdvjzsLQfUuLoGrwRa"
        const val RedirectEndpoint = "com.myvault.app:/oauth2redirect/shamela"
        const val Scope = "openid profile email offline_access"

        val RedirectUri: Uri = Uri.parse(RedirectEndpoint)
        val ServiceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse(AuthorizationEndpoint),
            Uri.parse(TokenEndpoint),
        )
    }
}

sealed interface ShamelaConnectionState {
    data object Disconnected : ShamelaConnectionState
    data object Connecting : ShamelaConnectionState
    data object Connected : ShamelaConnectionState
    data class Error(val message: String) : ShamelaConnectionState
}

private fun String.formEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
