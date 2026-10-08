package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.FavoriteMessage
import com.dwl.mutcube.core.model.Space
import java.util.concurrent.TimeUnit

private enum class ConversationFilter { ALL, PINNED, RECENT }

@Composable
fun AllChatsPage(
    conversations: List<Conversation>,
    searchResults: List<Conversation>,
    spaces: List<Space>,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    onRename: (Conversation) -> Unit,
    onPin: (Conversation, Boolean) -> Unit,
    onMove: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    onOpenTrash: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ConversationFilter.ALL) }
    val visible = remember(conversations, searchResults, query, filter) {
        (if (query.isBlank()) conversations else searchResults).asSequence()
            .filter {
                when (filter) {
                    ConversationFilter.ALL -> true
                    ConversationFilter.PINNED -> it.pinned
                    ConversationFilter.RECENT -> !it.pinned
                }
            }
            .sortedByDescending(Conversation::updatedAt)
            .toList()
    }

    Column(modifier.fillMaxSize()) {
        SettingsPageHeader("所有聊天", onBack)
        Text(
            "全局会话入口，不作为项目显示。会话仍可归属于某个项目。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onSearchQueryChange(it) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("搜索标题与消息内容") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("全部", filter == ConversationFilter.ALL) { filter = ConversationFilter.ALL }
            FilterChip("置顶", filter == ConversationFilter.PINNED) { filter = ConversationFilter.PINNED }
            FilterChip("最近", filter == ConversationFilter.RECENT) { filter = ConversationFilter.RECENT }
            TextButton(onClick = onOpenTrash) { Text("回收站") }
        }
        if (visible.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    if (query.isBlank()) "这里还没有符合条件的对话" else "没有匹配的对话",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id.value }) { conversation ->
                    ConversationLibraryRow(
                        conversation = conversation,
                        projectName = spaces.firstOrNull { it.id == conversation.spaceId }?.name,
                        onClick = { onConversationClick(conversation) },
                        onRename = { onRename(conversation) },
                        onPin = { onPin(conversation, !conversation.pinned) },
                        onMove = { onMove(conversation) },
                        onDelete = { onDelete(conversation) },
                    )
                }
            }
        }
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) { Text("新建对话") }
    }
}

@Composable
fun FavoritesPage(
    favorites: List<FavoriteMessage>,
    onBack: () -> Unit,
    onOpenConversation: (FavoriteMessage) -> Unit,
    onRemove: (FavoriteMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SettingsPageHeader("收藏", onBack)
        Text(
            "收藏绑定具体消息版本，切换回答分支不会丢失。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        if (favorites.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text("还没有收藏的消息", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(favorites, key = { it.messageId.value }) { favorite ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenConversation(favorite) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(favorite.text.ifBlank { "非文本消息" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${favorite.conversationTitle} · ${favorite.createdAt.relativeLabel()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 5.dp),
                                )
                            }
                            IconButton(onClick = { onRemove(favorite) }) {
                                Icon(Icons.Rounded.BookmarkRemove, contentDescription = "取消收藏")
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ConversationLibraryRow(
    conversation: Conversation,
    projectName: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(conversation.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${projectName ?: "未加入项目"} · ${conversation.updatedAt.relativeLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "对话操作")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(if (conversation.pinned) "取消置顶" else "置顶") }, onClick = { menuExpanded = false; onPin() })
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false; onRename() })
                DropdownMenuItem(text = { Text("移动到项目…") }, onClick = { menuExpanded = false; onMove() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}

private fun Long.relativeLabel(now: Long = System.currentTimeMillis()): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes((now - this).coerceAtLeast(0))
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}
