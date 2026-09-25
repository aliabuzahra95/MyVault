package com.myvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myvault.app.data.repository.NotebookExportConfig
import com.myvault.app.data.repository.NotebookPrintDensity
import com.myvault.app.data.repository.resolveNotebookDensity

enum class NotebookPrintAction { Save, Share, Print, Calibration }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotebookPrintSheet(onDismiss: () -> Unit, onAction: (NotebookPrintAction, NotebookExportConfig) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("notebook_print", 0) }
    var top by remember { mutableStateOf(preferences.getFloat("top", 10f).toString()) }
    var right by remember { mutableStateOf(preferences.getFloat("right", 10f).toString()) }
    var left by remember { mutableStateOf(preferences.getFloat("left", 5f).toString()) }
    var bottom by remember { mutableStateOf(preferences.getFloat("bottom", 5f).toString()) }
    var cutGuide by remember { mutableStateOf(preferences.getBoolean("cut", true)) }
    var numbers by remember { mutableStateOf(preferences.getBoolean("numbers", true)) }
    var density by remember {
        mutableStateOf(resolveNotebookDensity(
            storedValue = preferences.getString("density", null),
            hasExistingSettings = listOf("top", "right", "left", "bottom", "cut", "numbers").any(preferences::contains),
        ))
    }
    var bodySize by remember { mutableStateOf(preferences.getInt("body_size", 0).takeIf { it in 9..14 }) }
    var fontMenuOpen by remember { mutableStateOf(false) }
    val config = runCatching {
        NotebookExportConfig(
            topInsetMm = top.toFloat(), rightInsetMm = right.toFloat(),
            leftMarginMm = left.toFloat(), bottomMarginMm = bottom.toFloat(),
            drawCenterCutGuide = cutGuide, drawPageNumbers = numbers,
            density = density, bodyFontSizePt = bodySize,
        )
    }.getOrNull()

    fun act(action: NotebookPrintAction) {
        val valid = config ?: return
        preferences.edit()
            .putFloat("top", valid.topInsetMm).putFloat("right", valid.rightInsetMm)
            .putFloat("left", valid.leftMarginMm).putFloat("bottom", valid.bottomMarginMm)
            .putBoolean("cut", cutGuide).putBoolean("numbers", numbers)
            .putString("density", density.name).putInt("body_size", bodySize ?: 0).apply()
        onDismiss()
        onAction(action, valid)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("A5 Notebook Print", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text("A4 landscape · two continuous A5 pages per sheet")
            Text("Print density", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                NotebookPrintDensity.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = density == option,
                        onClick = { density = option },
                        shape = SegmentedButtonDefaults.itemShape(index, NotebookPrintDensity.entries.size),
                        modifier = Modifier.weight(1f),
                    ) { Text(option.name, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Body font size", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { fontMenuOpen = true }) {
                        Text(bodySize?.let { "$it pt" } ?: "Auto")
                    }
                    DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
                        listOf<Int?>(null, 9, 10, 11, 12, 13, 14).forEach { size ->
                            DropdownMenuItem(text = { Text(size?.let { "$it pt" } ?: "Auto") }, onClick = {
                                bodySize = size
                                fontMenuOpen = false
                            })
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(top, { top = it }, label = { Text("Top fold (mm)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(right, { right = it }, label = { Text("Right fold (mm)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(left, { left = it }, label = { Text("Left margin (mm)") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(bottom, { bottom = it }, label = { Text("Bottom margin (mm)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            if (config == null) Text("Enter margins between 0 and 30 mm.", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Centre cut guide")
                Switch(checked = cutGuide, onCheckedChange = { cutGuide = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Page identifiers")
                Switch(checked = numbers, onCheckedChange = { numbers = it })
            }
            Text("Print at 100% / Actual Size. Turn off Fit to Page and printer 2-up.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { act(NotebookPrintAction.Save) }, enabled = config != null, modifier = Modifier.weight(1f)) { Text("Save PDF") }
                OutlinedButton(onClick = { act(NotebookPrintAction.Print) }, enabled = config != null, modifier = Modifier.weight(1f)) { Text("Print") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { act(NotebookPrintAction.Share) }, enabled = config != null, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = { act(NotebookPrintAction.Calibration) }, enabled = config != null, modifier = Modifier.weight(1f)) { Text("Calibration PDF") }
            }
        }
    }
}
