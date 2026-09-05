package com.myvault.app.widget.quran

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.myvault.app.R
import com.myvault.app.widget.widgetAppearanceContext
import com.myvault.app.data.quran.audio.QuranPlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QuranWidgetPlaybackEntryPoint {
    fun controller(): QuranPlaybackController
}

internal fun quranWidgetPlayback(context: Context): QuranPlaybackController =
    EntryPointAccessors.fromApplication(context.applicationContext, QuranWidgetPlaybackEntryPoint::class.java).controller()

internal fun RemoteViews.setQuranAudioIcon(context: Context, id: Int, view: Int, icon: Int) {
    val themed = context.widgetAppearanceContext(id)
    val drawable = themed.getDrawable(icon)?.mutate() ?: return
    drawable.setTint(themed.getColor(R.color.quran_widget_icon))
    setImageViewBitmap(view, drawable.toBitmap())
}

internal object QuranWidgetPlayback {
    const val ROW = "com.myvault.app.quran.widget.ROW"
    const val HEADER_PLAY = "com.myvault.app.quran.widget.PLAY_SURAH"
    const val HEADER_STOP = "com.myvault.app.quran.widget.STOP"
    const val HEADER_CONTINUE = "com.myvault.app.quran.widget.CONTINUE"
    const val COMMAND = "quran_widget_row_command"
    const val OPEN = "open"
    const val PLAY = "play"

    fun rowIntent(widget: Int, surah: Int, ayah: Int, command: String): Intent = Intent().apply {
        putExtra(COMMAND, command)
        putExtra(QuranWidgetContract.EXTRA_WIDGET_ID, widget)
        putExtra(QuranWidgetContract.EXTRA_SURAH_NUMBER, surah)
        putExtra(QuranWidgetContract.EXTRA_AYAH_NUMBER, ayah)
        data = Uri.parse("myvault://quran/$surah/$ayah?widget=$widget&action=$command")
    }
}
