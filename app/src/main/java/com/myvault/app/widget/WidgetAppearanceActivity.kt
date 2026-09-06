package com.myvault.app.widget

import android.appwidget.AppWidgetManager
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.widget.note.QuickNoteWidgetProvider
import kotlinx.coroutines.launch

@Composable
internal fun rememberWidgetDark(id: Int): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("widget_appearance", 0) }
    var dark by remember(id) { mutableStateOf(WidgetAppearanceStore(context).isDark(id)) }
    DisposableEffect(preferences, id) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            dark = WidgetAppearanceStore(context).isDark(id)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return dark
}

@Composable
internal fun WidgetAppearanceControl(id: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = rememberWidgetDark(id)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Appearance", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false to "Light", true to "Dark").forEach { (value, label) ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = dark == value, onClick = {
                        WidgetAppearanceStore(context).setDark(id, value)
                        scope.launch { refreshWidgetAppearance(context, id) }
                    })
                    Text(label)
                }
            }
        }
    }
}

class WidgetAppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        if (provider != ComponentName(this, QuickNoteWidgetProvider::class.java)) { finish(); return }
        setResult(Activity.RESULT_CANCELED)
        setContent {
            val dark = rememberWidgetDark(id)
            VaultTheme(mode = if (dark) VaultThemeMode.Dark else VaultThemeMode.Light, materialYouEnabled = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Quick Note settings", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        WidgetAppearanceControl(id)
                        TextButton(onClick = {
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                            )
                            finish()
                        }) { Text("Done") }
                    }
                }
            }
        }
    }
}
