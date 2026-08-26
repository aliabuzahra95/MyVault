package com.myvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens
import kotlinx.coroutines.launch

data class VaultMobileWebNavigationItem(
    val label: String,
    val icon: ImageVector,
)

enum class VaultMobileWebExplorerNodeType {
    Folder,
    Note,
    Document,
}

data class VaultMobileWebExplorerNode(
    val id: String,
    val label: String,
    val type: VaultMobileWebExplorerNodeType,
    val count: Int? = null,
    val children: List<VaultMobileWebExplorerNode> = emptyList(),
    val canAdd: Boolean = false,
    val canManage: Boolean = true,
    val pinned: Boolean = false,
    val description: String? = null,
)

data class VaultMobileWebExplorerSection(
    val navigationIndex: Int,
    val nodes: List<VaultMobileWebExplorerNode> = emptyList(),
    val canAdd: Boolean = false,
)

/**
 * Native Android shell matching MyVault Web's phone layout: compact header,
 * hamburger navigation, left drawer, and no persistent bottom navigation.
 */
@Composable
fun VaultMobileWebShell(
    workspaceLabel: String,
    accountEmail: String,
    onWorkspaceSelected: () -> Unit,
    items: List<VaultMobileWebNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onDashboardSelected: () -> Unit,
    onSearchSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
    onQuickBackupSelected: () -> Unit,
    quickBackupRecommended: Boolean = false,
    explorerSections: List<VaultMobileWebExplorerSection> = emptyList(),
    selectedExplorerNodeId: String? = null,
    onExplorerNodeSelected: (Int, VaultMobileWebExplorerNode) -> Unit = { _, _ -> },
    onExplorerAddSelected: (Int, VaultMobileWebExplorerNode?) -> Unit = { _, _ -> },
    onExplorerMoreSelected: (Int, VaultMobileWebExplorerNode) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerGestureEdgeWidth = with(density) { 24.dp.toPx() }
    val drawerGestureZoneHeight = with(density) { 200.dp.toPx() }
    var expandedExplorerKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(selectedExplorerNodeId, explorerSections) {
        val selectedId = selectedExplorerNodeId ?: return@LaunchedEffect
        explorerSections.forEach { section ->
            val path = section.nodes.findExplorerPath(selectedId) ?: return@forEach
            val keys = buildList {
                add("section:${section.navigationIndex}")
                path.dropLast(1).forEach { node ->
                    add("node:${section.navigationIndex}:${node.type}:${node.id}")
                }
            }
            expandedExplorerKeys = (expandedExplorerKeys + keys).distinct()
        }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        modifier = Modifier.systemGestureExclusion { coordinates ->
            val height = coordinates.size.height.toFloat()
            val zoneTop = ((height - drawerGestureZoneHeight) / 2f).coerceAtLeast(0f)
            Rect(
                left = 0f,
                top = zoneTop,
                right = drawerGestureEdgeWidth.coerceAtMost(coordinates.size.width.toFloat()),
                bottom = (zoneTop + drawerGestureZoneHeight).coerceAtMost(height),
            )
        },
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = colors.scrim,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(292.dp)
                    .fillMaxHeight(),
                drawerContainerColor = colors.inset,
                drawerContentColor = colors.text,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    DrawerProfileHeader(
                        accountEmail = accountEmail,
                        workspaceLabel = workspaceLabel,
                        onWorkspaceSelected = { closeDrawerThen(onWorkspaceSelected) },
                        onClose = { scope.launch { drawerState.close() } },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                    ) {
                        DrawerSectionLabel("Application")
                        DrawerNavigationRow(
                            label = "Dashboard",
                            icon = Icons.Rounded.Home,
                            selected = false,
                            onClick = { closeDrawerThen(onDashboardSelected) },
                        )
                        DrawerNavigationRow(
                            label = "Search",
                            icon = Icons.Rounded.Search,
                            selected = false,
                            onClick = { closeDrawerThen(onSearchSelected) },
                        )
                        DrawerNavigationRow(
                            label = "Settings",
                            icon = Icons.Rounded.Settings,
                            selected = false,
                            onClick = { closeDrawerThen(onSettingsSelected) },
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DrawerSectionLabel(if (workspaceLabel == "Personal") "Workspace" else "Knowledge")
                        items.forEachIndexed { index, item ->
                            val explorerSection = explorerSections.firstOrNull { it.navigationIndex == index }
                            if (explorerSection == null) {
                                DrawerNavigationRow(
                                    label = item.label,
                                    icon = item.icon,
                                    selected = selectedIndex == index,
                                    onClick = { closeDrawerThen { onItemSelected(index) } },
                                )
                            } else {
                                val sectionKey = "section:$index"
                                val expanded = sectionKey in expandedExplorerKeys
                                DrawerExplorerSectionRow(
                                    label = item.label,
                                    icon = item.icon,
                                    selected = selectedIndex == index,
                                    expanded = expanded,
                                    count = explorerSection.nodes.size.takeIf { it > 0 },
                                    canAdd = explorerSection.canAdd,
                                    onToggle = {
                                        expandedExplorerKeys = expandedExplorerKeys.toggleKey(sectionKey)
                                    },
                                    onOpen = { closeDrawerThen { onItemSelected(index) } },
                                    onAdd = {
                                        closeDrawerThen { onExplorerAddSelected(index, null) }
                                    },
                                )
                                if (expanded) {
                                    explorerSection.nodes.forEach { node ->
                                        DrawerExplorerNode(
                                            node = node,
                                            sectionIndex = index,
                                            depth = 0,
                                            expandedKeys = expandedExplorerKeys,
                                            selectedNodeId = selectedExplorerNodeId,
                                            onToggle = { key ->
                                                expandedExplorerKeys = expandedExplorerKeys.toggleKey(key)
                                            },
                                            onOpen = { selectedNode ->
                                                closeDrawerThen { onExplorerNodeSelected(index, selectedNode) }
                                            },
                                            onAdd = { selectedNode ->
                                                closeDrawerThen { onExplorerAddSelected(index, selectedNode) }
                                            },
                                            onMore = { selectedNode ->
                                                closeDrawerThen { onExplorerMoreSelected(index, selectedNode) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DrawerUtilityRow(
                        accountEmail = accountEmail,
                        quickBackupRecommended = quickBackupRecommended,
                        onQuickBackupSelected = { closeDrawerThen(onQuickBackupSelected) },
                        onSettingsSelected = { closeDrawerThen(onSettingsSelected) },
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
            ) {
                Surface(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp),
                    shape = VaultShapes.sm,
                    color = Color.Transparent,
                    contentColor = colors.textSecondary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "Open navigation",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = "MyVault",
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W800,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

private fun List<String>.toggleKey(key: String): List<String> =
    if (key in this) this - key else this + key

private fun List<VaultMobileWebExplorerNode>.findExplorerPath(
    selectedId: String,
): List<VaultMobileWebExplorerNode>? {
    for (node in this) {
        if (node.id == selectedId) return listOf(node)
        node.children.findExplorerPath(selectedId)?.let { childPath ->
            return listOf(node) + childPath
        }
    }
    return null
}

@Composable
private fun DrawerExplorerSectionRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    count: Int?,
    canAdd: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (selected) colors.elevated else Color.Transparent, VaultShapes.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.size(30.dp),
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(17.dp), tint = if (selected) colors.accent else colors.textSecondary)
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.text else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.W700 else FontWeight.W600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            count?.let {
                Text(text = it.toString(), color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.W600)
            }
        }
        if (canAdd) {
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(30.dp),
                color = Color.Transparent,
                contentColor = colors.accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to $label", modifier = Modifier.size(17.dp))
                }
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
private fun DrawerExplorerNode(
    node: VaultMobileWebExplorerNode,
    sectionIndex: Int,
    depth: Int,
    expandedKeys: List<String>,
    selectedNodeId: String?,
    onToggle: (String) -> Unit,
    onOpen: (VaultMobileWebExplorerNode) -> Unit,
    onAdd: (VaultMobileWebExplorerNode) -> Unit,
    onMore: (VaultMobileWebExplorerNode) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val nodeKey = "node:$sectionIndex:${node.type}:${node.id}"
    val expandable = node.type == VaultMobileWebExplorerNodeType.Folder && node.children.isNotEmpty()
    val expanded = nodeKey in expandedKeys
    val selected = node.id == selectedNodeId
    val indent = (18 + depth * 14).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (node.type == VaultMobileWebExplorerNodeType.Folder) 38.dp else 34.dp)
            .background(if (selected) colors.elevated else Color.Transparent, VaultShapes.sm)
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.type == VaultMobileWebExplorerNodeType.Folder) {
            Surface(
                onClick = { if (expandable) onToggle(nodeKey) else onOpen(node) },
                modifier = Modifier.size(27.dp),
                color = Color.Transparent,
                contentColor = colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (expandable) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                            contentDescription = if (expanded) "Collapse ${node.label}" else "Expand ${node.label}",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.width(27.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { onOpen(node) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (node.type) {
                    VaultMobileWebExplorerNodeType.Folder -> Icons.Rounded.Folder
                    VaultMobileWebExplorerNodeType.Note -> Icons.Rounded.Description
                    VaultMobileWebExplorerNodeType.Document -> Icons.Rounded.PictureAsPdf
                },
                contentDescription = null,
                modifier = Modifier.size(if (node.type == VaultMobileWebExplorerNodeType.Folder) 16.dp else 15.dp),
                tint = if (node.type == VaultMobileWebExplorerNodeType.Folder) colors.accent else colors.textSecondary,
            )
            Text(
                text = node.label,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.text else colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = if (node.type == VaultMobileWebExplorerNodeType.Folder) FontWeight.W600 else FontWeight.W500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            node.count?.let {
                Text(text = it.toString(), color = colors.textMuted, fontSize = 9.sp, fontWeight = FontWeight.W600)
            }
        }
        if (node.canAdd) {
            Surface(onClick = { onAdd(node) }, modifier = Modifier.size(27.dp), color = Color.Transparent, contentColor = colors.accent) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to ${node.label}", modifier = Modifier.size(15.dp))
                }
            }
        }
        if (node.canManage) {
            Surface(onClick = { onMore(node) }, modifier = Modifier.size(27.dp), color = Color.Transparent, contentColor = colors.textMuted) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options for ${node.label}", modifier = Modifier.size(15.dp))
                }
            }
        }
    }
    if (expanded) {
        node.children.forEach { child ->
            DrawerExplorerNode(
                node = child,
                sectionIndex = sectionIndex,
                depth = depth + 1,
                expandedKeys = expandedKeys,
                selectedNodeId = selectedNodeId,
                onToggle = onToggle,
                onOpen = onOpen,
                onAdd = onAdd,
                onMore = onMore,
            )
        }
    }
}

@Composable
private fun DrawerProfileHeader(
    accountEmail: String,
    workspaceLabel: String,
    onWorkspaceSelected: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val accountLabel = accountEmail
        .substringBefore("@")
        .trim()
        .ifBlank { "Your account" }
    val initials = accountLabel
        .split(" ", ".", "_", "-")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "MV" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onWorkspaceSelected),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W700,
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = accountLabel,
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = workspaceLabel.uppercase(),
                    color = colors.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                )
            }
        }
        Surface(
            onClick = onClose,
            modifier = Modifier.size(36.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = "Close navigation", modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    val colors = VaultThemeTokens.colors
    Text(
        text = label.uppercase(),
        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 5.dp),
        color = colors.textMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.W800,
    )
}

@Composable
private fun DrawerNavigationRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val containerColor = if (selected) colors.elevated else Color.Transparent
    val contentColor = if (selected) colors.text else colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(containerColor, VaultShapes.sm)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = if (selected) colors.accent else colors.textSecondary,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerUtilityRow(
    accountEmail: String,
    quickBackupRecommended: Boolean,
    onQuickBackupSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val connected = accountEmail.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        HorizontalDivider(color = colors.border.copy(alpha = 0.55f))
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(40.dp)
                .background(colors.elevated.copy(alpha = 0.62f), VaultShapes.sm)
                .clickable(onClick = onSettingsSelected)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CloudQueue, null, modifier = Modifier.size(17.dp), tint = colors.textSecondary)
            Text(
                text = if (connected) "Drive connected" else "Drive not connected",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (connected) colors.success else colors.textMuted, CircleShape),
            )
            Surface(
                onClick = onQuickBackupSelected,
                enabled = connected,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(32.dp),
                shape = VaultShapes.sm,
                color = if (quickBackupRecommended && connected) colors.accentSoft else Color.Transparent,
                contentColor = if (connected) colors.accent else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Backup,
                        contentDescription = "Back up to Google Drive",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Icon(
                Icons.Rounded.PersonOutline,
                contentDescription = "Account and settings",
                modifier = Modifier
                    .padding(start = 14.dp)
                    .size(18.dp),
                tint = colors.textSecondary,
            )
        }
    }
}
