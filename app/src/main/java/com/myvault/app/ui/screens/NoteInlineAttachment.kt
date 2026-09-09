package com.myvault.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

internal fun AttachmentEntity.hasInlineImage(): Boolean = mimeType.startsWith("image/")

internal fun AttachmentEntity.displayFileName(): String = fileName.takeIf {
    it.isNotBlank() && !Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.[^.]+$").matches(it)
} ?: if (hasInlineImage()) "Image attachment" else "Attachment"

@Composable
internal fun NoteInlineAttachment(attachment: AttachmentEntity, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!attachment.hasInlineImage()) {
        AttachmentSheetRow(attachment, onClick, modifier)
        return
    }
    val result by produceState<Result<ImageBitmap>?>(null, attachment.id, attachment.localPath, attachment.sizeBytes, compact) {
        value = loadImageBitmap(attachment.localPath, maxSize = if (compact) 320 else 1200)
    }
    val bitmap = result?.getOrNull()
    if (result?.isFailure == true) {
        AttachmentSheetRow(attachment, onClick, modifier)
        return
    }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = VaultShapes.sm,
        color = VaultThemeTokens.colors.inset) {
        BoxWithConstraints {
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                val imageHeight = (maxWidth * bitmap.height.toFloat() / bitmap.width).coerceAtMost(if (compact) 140.dp else 600.dp)
                Image(bitmap, attachment.fileName.ifBlank { "Image attachment" },
                    Modifier.fillMaxWidth().height(imageHeight), contentScale = ContentScale.Fit)
            }
        }
    }
}
