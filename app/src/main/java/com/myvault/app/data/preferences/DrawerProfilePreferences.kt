package com.myvault.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myvault.app.ui.components.normalizedDrawerName
import com.myvault.app.ui.components.validDrawerName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.drawerProfileDataStore by preferencesDataStore(name = "drawer_profile_preferences")

/** Device-local presentation only; not part of VaultUserPreferences or the backup writer. */
@Singleton
class DrawerProfilePreferences @Inject constructor(@param:ApplicationContext private val context: Context) {
    val names = context.drawerProfileDataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) -> (value as? String)?.let { key.name to it } }.toMap()
    }

    suspend fun setName(accountKey: String, value: String) {
        require(accountKey == "local" || accountKey.matches(Regex("google_[0-9a-f]{64}")))
        require(validDrawerName(value))
        val name = normalizedDrawerName(value)
        context.drawerProfileDataStore.edit { preferences ->
            val key = stringPreferencesKey(accountKey)
            if (name.isEmpty()) preferences.remove(key) else preferences[key] = name
        }
    }
}
