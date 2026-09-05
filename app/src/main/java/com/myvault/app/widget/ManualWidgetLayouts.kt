package com.myvault.app.widget

import com.myvault.app.R

internal fun manualWidgetLayout(layout: Int, dark: Boolean): Int = when (layout) {
    R.layout.widget_quran_ayah_extra_large -> if (dark) R.layout.manual_dark_widget_quran_ayah_extra_large else R.layout.manual_light_widget_quran_ayah_extra_large
    R.layout.widget_quran_medium -> if (dark) R.layout.manual_dark_widget_quran_medium else R.layout.manual_light_widget_quran_medium
    R.layout.widget_quran_setting_toggle_row -> if (dark) R.layout.manual_dark_widget_quran_setting_toggle_row else R.layout.manual_light_widget_quran_setting_toggle_row
    R.layout.widget_note_body_row -> if (dark) R.layout.manual_dark_widget_note_body_row else R.layout.manual_light_widget_note_body_row
    R.layout.widget_quran_setting_size_row -> if (dark) R.layout.manual_dark_widget_quran_setting_size_row else R.layout.manual_light_widget_quran_setting_size_row
    R.layout.widget_note_loading_row -> if (dark) R.layout.manual_dark_widget_note_loading_row else R.layout.manual_light_widget_note_loading_row
    R.layout.widget_note_viewer_medium -> if (dark) R.layout.manual_dark_widget_note_viewer_medium else R.layout.manual_light_widget_note_viewer_medium
    R.layout.widget_quick_note_wide -> if (dark) R.layout.manual_dark_widget_quick_note_wide else R.layout.manual_light_widget_quick_note_wide
    R.layout.widget_note_viewer_extra_large -> if (dark) R.layout.manual_dark_widget_note_viewer_extra_large else R.layout.manual_light_widget_note_viewer_extra_large
    R.layout.widget_quran_loading_row -> if (dark) R.layout.manual_dark_widget_quran_loading_row else R.layout.manual_light_widget_quran_loading_row
    R.layout.widget_note_viewer_compact -> if (dark) R.layout.manual_dark_widget_note_viewer_compact else R.layout.manual_light_widget_note_viewer_compact
    R.layout.widget_quran_ayah_compact -> if (dark) R.layout.manual_dark_widget_quran_ayah_compact else R.layout.manual_light_widget_quran_ayah_compact
    R.layout.widget_quran_ayah_large -> if (dark) R.layout.manual_dark_widget_quran_ayah_large else R.layout.manual_light_widget_quran_ayah_large
    R.layout.widget_quran_extra_large -> if (dark) R.layout.manual_dark_widget_quran_extra_large else R.layout.manual_light_widget_quran_extra_large
    R.layout.widget_note_viewer_large -> if (dark) R.layout.manual_dark_widget_note_viewer_large else R.layout.manual_light_widget_note_viewer_large
    R.layout.widget_quick_note_compact -> if (dark) R.layout.manual_dark_widget_quick_note_compact else R.layout.manual_light_widget_quick_note_compact
    R.layout.widget_quran_compact -> if (dark) R.layout.manual_dark_widget_quran_compact else R.layout.manual_light_widget_quran_compact
    R.layout.widget_quran_ayah_medium -> if (dark) R.layout.manual_dark_widget_quran_ayah_medium else R.layout.manual_light_widget_quran_ayah_medium
    R.layout.widget_quran_large -> if (dark) R.layout.manual_dark_widget_quran_large else R.layout.manual_light_widget_quran_large
    R.layout.widget_quran_surah_row -> if (dark) R.layout.manual_dark_widget_quran_surah_row else R.layout.manual_light_widget_quran_surah_row
    R.layout.widget_quran_setting_done_row -> if (dark) R.layout.manual_dark_widget_quran_setting_done_row else R.layout.manual_light_widget_quran_setting_done_row
    else -> layout
}
