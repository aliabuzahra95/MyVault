package com.myvault.app.widget.note

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Singleton
class NoteWidgetUpdateCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: NoteWidgetDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var previous = emptyMap<String, String>()

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            dataSource.observeItems()
                .catch { error -> Log.e(TAG, "Note widget updates stopped", error) }
                .collect { items ->
                    val current = items.associate { it.id to it.fingerprint }
                    val manager = AppWidgetManager.getInstance(context)
                    val widgetIds = manager.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
                    if (widgetIds.isNotEmpty()) {
                        val stateStore = NoteWidgetStateStore(context)
                        val changedIds = (previous.keys + current.keys).filter { previous[it] != current[it] }.toSet()
                        widgetIds
                            .filter { id -> previous.isEmpty() || stateStore.state(id).noteId in changedIds }
                            .forEach { id -> NoteWidgetProvider.updateWidget(context, manager, id) }
                    }
                    previous = current
                }
        }
    }

    private companion object {
        const val TAG = "NoteWidgetUpdates"
    }
}
