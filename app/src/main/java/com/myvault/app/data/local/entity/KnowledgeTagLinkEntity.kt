package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

const val KNOWLEDGE_TAG_TARGET_NOTE = "note"
const val KNOWLEDGE_TAG_TARGET_ATTACHMENT = "attachment"
const val KNOWLEDGE_TAG_TARGET_ANNOTATION = "annotation"

@Entity(
    tableName = "knowledge_tag_links",
    primaryKeys = ["tagId", "targetType", "targetId"],
    indices = [
        Index("tagId"),
        Index("targetType", "targetId"),
    ],
)
data class KnowledgeTagLinkEntity(
    val tagId: String,
    val targetType: String,
    val targetId: String,
    val createdAt: Long,
)
