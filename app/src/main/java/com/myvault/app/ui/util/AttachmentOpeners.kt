package com.myvault.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.myvault.app.data.local.entity.AttachmentEntity
import java.io.File

fun openAttachment(context: Context, attachment: AttachmentEntity) {
    val file = File(attachment.localPath)
    if (!file.exists()) {
        Toast.makeText(context, "Attachment file is missing", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Open attachment"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file type", Toast.LENGTH_SHORT).show()
    }
}
