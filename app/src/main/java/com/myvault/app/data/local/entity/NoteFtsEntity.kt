package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    contentEntity = NoteEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4],
)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val bodyPlainText: String,
)
