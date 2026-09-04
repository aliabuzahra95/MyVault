package com.myvault.app.widget.quran

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.myvault.app.R

class QuranWidgetSearchActivity : Activity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.widget_quran_search_dialog)
        val query = findViewById<EditText>(R.id.quran_widget_search_input)
        query.setText(QuranWidgetStateStore(this).read(appWidgetId).searchQuery)
        query.setSelection(query.text.length)
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                applyQuery(query.text.toString())
                true
            } else {
                false
            }
        }
        findViewById<ImageButton>(R.id.quran_widget_search_close).setOnClickListener { finish() }
        findViewById<TextView>(R.id.quran_widget_search_clear).setOnClickListener { applyQuery("") }
        findViewById<TextView>(R.id.quran_widget_search_apply).setOnClickListener {
            applyQuery(query.text.toString())
        }
        query.requestFocus()
        query.post {
            getSystemService(InputMethodManager::class.java)?.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun applyQuery(value: String) {
        QuranWidgetStateStore(this).apply {
            setSearchQuery(appWidgetId, value)
            setMode(appWidgetId, QuranWidgetMode.Picker)
        }
        QuranWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
        finish()
    }
}
