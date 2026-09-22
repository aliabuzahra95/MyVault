package com.myvault.app.data.local.entity

import android.net.Uri

data class PdfClipSource(
    val attachmentId: String,
    val annotationId: String,
    val pageIndex: Int,
)

fun buildPdfClipSourceUrl(attachmentId: String, annotationId: String, pageIndex: Int): String =
    Uri.Builder()
        .scheme("myvault")
        .authority("pdf-clip")
        .appendQueryParameter("attachmentId", attachmentId)
        .appendQueryParameter("annotationId", annotationId)
        .appendQueryParameter("page", pageIndex.coerceAtLeast(0).toString())
        .build()
        .toString()

fun AttachmentEntity.pdfClipSourceOrNull(): PdfClipSource? = runCatching {
    val uri = Uri.parse(remoteUrl ?: return null)
    if (uri.scheme != "myvault" || uri.authority != "pdf-clip") return null
    PdfClipSource(
        attachmentId = uri.getQueryParameter("attachmentId")?.takeIf(String::isNotBlank) ?: return null,
        annotationId = uri.getQueryParameter("annotationId")?.takeIf(String::isNotBlank) ?: return null,
        pageIndex = uri.getQueryParameter("page")?.toIntOrNull()?.coerceAtLeast(0) ?: return null,
    )
}.getOrNull()
