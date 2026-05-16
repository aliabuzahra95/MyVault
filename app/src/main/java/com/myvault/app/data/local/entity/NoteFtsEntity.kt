package com.myvault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val bodyPlainText: String,
)
