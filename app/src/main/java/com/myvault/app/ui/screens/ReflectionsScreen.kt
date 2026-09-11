package com.myvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.data.quran.quranCatalog
import com.myvault.app.ui.quran.ReflectionListIndex
import com.myvault.app.ui.quran.ReflectionSort
import com.myvault.app.ui.quran.groupReflections
import com.myvault.app.ui.quran.reflectionEmptyTitle
import com.myvault.app.ui.quran.reflectionSummary
import com.myvault.app.ui.quran.searchReflectionSurahs
import com.myvault.app.ui.theme.VaultThemeTokens
import java.util.Locale

@Composable
fun ReflectionsScreen(
    reflections: List<QuranReflectionItem>,
    onOpenNavigation: () -> Unit,
    onOpenQuran: () -> Unit,
    onReflectionClick: (QuranReflectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var surah by rememberSaveable { mutableStateOf<Int?>(null) }
    var sort by rememberSaveable { mutableStateOf(ReflectionSort.Newest) }
    var surahSheet by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val index = remember(reflections) { ReflectionListIndex(reflections, quranCatalog) }
    val results = remember(index, query, surah, sort) { index.select(query, surah, sort) }
    val groups = remember(results) { groupReflections(results) }
    val catalog = remember { quranCatalog.associateBy { it.num } }
    val selectedSurah = catalog[surah]

    Surface(modifier.fillMaxSize(), color = colors.bg) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenNavigation, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Menu, "Open navigation", tint = colors.text, modifier = Modifier.size(23.dp))
                }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("Reflections", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text("Your Qur’an reflections", color = colors.textSecondary, fontSize = 11.5.sp, letterSpacing = 0.sp)
                }
            }
            if (reflections.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ReflectionSearch(query, { query = it; listState.requestScrollToItem(0) }, "Search reflections or ayah…", "Search reflections or ayah", "Clear search")
                    Text(reflectionSummary(results, selectedSurah), modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 2.dp)
                        .testTag("reflections-summary"), color = colors.textSecondary, fontSize = 11.sp, letterSpacing = 0.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ReflectionFilter("Surah", selectedSurah?.name ?: "All Surahs", {
                                focus.clearFocus(); surahSheet = true
                            })
                        }
                        Box {
                            ReflectionFilter("Sort", sort.label, { focus.clearFocus(); sortMenu = true })
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false },
                                shape = RoundedCornerShape(12.dp), containerColor = colors.surface, tonalElevation = 0.dp) {
                                listOf(ReflectionSort.QuranOrder, ReflectionSort.Newest, ReflectionSort.Oldest).forEach { option ->
                                    DropdownMenuItem(text = { Text(option.label, fontSize = 12.sp, color = colors.text) },
                                        trailingIcon = { if (sort == option) Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = colors.textSecondary) },
                                        modifier = Modifier.semantics { selected = sort == option },
                                        onClick = { sort = option; sortMenu = false; listState.requestScrollToItem(0) })
                                }
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).testTag("reflections-list"), state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp)) {
                when {
                    reflections.isEmpty() -> item("empty-collection") {
                        ReflectionEmpty(reflectionEmptyTitle(false, query, selectedSurah),
                            "Reflections you add while reading the Qur’an will appear here.", "Open Qur’an", onOpenQuran)
                    }
                    results.isEmpty() -> item("empty-results") {
                        ReflectionEmpty(reflectionEmptyTitle(true, query, selectedSurah),
                            "Try another phrase, ayah reference or Surah.", "Clear search / filter", { query = ""; surah = null; listState.requestScrollToItem(0) })
                    }
                    else -> groups.forEach { (number, entries) ->
                        item(key = "surah-$number", contentType = "surah-heading") {
                            ReflectionSurahHeading(catalog[number], entries.first().surahName)
                        }
                        items(entries, key = { "reflection-" + it.noteId }, contentType = { "reflection" }) { item ->
                            ReflectionJournalRow(item) { focus.clearFocus(); onReflectionClick(item) }
                        }
                        item(key = "surah-gap-$number", contentType = "group-space") { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
        if (surahSheet) ReflectionSurahSheet(surah, onSelect = {
            surah = it; surahSheet = false; listState.requestScrollToItem(0)
        }, onDismiss = { surahSheet = false })
    }
}

@Composable
private fun ReflectionSearch(value: String, onValueChange: (String) -> Unit, placeholder: String, label: String, clearLabel: String) {
    val colors = VaultThemeTokens.colors
    // A quiet 39dp visual surface, with an actual 48dp editable/touch region.
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().padding(vertical = 4.5.dp).background(colors.elevated, RoundedCornerShape(10.dp)))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = colors.textSecondary)
            BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp, textDirection = TextDirection.ContentOrLtr),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = label },
                decorationBox = { field ->
                    Box(Modifier.padding(horizontal = 9.dp, vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, color = colors.textSecondary, fontSize = 13.sp, letterSpacing = 0.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        field()
                    }
                })
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, clearLabel, Modifier.size(17.dp), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ReflectionFilter(label: String, value: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 3.dp).semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, fontSize = 12.sp, color = colors.text, modifier = Modifier.weight(1f, fill = false),
            letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ReflectionSurahHeading(surah: SurahInfo?, fallback: String) {
    val colors = VaultThemeTokens.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 8.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text((surah?.name ?: fallback).uppercase(Locale.ROOT), Modifier.weight(1f), color = colors.textSecondary,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.25.sp)
        if (surah != null) Text(surah.arabic, color = colors.textSecondary,
            style = TextStyle(fontSize = 14.sp, textDirection = TextDirection.Rtl, textAlign = TextAlign.End))
    }
}

@Composable
private fun ReflectionJournalRow(item: QuranReflectionItem, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).testTag("reflection-" + item.noteId)
        .semantics { contentDescription = "Open reflection " + item.surahName + " " + item.verseKey },
        color = colors.surface, shape = RoundedCornerShape(11.dp), tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors.elevated, shape = RoundedCornerShape(5.dp)) {
                    Text(item.verseKey, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), color = colors.textSecondary,
                        style = TextStyle(fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                            platformStyle = PlatformTextStyle(includeFontPadding = false), textDirection = TextDirection.Ltr))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
            }
            Text(item.reflectionBody.ifBlank { "Open reflection" }, color = colors.text,
                modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.5.sp, lineHeight = 23.2.sp, fontWeight = FontWeight.Normal,
                    letterSpacing = 0.sp, textDirection = TextDirection.ContentOrLtr, textAlign = TextAlign.Start,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflectionSurahSheet(selectedSurah: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val colors = VaultThemeTokens.colors
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) { searchReflectionSurahs(query, quranCatalog) }
    // Include the handle and native bottom inset in the approved overall 70% cap.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val maxHeight = (windowHeight * 0.7f - 24.dp -
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).coerceAtLeast(200.dp)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface, contentColor = colors.text, scrimColor = colors.scrim, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp)
            .background(colors.borderStrong, RoundedCornerShape(2.dp))) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filter by Surah", Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, "Close Surah filter", Modifier.size(20.dp), tint = colors.textSecondary)
                }
            }
            ReflectionSearch(query, { query = it }, "Search Surah", "Search Surah", "Clear Surah search")
            LazyColumn(Modifier.weight(1f, fill = false).testTag("reflection-surah-options"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp)) {
                item("all") { ReflectionSurahOption("All Surahs", null, selectedSurah == null) { onSelect(null) } }
                items(matches, key = { it.num }, contentType = { "surah" }) { item ->
                    ReflectionSurahOption(item.name, item.arabic, selectedSurah == item.num) { onSelect(item.num) }
                }
                if (matches.isEmpty()) item("empty") {
                    Text("No Surahs found", Modifier.padding(16.dp), fontSize = 13.sp, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ReflectionSurahOption(name: String, arabic: String?, isSelected: Boolean, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Surface(onClick = onClick, color = if (isSelected) colors.elevated else colors.surface, shape = RoundedCornerShape(9.dp),
        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected }) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, Modifier.weight(1f), fontSize = 14.sp, color = colors.text, letterSpacing = 0.sp)
            if (arabic != null) Text(arabic, color = colors.textSecondary,
                style = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Rtl, textAlign = TextAlign.End))
            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = colors.textSecondary)
        }
    }
}

@Composable
private fun ReflectionEmpty(title: String, body: String, action: String, onClick: () -> Unit) {
    val colors = VaultThemeTokens.colors
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        Text(title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            if (action == "Open Qur’an") {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(action, fontSize = 13.sp)
        }
    }
}
