package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "pdf_annotation_segments",
    primaryKeys = ["annotationId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = PdfAnnotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["annotationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("annotationId"),
        Index("pageIndex"),
    ],
)
data class PdfAnnotationSegmentEntity(
    val annotationId: String,
    val orderIndex: Int,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

fun PdfAnnotationSegmentEntity.isValidPdfAnnotationSegment(): Boolean =
    annotationId.isNotBlank() &&
        orderIndex >= 0 &&
        pageIndex >= 0 &&
        left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        right > left &&
        bottom > top &&
        right - left >= 0.5f &&
        bottom - top >= 0.5f

fun PdfAnnotationEntity.legacyGeometrySegment(): PdfAnnotationSegmentEntity? =
    if (
        annotationType == PdfAnnotationEntity.TYPE_HIGHLIGHT &&
        attachmentId.isNotBlank() &&
        pageIndex >= 0 &&
        left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        right > left &&
        bottom > top
    ) {
        PdfAnnotationSegmentEntity(
            annotationId = id,
            orderIndex = 0,
            pageIndex = pageIndex,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    } else {
        null
    }

fun PdfAnnotationEntity.resolvedGeometrySegments(
    segments: List<PdfAnnotationSegmentEntity>,
): List<PdfAnnotationSegmentEntity> {
    val validSegments = segments
        .asSequence()
        .filter { it.annotationId == id && it.isValidPdfAnnotationSegment() }
        .sortedBy { it.orderIndex }
        .toList()
    return validSegments.ifEmpty { listOfNotNull(legacyGeometrySegment()) }
}
