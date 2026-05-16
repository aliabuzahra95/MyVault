package com.myvault.app.data.supabase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.supabaseSessionDataStore by preferencesDataStore(name = "supabase_session")

data class SupabaseSession(
    val userId: String = "",
    val email: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
) {
    val isSignedIn: Boolean get() = userId.isNotBlank() && accessToken.isNotBlank()
}

@Singleton
class SupabaseSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val session: Flow<SupabaseSession> =
        context.supabaseSessionDataStore.data.map { preferences ->
            SupabaseSession(
                userId = preferences[Keys.UserId].orEmpty(),
                email = preferences[Keys.Email].orEmpty(),
                accessToken = preferences[Keys.AccessToken].orEmpty(),
                refreshToken = preferences[Keys.RefreshToken].orEmpty(),
                expiresAt = preferences[Keys.ExpiresAt] ?: 0L,
            )
        }

    suspend fun save(session: SupabaseSession) {
        context.supabaseSessionDataStore.edit { preferences ->
            preferences[Keys.UserId] = session.userId
            preferences[Keys.Email] = session.email
            preferences[Keys.AccessToken] = session.accessToken
            preferences[Keys.RefreshToken] = session.refreshToken
            preferences[Keys.ExpiresAt] = session.expiresAt
        }
    }

    suspend fun clear() {
        context.supabaseSessionDataStore.edit { it.clear() }
    }

    private object Keys {
        val UserId = stringPreferencesKey("user_id")
        val Email = stringPreferencesKey("email")
        val AccessToken = stringPreferencesKey("access_token")
        val RefreshToken = stringPreferencesKey("refresh_token")
        val ExpiresAt = longPreferencesKey("expires_at")
    }
}
