package com.myvault.app.widget.note

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import com.myvault.app.ui.theme.VaultThemeTokens
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NoteWidgetConfigActivity : androidx.fragment.app.FragmentActivity() {
    @Inject lateinit var dataSource: NoteWidgetDataSource

    private val appWidgetId: Int
        get() = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = NoteWidgetStateStore(this)
        setContent {
            val dark = com.myvault.app.widget.rememberWidgetDark(appWidgetId)
            VaultTheme(mode = if (dark) VaultThemeMode.Dark else VaultThemeMode.Light, materialYouEnabled = false) {
                val items by dataSource.observeItems().collectAsStateWithLifecycle(initialValue = emptyList())
                var choosingNote by remember {
                    mutableStateOf(store.state(appWidgetId).noteId == null || !intent.getBooleanExtra(EXTRA_EDITING, false))
                }
                NoteWidgetConfigurationScreen(
                    items = items,
                    stateProvider = { store.state(appWidgetId) },
                    choosingNote = choosingNote,
                    onChooseNote = { choosingNote = true },
                    onSelectNote = { note ->
                        store.setNote(appWidgetId, note.id)
                        refreshWidget()
                        if (intent.getBooleanExtra(EXTRA_EDITING, false)) {
                            choosingNote = false
                        } else {
                            completeConfiguration()
                        }
                    },
                    onTextSizeChange = { direction ->
                        val current = store.state(appWidgetId).textSizeLevel
                        store.setTextSizeLevel(appWidgetId, adjustedNoteTextSizeLevel(current, direction))
                        refreshWidget()
                    },
                    onShowTitleChange = {
                        store.setShowTitle(appWidgetId, it)
                        refreshWidget()
                    },
                    onShowContextChange = {
                        store.setShowContext(appWidgetId, it)
                        refreshWidget()
                    },
                    onBack = {
                        if (choosingNote && store.state(appWidgetId).noteId != null) choosingNote = false else finish()
                    },
                    onDone = ::completeConfiguration,
                )
            }
        }
    }

    private fun refreshWidget() {
        lifecycleScope.launch {
            NoteWidgetProvider.updateWidget(
                this@NoteWidgetConfigActivity,
                AppWidgetManager.getInstance(this@NoteWidgetConfigActivity),
                appWidgetId,
            )
        }
    }

    private fun completeConfiguration() {
        if (NoteWidgetStateStore(this).state(appWidgetId).noteId == null) return
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    companion object {
        private const val EXTRA_EDITING = "note_widget_editing"

        fun intent(context: Context, appWidgetId: Int, editing: Boolean): Intent =
            Intent(context, NoteWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_EDITING, editing)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}

private enum class NotePickerFilter(val label: String) { All("All"), Study("Study"), Courses("Courses") }

@Composable
private fun NoteWidgetConfigurationScreen(
    items: List<NoteWidgetItem>,
    stateProvider: () -> NoteWidgetState,
    choosingNote: Boolean,
    onChooseNote: () -> Unit,
    onSelectNote: (NoteWidgetItem) -> Unit,
    onTextSizeChange: (Int) -> Unit,
    onShowTitleChange: (Boolean) -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = VaultThemeTokens.colors
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.text)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (choosingNote) "Choose a note" else "Note widget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Text(
                        text = if (choosingNote) "Study and Course notes" else "Display preferences",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        },
    ) { padding ->
        if (choosingNote) {
            NotePicker(
                modifier = Modifier.padding(padding),
                items = items,
                onSelectNote = onSelectNote,
            )
        } else {
            NoteWidgetSettings(
                modifier = Modifier.padding(padding),
                selected = items.firstOrNull { it.id == stateProvider().noteId },
                stateProvider = stateProvider,
                onChooseNote = onChooseNote,
                onTextSizeChange = onTextSizeChange,
                onShowTitleChange = onShowTitleChange,
                onShowContextChange = onShowContextChange,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun NotePicker(
    modifier: Modifier,
    items: List<NoteWidgetItem>,
    onSelectNote: (NoteWidgetItem) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NotePickerFilter.All) }
    val visibleItems = remember(items, query, filter) {
        val needle = query.trim()
        items.filter { item ->
            val matchesFilter = when (filter) {
                NotePickerFilter.All -> true
                NotePickerFilter.Study -> item.courseId == null
                NotePickerFilter.Courses -> item.courseId != null
            }
            matchesFilter && (
                needle.isBlank() ||
                    item.title.contains(needle, ignoreCase = true) ||
                    item.context.contains(needle, ignoreCase = true) ||
                    item.body.contains(needle, ignoreCase = true)
                )
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search notes or locations") },
            shape = RoundedCornerShape(8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NotePickerFilter.entries.forEach { option ->
                TextButton(
                    onClick = { filter = option },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (filter == option) colors.accent else colors.textSecondary,
                    ),
                ) {
                    Text(option.label, fontWeight = if (filter == option) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
        if (visibleItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isBlank()) "No notes available" else "No matching notes",
                    color = colors.textSecondary,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleItems, key = { it.id }) { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectNote(note) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = note.context,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            note.body.trim().takeIf(String::isNotEmpty)?.let { body ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = body.replace(Regex("\\s+"), " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = colors.border.copy(alpha = 0.55f), modifier = Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteWidgetSettings(
    modifier: Modifier,
    selected: NoteWidgetItem?,
    stateProvider: () -> NoteWidgetState,
    onChooseNote: () -> Unit,
    onTextSizeChange: (Int) -> Unit,
    onShowTitleChange: (Boolean) -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    var state by remember { mutableStateOf(stateProvider()) }
    fun refreshState() { state = stateProvider() }
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("DISPLAYING", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseNote).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(selected?.title ?: "Choose a note", color = colors.text, fontWeight = FontWeight.SemiBold)
                selected?.context?.let { Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Text("Change", color = colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
        val activity = androidx.activity.compose.LocalActivity.current
        val widgetId = activity?.intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != null && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            com.myvault.app.widget.WidgetAppearanceControl(widgetId)
        }
        SettingRow(label = "Text size") {
            IconButton(onClick = { onTextSizeChange(-1); refreshState() }) {
                Icon(Icons.Rounded.Remove, contentDescription = "Smaller text", tint = colors.text)
            }
            Text(state.textSizeLevel.toString(), color = colors.accent, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onTextSizeChange(1); refreshState() }) {
                Icon(Icons.Rounded.Add, contentDescription = "Larger text", tint = colors.text)
            }
        }
        SettingRow(label = "Show title") {
            Switch(
                checked = state.showTitle,
                onCheckedChange = { onShowTitleChange(it); refreshState() },
            )
        }
        SettingRow(label = "Show location") {
            Switch(
                checked = state.showContext,
                onCheckedChange = { onShowContextChange(it); refreshState() },
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "The selected note is visible on your home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = colors.text, fontWeight = FontWeight.Medium)
        content()
    }
}
