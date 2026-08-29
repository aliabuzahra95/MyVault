package com.myvault.app.data.preferences

import java.nio.charset.StandardCharsets
import java.util.Base64

data class GoogleDriveSyncMetadata(
    val lastSyncAt: Long = 0L,
    val lastManifestAt: Long = 0L,
)

internal fun normalizeGoogleDriveAccount(email: String): String = email.trim().lowercase()

@Suppress("UNUSED_PARAMETER")
internal fun resolveGoogleDriveSyncMetadata(
    accountEmail: String,
    scopedEntries: Set<String>,
    legacyLastSyncAt: Long,
    legacyLastManifestAt: Long,
): GoogleDriveSyncMetadata {
    val account = normalizeGoogleDriveAccount(accountEmail)
    if (account.isBlank()) return GoogleDriveSyncMetadata()
    // Legacy global timestamps cannot be attributed to an account safely.
    return scopedEntries.toGoogleDriveSyncMetadataByAccount()[account] ?: GoogleDriveSyncMetadata()
}

internal fun Set<String>.toGoogleDriveSyncMetadataByAccount(): Map<String, GoogleDriveSyncMetadata> =
    mapNotNull { entry ->
        val parts = entry.split('|')
        if (parts.size != 3) return@mapNotNull null
        val account = runCatching {
            String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
        }.getOrNull()?.let(::normalizeGoogleDriveAccount).orEmpty()
        val syncAt = parts[1].toLongOrNull()
        val manifestAt = parts[2].toLongOrNull()
        if (account.isBlank() || syncAt == null || manifestAt == null || syncAt < 0L || manifestAt < 0L) {
            null
        } else {
            account to GoogleDriveSyncMetadata(syncAt, manifestAt)
        }
    }.toMap()

internal fun Map<String, GoogleDriveSyncMetadata>.toGoogleDriveSyncMetadataEntries(): Set<String> =
    mapNotNull { (rawAccount, metadata) ->
        val account = normalizeGoogleDriveAccount(rawAccount)
        if (account.isBlank() || metadata.lastSyncAt < 0L || metadata.lastManifestAt < 0L) return@mapNotNull null
        val encodedAccount = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(account.toByteArray(StandardCharsets.UTF_8))
        "$encodedAccount|${metadata.lastSyncAt}|${metadata.lastManifestAt}"
    }.toSet()
