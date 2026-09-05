package com.myvault.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.myvault.app.widget.note.NoteWidgetProvider
import com.myvault.app.widget.note.QuickNoteWidgetProvider
import com.myvault.app.widget.quran.QuranWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetAppearanceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("widget_appearance", Context.MODE_PRIVATE)
    fun isDark(id: Int): Boolean = preferences.getBoolean("dark_$id", false)
    fun setDark(id: Int, dark: Boolean) { preferences.edit().putBoolean("dark_$id", dark).apply() }
    fun delete(id: Int) { preferences.edit().remove("dark_$id").apply() }
}

internal fun Context.widgetAppearanceContext(id: Int): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (WidgetAppearanceStore(this).isDark(id)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(configuration)
}

// Explicit resources keep launcher inflation independent of the phone's night mode.
internal fun widgetRemoteViews(context: Context, id: Int, layout: Int): RemoteViews =
    RemoteViews(context.packageName, manualWidgetLayout(layout, WidgetAppearanceStore(context).isDark(id)))

internal fun RemoteViews.setWidgetIcon(context: Context, id: Int, viewId: Int, drawable: Int) {
    val image = context.widgetAppearanceContext(id).getDrawable(drawable) ?: return
    setImageViewBitmap(viewId, image.toBitmap())
}

internal suspend fun refreshWidgetAppearance(context: Context, id: Int) = withContext(Dispatchers.IO) {
    val manager = AppWidgetManager.getInstance(context)
    when (manager.getAppWidgetInfo(id)?.provider) {
        ComponentName(context, NoteWidgetProvider::class.java) -> NoteWidgetProvider.updateWidget(context, manager, id)
        ComponentName(context, QuickNoteWidgetProvider::class.java) -> QuickNoteWidgetProvider.updateWidget(context, manager, id)
        ComponentName(context, QuranWidgetProvider::class.java) -> QuranWidgetProvider.updateWidget(context, manager, id)
    }
}
