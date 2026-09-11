package com.myvault.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.quran.ReflectionListIndex
import com.myvault.app.ui.quran.ReflectionSort
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun ReflectionsScreen(
    reflections: List<QuranReflectionItem>,
    onOpenNavigation: () -> Unit,
    onOpenQuran: () -> Unit,
    onReflectionClick: (QuranReflectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    var surah by rememberSaveable { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable { mutableStateOf(ReflectionSort.Newest) }
    var surahMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val index = remember(reflections) { ReflectionListIndex(reflections, quranCatalog) }
    val results = remember(index, query, surah, sort) { index.select(query, surah, sort) }
    Surface(modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenNavigation, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Menu, "Open navigation", tint = colors.textSecondary, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Reflections", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Your Qur'an reflections", color = colors.textMuted, fontSize = 11.sp, letterSpacing = 0.sp)
                }
            }
            if (reflections.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = colors.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, colors.border),
            ) {
                Row(Modifier.heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.text, fontSize = 13.sp, letterSpacing = 0.sp),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)
                            .semantics { contentDescription = "Search reflections or ayah" },
                        decorationBox = { field ->
                            if (query.isEmpty()) Text("Search reflections or ayah...", color = colors.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            field()
                        },
                    )
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, "Clear search", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(1f)) {
                    ReflectionFilter("Surah", quranCatalog.firstOrNull { it.num == surah }?.name ?: "All", { surahMenu = true })
                    DropdownMenu(expanded = surahMenu, onDismissRequest = { surahMenu = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                        DropdownMenuItem(text = { Text("All Surahs") }, onClick = { surah = null; surahMenu = false })
                        quranCatalog.forEach { item ->
                            DropdownMenuItem(text = { Text("${item.num}. ${item.name}") }, onClick = { surah = item.num; surahMenu = false })
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    ReflectionFilter("Sort", sort.label, { sortMenu = true }, alignEnd = true)
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        ReflectionSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortMenu = false })
                        }
                    }
                }
            }
            }
            LazyColumn(Modifier.weight(1f).testTag("reflections-list"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    reflections.isEmpty() -> item("empty-collection") {
                        ReflectionEmpty("No reflections yet", "Reflections you add while reading the Qur'an will appear here.", "Open Qur'an", onOpenQuran)
                    }
                    results.isEmpty() -> item("empty-results") {
                        ReflectionEmpty("No reflections found", "Try another phrase, ayah reference or Surah.", "Clear search / filter", { query = ""; surah = null })
                    }
                    else -> items(results, key = { it.noteId }, contentType = { "reflection" }) { item ->
                        Column(Modifier.fillMaxWidth().testTag("reflection-${item.noteId}").clickable { onReflectionClick(item) }) {
                            Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.surahName, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(" · ${item.verseKey}", color = colors.textMuted, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
                                }
                                Text(item.reflectionBody.ifBlank { "Open reflection" }, color = colors.text,
                                    modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal,
                                        letterSpacing = 0.sp, textDirection = TextDirection.ContentOrLtr, textAlign = TextAlign.Start)))
                            }
                            HorizontalDivider(color = colors.border.copy(alpha = 0.7f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflectionFilter(label: String, value: String, onClick: () -> Unit, alignEnd: Boolean = false) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (alignEnd) Spacer(Modifier.weight(1f))
        Text(label, fontSize = 11.sp, color = colors.textMuted)
        Text(value, fontSize = 12.sp, color = colors.text, modifier = Modifier.weight(2f, fill = !alignEnd), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.textMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ReflectionEmpty(title: String, body: String, action: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = colors.textMuted, modifier = Modifier.size(24.dp))
        Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = colors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            if (action == "Open Qur'an") {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(action, fontSize = 13.sp)
        }
    }
}
