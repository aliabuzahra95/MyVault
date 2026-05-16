package com.myvault.app.data.supabase

import com.myvault.app.BuildConfig

object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()
    const val backupBucket: String = "vault-backups"

    val isConfigured: Boolean
        get() = url.startsWith("https://") && anonKey.isNotBlank()
}
