package com.myvault.app.ui.components

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

const val DRAWER_NAME_MAX_LENGTH = 60

data class DrawerGoogleProfile(val email: String, val displayName: String?, val photoUrl: String?)

data class DrawerIdentity(
    val accountKey: String = "local",
    val displayName: String = "MyVault",
    val photoUrl: String? = null,
)

fun drawerAccountKey(email: String): String = email.trim().lowercase(Locale.ROOT).let {
    if (it.isEmpty()) "local" else "google_${drawerIdentityHash(it)}"
}

internal fun drawerIdentityHash(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun drawerPhotoCacheKey(identity: DrawerIdentity): String = drawerIdentityHash("${identity.accountKey}|${identity.photoUrl}")

fun normalizedDrawerName(value: String): String = value.replace(Regex("[\\r\\n\\t]"), " ")
    .filterNot(Char::isISOControl).trim()

fun validDrawerName(value: String): Boolean = normalizedDrawerName(value).let {
    it.codePointCount(0, it.length) <= DRAWER_NAME_MAX_LENGTH
}

internal fun drawerPhotoUrl(value: String?): String? = value?.takeIf {
    runCatching { URI(it).let { uri -> uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null } }.getOrDefault(false)
}

fun resolveDrawerIdentity(email: String, names: Map<String, String>, google: DrawerGoogleProfile?): DrawerIdentity {
    val key = drawerAccountKey(email)
    val matched = google?.takeIf { key != "local" && drawerAccountKey(it.email) == key }
    val name = names[key]?.takeIf(::validDrawerName)?.let(::normalizedDrawerName)?.takeIf(String::isNotBlank)
        ?: matched?.displayName?.trim()?.takeIf(String::isNotBlank)
        ?: email.substringBefore('@').trim().takeIf(String::isNotBlank)
        ?: "MyVault"
    return DrawerIdentity(key, name, drawerPhotoUrl(matched?.photoUrl))
}

internal fun drawerInitials(name: String): String = name.split(Regex("[\\s._-]+"))
    .filter(String::isNotBlank).take(2)
    .joinToString("") { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.ROOT) }.ifBlank { "MV" }
