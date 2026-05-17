package com.myvault.app.data.repository

import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.KnowledgeTagDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.PdfAnnotationDao
import com.myvault.app.data.local.dao.SourceBacklinkDao
import com.myvault.app.data.local.entity.KNOWLEDGE_TAG_TARGET_ANNOTATION
import com.myvault.app.data.local.entity.KNOWLEDGE_TAG_TARGET_ATTACHMENT
import com.myvault.app.data.local.entity.KNOWLEDGE_TAG_TARGET_NOTE
import com.myvault.app.data.local.entity.KnowledgeTagEntity
import com.myvault.app.data.local.entity.KnowledgeTagLinkEntity
import com.myvault.app.data.local.entity.SourceBacklinkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class KnowledgeTagChip(
    val id: String,
    val name: String,
)

data class SourceReferenceCard(
    val id: String,
    val noteId: String,
    val attachmentId: String,
    val annotationId: String?,
    val title: String,
    val pageIndex: Int,
    val unavailable: Boolean,
    val annotationDeleted: Boolean,
)

data class LibraryReferencedNote(
    val id: String,
    val noteId: String,
    val noteTitle: String,
    val attachmentId: String,
    val pageIndex: Int,
)

@Singleton
class KnowledgeRepository @Inject constructor(
    private val sourceBacklinkDao: SourceBacklinkDao,
    private val knowledgeTagDao: KnowledgeTagDao,
    private val attachmentDao: AttachmentDao,
    private val noteDao: NoteDao,
    private val pdfAnnotationDao: PdfAnnotationDao,
) {
    fun observeSourceReferencesForNote(noteId: String): Flow<List<SourceReferenceCard>> =
        combine(
            sourceBacklinkDao.observeForNote(noteId),
            attachmentDao.observeLibraryFiles(),
            pdfAnnotationDao.observeAll(),
        ) { links, attachments, annotations ->
            val attachmentsById = attachments.associateBy { it.id }
            val annotationsById = annotations.associateBy { it.id }
            links.map { link ->
                val attachment = attachmentsById[link.attachmentId]
                SourceReferenceCard(
                    id = link.id,
                    noteId = link.noteId,
                    attachmentId = link.attachmentId,
                    annotationId = link.annotationId,
                    title = attachment?.fileName ?: "Source unavailable",
                    pageIndex = link.pageIndex,
                    unavailable = attachment == null,
                    annotationDeleted = link.annotationId != null && link.annotationId !in annotationsById,
                )
            }
        }

    fun observeLibraryReferences(): Flow<List<LibraryReferencedNote>> =
        combine(
            sourceBacklinkDao.observeAll(),
            noteDao.observeAll(),
            attachmentDao.observeLibraryFiles(),
        ) { links, notes, attachments ->
            val notesById = notes.associateBy { it.id }
            val attachmentIds = attachments.map { it.id }.toSet()
            links.mapNotNull { link ->
                val note = notesById[link.noteId] ?: return@mapNotNull null
                if (link.attachmentId !in attachmentIds) return@mapNotNull null
                LibraryReferencedNote(
                    id = link.id,
                    noteId = link.noteId,
                    noteTitle = note.title,
                    attachmentId = link.attachmentId,
                    pageIndex = link.pageIndex,
                )
            }
        }

    fun observeTagsFor(targetType: String, targetId: String): Flow<List<KnowledgeTagChip>> =
        combine(knowledgeTagDao.observeTags(), knowledgeTagDao.observeLinks()) { tags, links ->
            val tagsById = tags.associateBy { it.id }
            links
                .filter { it.targetType == targetType && it.targetId == targetId }
                .mapNotNull { tagsById[it.tagId] }
                .sortedBy { it.name.lowercase() }
                .map { KnowledgeTagChip(it.id, it.name) }
        }

    fun observeTagsByTargetType(targetType: String): Flow<Map<String, List<KnowledgeTagChip>>> =
        combine(knowledgeTagDao.observeTags(), knowledgeTagDao.observeLinks()) { tags, links ->
            val tagsById = tags.associateBy { it.id }
            links
                .filter { it.targetType == targetType }
                .groupBy { it.targetId }
                .mapValues { (_, targetLinks) ->
                    targetLinks
                        .mapNotNull { tagsById[it.tagId] }
                        .sortedBy { it.name.lowercase() }
                        .map { KnowledgeTagChip(it.id, it.name) }
                }
        }

    suspend fun createSourceLinkFromAnnotation(noteId: String, annotationId: String) {
        val annotation = pdfAnnotationDao.getAll().firstOrNull { it.id == annotationId } ?: return
        sourceBacklinkDao.upsert(
            SourceBacklinkEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                attachmentId = annotation.attachmentId,
                annotationId = annotation.id,
                pageIndex = annotation.pageIndex,
                left = annotation.left,
                top = annotation.top,
                right = annotation.right,
                bottom = annotation.bottom,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addTag(targetType: String, targetId: String, rawName: String) {
        val name = rawName.trim()
        if (name.isBlank()) return
        val existing = knowledgeTagDao.getAllTags().firstOrNull { it.name.equals(name, ignoreCase = true) }
        val tag = existing ?: KnowledgeTagEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
        )
        knowledgeTagDao.upsertTags(listOf(tag))
        knowledgeTagDao.upsertLinks(
            listOf(
                KnowledgeTagLinkEntity(
                    tagId = tag.id,
                    targetType = targetType,
                    targetId = targetId,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )
    }

    suspend fun removeTag(targetType: String, targetId: String, tagId: String) {
        knowledgeTagDao.deleteLink(tagId, targetType, targetId)
    }

    suspend fun removeSourceReference(referenceId: String) {
        sourceBacklinkDao.deleteById(referenceId)
    }

    companion object {
        const val TargetNote = KNOWLEDGE_TAG_TARGET_NOTE
        const val TargetAttachment = KNOWLEDGE_TAG_TARGET_ATTACHMENT
        const val TargetAnnotation = KNOWLEDGE_TAG_TARGET_ANNOTATION
    }
}
