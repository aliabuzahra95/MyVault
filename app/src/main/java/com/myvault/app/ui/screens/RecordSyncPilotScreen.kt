package com.myvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.myvault.app.ui.theme.VaultThemeTokens
import com.myvault.app.ui.viewmodel.RecordSyncPilotUiState
import kotlinx.coroutines.delay

@Composable
fun RecordSyncPilotScreen(
    state: RecordSyncPilotUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSync: () -> Unit,
    onAutoSync: () -> Unit,
    onOpenNote: (String) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                onAutoSync()
                delay(30_000)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Two-phone sync test", style = MaterialTheme.typography.titleLarge, color = colors.text)
        }
        Spacer(Modifier.height(20.dp))
        Text("Test notes only", style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(
            "Test notes sync automatically after edits and when MyVault opens. Your other notes and Drive backups are not part of this test.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Create test note") }
            OutlinedButton(onClick = onSync, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Check now") }
        }
        state.message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Test notes", style = MaterialTheme.typography.titleSmall, color = colors.text)
        LazyColumn(Modifier.weight(1f)) {
            items(state.notes, key = { it.id }) { note ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenNote(note.id) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (note.title == note.id) "Test note ${note.id.removePrefix("SYNC_TEST_CODEX_").take(8)}" else note.title,
                        Modifier.weight(1f), color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textSecondary)
                }
                HorizontalDivider(color = colors.border)
            }
        }
    }
}
