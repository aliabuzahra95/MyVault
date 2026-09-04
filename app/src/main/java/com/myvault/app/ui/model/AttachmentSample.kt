package com.myvault.app.ui.model

data class AttachmentSample(
    val name: String,
    val note: String,
    val size: String,
    val date: String,
    val kind: String,
    val id: String = "",
    val noteId: String = "",
    val mimeType: String = "application/octet-stream",
    val localPath: String = "",
)
