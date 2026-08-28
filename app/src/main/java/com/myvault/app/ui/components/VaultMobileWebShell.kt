package com.myvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DrawerValue
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
import androidx.compose.ui.graphics.RectangleShape
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

enum class VaultMobileWebApplicationDestination {
    Dashboard,
    Search,
    Settings,
}

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
    onWorkspaceSelected: (String) -> Unit,
    items: List<VaultMobileWebNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onDashboardSelected: () -> Unit,
    onSearchSelected: () -> Unit,
    onAttachmentsSelected: () -> Unit,
    onFavouritesSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
    onThemeSelected: () -> Unit,
    selectedApplicationDestination: VaultMobileWebApplicationDestination? = null,
    attachmentsSelected: Boolean = false,
    favouritesSelected: Boolean = false,
    explorerSections: List<VaultMobileWebExplorerSection> = emptyList(),
    selectedExplorerNodeId: String? = null,
    onExplorerNodeSelected: (Int, VaultMobileWebExplorerNode) -> Unit = { _, _ -> },
    onExplorerAddSelected: (Int, VaultMobileWebExplorerNode?) -> Unit = { _, _ -> },
    onExplorerMoreSelected: (Int, VaultMobileWebExplorerNode) -> Unit = { _, _ -> },
    contentStartsInMenuBar: Boolean = false,
    menuVisible: Boolean = true,
    drawerGesturesEnabled: Boolean = true,
    content: @Composable (onOpenNavigation: () -> Unit) -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerGestureEdgeWidth = with(density) { 24.dp.toPx() }
    val drawerGestureZoneHeight = with(density) { 200.dp.toPx() }
    var expandedExplorerKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var workspaceChooserOpen by rememberSaveable { mutableStateOf(false) }

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

    val drawerModifier = if (drawerGesturesEnabled) {
        Modifier.systemGestureExclusion { coordinates ->
            val height = coordinates.size.height.toFloat()
            val zoneTop = ((height - drawerGestureZoneHeight) / 2f).coerceAtLeast(0f)
            Rect(
                left = 0f,
                top = zoneTop,
                right = drawerGestureEdgeWidth.coerceAtMost(coordinates.size.width.toFloat()),
                bottom = (zoneTop + drawerGestureZoneHeight).coerceAtMost(height),
            )
        }
    } else {
        Modifier
    }

    ModalNavigationDrawer(
        modifier = drawerModifier,
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        scrimColor = colors.scrim,
        drawerContent = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val drawerWidth = (maxWidth - 46.dp).coerceAtMost(366.dp).coerceAtLeast(0.dp)
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(drawerWidth)
                        .fillMaxHeight(),
                    drawerContainerColor = colors.inset,
                    drawerContentColor = colors.text,
                    drawerShape = RectangleShape,
                    drawerTonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    ) {
                        DrawerProfileHeader(
                            accountEmail = accountEmail,
                            workspaceLabel = workspaceLabel,
                            onWorkspaceSelected = { closeDrawerThen { workspaceChooserOpen = true } },
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
                                icon = Icons.Outlined.Home,
                                selected = selectedApplicationDestination == VaultMobileWebApplicationDestination.Dashboard,
                                onClick = { closeDrawerThen(onDashboardSelected) },
                            )
                            DrawerNavigationRow(
                                label = "Search",
                                icon = Icons.Outlined.Search,
                                selected = selectedApplicationDestination == VaultMobileWebApplicationDestination.Search,
                                onClick = { closeDrawerThen(onSearchSelected) },
                            )
                            DrawerNavigationRow(
                                label = "Settings",
                                icon = Icons.Outlined.Settings,
                                selected = selectedApplicationDestination == VaultMobileWebApplicationDestination.Settings,
                                onClick = { closeDrawerThen(onSettingsSelected) },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
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
                                        canAdd = explorerSection.canAdd,
                                        onToggle = {
                                            expandedExplorerKeys = expandedExplorerKeys.toggleKey(sectionKey)
                                        },
                                        onOpen = { closeDrawerThen { onItemSelected(index) } },
                                        onAdd = {
                                            closeDrawerThen { onExplorerAddSelected(index, null) }
                                        },
                                    )
                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically(animationSpec = tween(190)) + fadeIn(animationSpec = tween(150)),
                                        exit = shrinkVertically(animationSpec = tween(170)) + fadeOut(animationSpec = tween(130)),
                                    ) {
                                        Column {
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
                                if (item.label == "Library") {
                                    DrawerNavigationRow(
                                        label = "Workspace Attachments",
                                        icon = Icons.Outlined.AttachFile,
                                        selected = attachmentsSelected,
                                        onClick = { closeDrawerThen(onAttachmentsSelected) },
                                    )
                                    DrawerNavigationRow(
                                        label = "Favourites",
                                        icon = Icons.Outlined.StarOutline,
                                        selected = favouritesSelected,
                                        onClick = { closeDrawerThen(onFavouritesSelected) },
                                    )
                                }
                            }
                        }
                        DrawerUtilityRow(
                            accountEmail = accountEmail,
                            onDriveSelected = { closeDrawerThen(onSettingsSelected) },
                            onThemeSelected = { closeDrawerThen(onThemeSelected) },
                            onSettingsSelected = { closeDrawerThen(onSettingsSelected) },
                        )
                    }
                }
            }
        },
    ) {
        val menuButton: @Composable BoxScope.() -> Unit = {
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
        }
        if (contentStartsInMenuBar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bg)
                    .statusBarsPadding(),
            ) {
                content { scope.launch { drawerState.open() } }
                if (menuVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 12.dp),
                        content = menuButton,
                    )
                }
            }
        } else {
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
                    content = menuButton,
                )
                Box(modifier = Modifier.weight(1f)) {
                    content { scope.launch { drawerState.open() } }
                }
            }
        }
    }

    if (workspaceChooserOpen) {
        VaultActionModal(
            title = "Switch workspace",
            actions = listOf(
                VaultModalAction(
                    label = "Islamic Corpus",
                    icon = Icons.Outlined.AutoStories,
                    selected = workspaceLabel == "Islamic Corpus",
                    onClick = {
                        workspaceChooserOpen = false
                        if (workspaceLabel != "Islamic Corpus") onWorkspaceSelected("Islamic Corpus")
                    },
                ),
                VaultModalAction(
                    label = "Personal",
                    icon = Icons.Outlined.PersonOutline,
                    selected = workspaceLabel == "Personal",
                    onClick = {
                        workspaceChooserOpen = false
                        if (workspaceLabel != "Personal") onWorkspaceSelected("Personal")
                    },
                ),
            ),
            onDismiss = { workspaceChooserOpen = false },
        )
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
    canAdd: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 37.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.size(27.dp),
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse $label" else "Expand $label",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (selected) colors.accent else colors.textSecondary)
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.text else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canAdd) {
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(31.dp),
                color = Color.Transparent,
                contentColor = colors.accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to $label", modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val indent = (12 + depth * 12).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (node.type == VaultMobileWebExplorerNodeType.Folder) 36.dp else 31.dp)
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.type == VaultMobileWebExplorerNodeType.Folder) {
            Surface(
                onClick = { if (expandable) onToggle(nodeKey) else onOpen(node) },
                modifier = Modifier.size(24.dp),
                color = Color.Transparent,
                contentColor = colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (expandable) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                            contentDescription = if (expanded) "Collapse ${node.label}" else "Expand ${node.label}",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = { onOpen(node) },
                    onLongClick = if (node.canManage) ({ onMore(node) }) else null,
                ),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (node.type) {
                    VaultMobileWebExplorerNodeType.Folder -> Icons.Outlined.Folder
                    VaultMobileWebExplorerNodeType.Note -> Icons.Outlined.Description
                    VaultMobileWebExplorerNodeType.Document -> Icons.Outlined.PictureAsPdf
                },
                contentDescription = null,
                modifier = Modifier.size(if (node.type == VaultMobileWebExplorerNodeType.Folder) 18.dp else 16.dp),
                tint = if (selected) colors.accent else colors.textSecondary,
            )
            Text(
                text = node.label,
                modifier = Modifier.weight(1f),
                color = if (selected) colors.text else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (node.type == VaultMobileWebExplorerNodeType.Folder || selected) FontWeight.W700 else FontWeight.W500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            node.count?.let {
                Text(text = it.toString(), color = colors.textMuted, fontSize = 10.sp, fontWeight = FontWeight.W500)
            }
            if (selected && node.type != VaultMobileWebExplorerNodeType.Folder) {
                Box(modifier = Modifier.size(5.dp).background(colors.accent, CircleShape))
            }
        }
        if (node.canAdd) {
            Surface(onClick = { onAdd(node) }, modifier = Modifier.size(31.dp), color = Color.Transparent, contentColor = colors.accent) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add to ${node.label}", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(animationSpec = tween(190)) + fadeIn(animationSpec = tween(150)),
        exit = shrinkVertically(animationSpec = tween(170)) + fadeOut(animationSpec = tween(130)),
    ) {
        Column {
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
            .heightIn(min = 62.dp)
            .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onWorkspaceSelected),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(colors.accentSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = accountLabel,
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = workspaceLabel.uppercase(),
                    color = colors.textMuted,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.W700,
                    maxLines = 1,
                )
            }
        }
        Surface(
            onClick = onClose,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = "Close navigation", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    val colors = VaultThemeTokens.colors
    Text(
        text = label.uppercase(),
        modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp),
        color = colors.textMuted,
        fontSize = 9.5.sp,
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
    val contentColor = if (selected) colors.text else colors.textSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) colors.accent else colors.textSecondary,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerUtilityRow(
    accountEmail: String,
    onDriveSelected: () -> Unit,
    onThemeSelected: () -> Unit,
    onSettingsSelected: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val connected = accountEmail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .heightIn(min = 56.dp)
            .background(colors.elevated.copy(alpha = 0.48f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clickable(onClick = onDriveSelected)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudQueue, null, modifier = Modifier.size(17.dp), tint = colors.textSecondary)
            Text(
                text = if (connected) "Drive connected" else "Drive not connected",
                modifier = Modifier.padding(start = 9.dp, end = 7.dp),
                color = colors.textSecondary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W500,
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (connected) colors.success else colors.textMuted, CircleShape),
            )
        }
        Surface(
            onClick = onThemeSelected,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LightMode, contentDescription = "Change theme", modifier = Modifier.size(19.dp))
            }
        }
        Surface(
            onClick = onSettingsSelected,
            modifier = Modifier.size(40.dp),
            shape = VaultShapes.sm,
            color = Color.Transparent,
            contentColor = colors.textSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = "Account and settings", modifier = Modifier.size(19.dp))
            }
        }
    }
}
