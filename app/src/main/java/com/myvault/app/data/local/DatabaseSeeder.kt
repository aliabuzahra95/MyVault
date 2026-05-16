package com.myvault.app.data.local

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
) {
    suspend fun seedIfNeeded() = Unit
}
