package com.myvault.app.widget.note

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NoteWidgetEntryPoint {
    fun dataSource(): NoteWidgetDataSource
}

internal fun Context.noteWidgetDataSource(): NoteWidgetDataSource =
    EntryPointAccessors.fromApplication(applicationContext, NoteWidgetEntryPoint::class.java).dataSource()
