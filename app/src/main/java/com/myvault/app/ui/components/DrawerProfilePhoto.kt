package com.myvault.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun DrawerProfilePhoto(identity: DrawerIdentity) {
    val colors = VaultThemeTokens.colors
    val context = LocalContext.current.applicationContext
    // Recreate state for every identity/URL, so a previous account's bitmap is never retained.
    key(identity.accountKey, identity.photoUrl) {
        val bitmap by produceState<Bitmap?>(null) {
            value = withContext(Dispatchers.IO) { loadDrawerPhoto(context, identity) }
        }
        Box(Modifier.size(42.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Google profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(drawerInitials(identity.displayName), color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.W700)
        }
    }
}

private val photos = object : LruCache<String, Bitmap>(1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
private const val MaxPhotoBytes = 512 * 1024

internal fun decodeDrawerPhoto(bytes: ByteArray): Bitmap? {
    if (bytes.size > MaxPhotoBytes) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

internal fun loadDrawerPhoto(context: Context, identity: DrawerIdentity): Bitmap? {
    var location = drawerPhotoUrl(identity.photoUrl) ?: return null
    if (identity.accountKey == "local") return null
    val key = drawerPhotoCacheKey(identity)
    photos.get(key)?.let { return it }
    return runCatching {
        val directory = File(context.cacheDir, "drawer-profile-photos").apply { mkdirs() }
        val file = File(directory, key)
        if (file.isFile && file.length() <= MaxPhotoBytes) {
            decodeDrawerPhoto(file.readBytes())?.let { photos.put(key, it); return it }
        }
        repeat(3) {
            val connection = (URL(location).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3_000
                readTimeout = 3_000
                instanceFollowRedirects = false
            }
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    location = drawerPhotoUrl(URL(URL(location), connection.getHeaderField("Location")).toString()) ?: return null
                } else {
                    if (status != 200 || connection.contentLengthLong > MaxPhotoBytes) return null
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= MaxPhotoBytes) {
                            val count = input.read(buffer, 0, minOf(buffer.size, MaxPhotoBytes + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bitmap = decodeDrawerPhoto(bytes) ?: return null
                    photos.put(key, bitmap)
                    // A cache write failure must not suppress a successfully loaded photo.
                    runCatching {
                        val atomic = AtomicFile(file)
                        val output = atomic.startWrite()
                        try { output.write(bytes); atomic.finishWrite(output) }
                        catch (error: Exception) { atomic.failWrite(output); throw error }
                        directory.listFiles()?.filter { it.isFile && it.name != key }
                            ?.sortedByDescending(File::lastModified)?.drop(15)?.forEach { it.delete() }
                    }
                    return bitmap
                }
            } finally { connection.disconnect() }
        }
        null
    }.getOrNull()
}
