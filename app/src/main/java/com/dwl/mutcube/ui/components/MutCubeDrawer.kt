package com.dwl.mutcube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId

@Composable
fun MutCubeDrawer(
    spaces: List<Space>,
    conversations: List<Conversation>,
    searchQuery: String,
    searchResults: List<Conversation>,
    selectedConversationId: ConversationId?,
    selectedSpaceId: SpaceId?,
    onConversationClick: (ConversationId) -> Unit,
    onConversationRename: (Conversation) -> Unit,
    onConversationRegenerateTitle: (ConversationId) -> Unit,
    onConversationPin: (ConversationId, Boolean) -> Unit,
    onConversationMove: (Conversation) -> Unit,
    onConversationDelete: (ConversationId) -> Unit,
    onConversationExport: (Conversation) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAllChatsClick: () -> Unit,
    onSpaceClick: (SpaceId) -> Unit,
    onSpaceRename: (Space) -> Unit,
    onSpacePin: (SpaceId, Boolean) -> Unit,
    onSpaceDelete: (SpaceId) -> Unit,
    onSpaceSettings: (Space) -> Unit,
    onSpaceShortcut: (Space) -> Unit,
    onCreateSpace: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChat: () -> Unit,
    userNickname: String,
    userAvatarFile: String,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearch by remember { mutableStateOf(false) }
    val pinnedSpaces = spaces.filter(Space::pinned)
    val pinnedConversations = conversations.filter(Conversation::pinned)
    val recent = conversations
        .filter { !it.pinned && it.spaceId == null }
        .sortedByDescending(Conversation::updatedAt)
    ModalDrawerSheet(
        modifier = modifier.fillMaxWidth(0.86f),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MutCubeMark()
                    Text(
                        "MutCube",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(
                    onClick = {
                        showSearch = !showSearch
                        if (!showSearch) onSearchQueryChange("")
                    },
                ) { Icon(Icons.Rounded.Search, contentDescription = "搜索") }
            }

            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    placeholder = { Text("搜索标题和消息内容") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                if (searchQuery.isNotBlank()) {
                    DrawerSection("搜索结果") {
                        searchResults.forEach { conversation ->
                            DrawerRow(
                                icon = null,
                                text = conversation.title,
                                meta = conversation.updatedAt.toRelativeLabel(),
                                selected = conversation.id == selectedConversationId,
                                onClick = { onConversationClick(conversation.id) },
                                onMove = { onConversationMove(conversation) },
                            )
                        }
                        if (searchResults.isEmpty()) {
                            Text("没有匹配的对话", modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }

            ProfileCard(
                nickname = userNickname,
                avatarFile = userAvatarFile,
                onProfileClick = onProfileClick,
                onFavoritesClick = onFavoritesClick,
                onSettingsClick = onSettingsClick,
            )
            Surface(
                onClick = onAllChatsClick,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BookmarkBorder, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("所有聊天", modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.Medium)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
            DrawerSection("置顶") {
                pinnedSpaces.forEach { space ->
                    DrawerRow(
                        icon = Icons.Rounded.Folder,
                        text = space.name,
                        meta = "项目",
                        selected = space.id == selectedSpaceId,
                        onClick = { onSpaceClick(space.id) },
                        onRename = { onSpaceRename(space) },
                        onPin = { onSpacePin(space.id, false) },
                        pinLabel = "取消置顶",
                        onDelete = { onSpaceDelete(space.id) },
                        onSettings = { onSpaceSettings(space) },
                        onShortcut = { onSpaceShortcut(space) },
                    )
                }
                pinnedConversations.forEach { conversation ->
                    DrawerRow(
                        icon = Icons.Rounded.PushPin,
                        text = conversation.title,
                        meta = "对话",
                        selected = conversation.id == selectedConversationId,
                        onClick = { onConversationClick(conversation.id) },
                        onRename = { onConversationRename(conversation) },
                        onRegenerateTitle = { onConversationRegenerateTitle(conversation.id) },
                        onPin = { onConversationPin(conversation.id, false) },
                        pinLabel = "取消置顶",
                        onMove = { onConversationMove(conversation) },
                        onDelete = { onConversationDelete(conversation.id) },
                        onExport = { onConversationExport(conversation) },
                    )
                }
            }
            DrawerSection("项目") {
                spaces.forEach { space ->
                    DrawerRow(
                        icon = Icons.Rounded.Folder,
                        text = space.name,
                        selected = space.id == selectedSpaceId,
                        onClick = { onSpaceClick(space.id) },
                        onRename = { onSpaceRename(space) },
                        onPin = { onSpacePin(space.id, !space.pinned) },
                        pinLabel = if (space.pinned) "取消置顶" else "置顶",
                        onDelete = { onSpaceDelete(space.id) },
                        onSettings = { onSpaceSettings(space) },
                        onShortcut = { onSpaceShortcut(space) },
                    )
                }
                TextButton(onClick = onCreateSpace) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("新建项目", modifier = Modifier.padding(start = 6.dp))
                }
            }
            DrawerSection("最近") {
                recent.forEach { conversation ->
                    DrawerRow(
                        icon = null,
                        text = conversation.title,
                        meta = conversation.updatedAt.toRelativeLabel(),
                        selected = conversation.id == selectedConversationId,
                        onClick = { onConversationClick(conversation.id) },
                        onRename = { onConversationRename(conversation) },
                        onRegenerateTitle = { onConversationRegenerateTitle(conversation.id) },
                        onPin = { onConversationPin(conversation.id, !conversation.pinned) },
                        pinLabel = if (conversation.pinned) "取消置顶" else "置顶",
                        onMove = { onConversationMove(conversation) },
                        onDelete = { onConversationDelete(conversation.id) },
                        onExport = { onConversationExport(conversation) },
                    )
                }
                if (recent.isEmpty()) {
                    Text(
                        "暂无对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
            }

            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("新建对话", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    nickname: String,
    avatarFile: String,
    onProfileClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                Box(Modifier.clickable(onClick = onProfileClick)) { UserAvatar(avatarFile) }
                Column(Modifier.weight(1f).clickable(onClick = onProfileClick).padding(start = 12.dp)) {
                    Text(nickname.trim().ifBlank { "用户" }, fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "本地个人空间",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavoritesClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.BookmarkBorder, contentDescription = "收藏", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettingsClick, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置", modifier = Modifier.size(20.dp))
                }
            }
    }
}

@Composable
fun MutCubeMark(modifier: Modifier = Modifier, size: Dp = 25.dp) {
    val markColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(size)) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val center = this.center
            val strokeWidth = 1.5.dp.toPx()
            val inset = size.toPx() * 0.21f
            val outerRadius = size.toPx() * 0.24f + 2.dp.toPx()
            val innerRadius = size.toPx() * 0.17f
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokeWidth
            }

            nativeCanvas.save()
            nativeCanvas.translate(center.x, center.y)
            nativeCanvas.rotate(30f)
            nativeCanvas.skew(-0.07f, -0.07f)
            nativeCanvas.translate(-center.x, -center.y)

            paint.color = markColor.copy(alpha = 0.34f).toArgb()
            nativeCanvas.drawRoundRect(
                strokeWidth / 2,
                strokeWidth / 2,
                size.toPx() - strokeWidth / 2,
                size.toPx() - strokeWidth / 2,
                outerRadius,
                outerRadius,
                paint,
            )
            paint.color = markColor.copy(alpha = 0.22f).toArgb()
            nativeCanvas.drawRoundRect(
                inset,
                inset,
                size.toPx() - inset,
                size.toPx() - inset,
                innerRadius,
                innerRadius,
                paint,
            )
            nativeCanvas.restore()
        }
    }
}

@Composable
private fun DrawerSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector?,
    text: String,
    meta: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onRename: (() -> Unit)? = null,
    onRegenerateTitle: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    pinLabel: String? = null,
    onDelete: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onShortcut: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
            .background(
                color = if (selected) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
        }
        Text(text, modifier = Modifier.weight(1f), maxLines = 1)
        if (meta != null) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (
            onRename != null || onRegenerateTitle != null || onPin != null || onDelete != null ||
            onSettings != null || onMove != null || onExport != null || onShortcut != null
        ) {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "对话操作", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                onRename?.let {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false; it() })
                }
                onRegenerateTitle?.let {
                    DropdownMenuItem(text = { Text("重新生成标题") }, onClick = { menuExpanded = false; it() })
                }
                onPin?.let {
                    DropdownMenuItem(text = { Text(pinLabel ?: "置顶") }, onClick = { menuExpanded = false; it() })
                }
                onSettings?.let {
                    DropdownMenuItem(text = { Text("项目设置") }, onClick = { menuExpanded = false; it() })
                }
                onMove?.let {
                    DropdownMenuItem(text = { Text("移动到项目…") }, onClick = { menuExpanded = false; it() })
                }
                onExport?.let {
                    DropdownMenuItem(text = { Text("导出") }, onClick = { menuExpanded = false; it() })
                }
                onShortcut?.let {
                    DropdownMenuItem(text = { Text("添加到桌面") }, onClick = { menuExpanded = false; it() })
                }
                onDelete?.let {
                    DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; it() })
                }
            }
        }
    }
}

private fun Long.toRelativeLabel(): String {
    val elapsed = (System.currentTimeMillis() - this).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "刚刚"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} 分钟前"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} 小时前"
        else -> "${elapsed / 86_400_000L} 天前"
    }
}
