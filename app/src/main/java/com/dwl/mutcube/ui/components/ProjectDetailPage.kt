package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.Space

@Composable
fun ProjectDetailPage(
    space: Space,
    conversations: List<Conversation>,
    memoryCount: Int,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onFocusComposer: () -> Unit,
    onConversationClick: (Conversation) -> Unit,
    onConversationPin: (Conversation) -> Unit,
    onConversationRename: (Conversation) -> Unit,
    onConversationRemove: (Conversation) -> Unit,
    onConversationDelete: (Conversation) -> Unit,
    onSettings: () -> Unit,
    onMemory: () -> Unit,
    onRename: () -> Unit,
    onShortcut: () -> Unit,
    onDelete: () -> Unit,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                }
            }
            Text(
                space.name,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Box {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = "项目菜单")
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("项目设置") }, onClick = { menuExpanded = false; onSettings() })
                    DropdownMenuItem(text = { Text("项目记忆") }, onClick = { menuExpanded = false; onMemory() })
                    DropdownMenuItem(text = { Text("重命名项目") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("添加到桌面") }, onClick = { menuExpanded = false; onShortcut() })
                    DropdownMenuItem(text = { Text("删除项目") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "项目聚合相关会话、模板、记忆和可选模型设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Icon(Icons.Rounded.Folder, null, Modifier.padding(12.dp).size(22.dp))
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(space.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${conversations.size} 个会话 · $memoryCount 条项目记忆",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProjectShortcut(
                                icon = Icons.Rounded.ViewInAr,
                                label = "打开模板",
                                onClick = onOpenTemplates,
                                modifier = Modifier.weight(1f),
                            )
                            ProjectShortcut(
                                icon = Icons.Rounded.Edit,
                                label = "新建对话",
                                onClick = onFocusComposer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "最近会话",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    if (conversations.isEmpty()) {
                        Text(
                            "这个项目还没有会话，可以直接在下方输入内容开始。",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column {
                            conversations.sortedByDescending(Conversation::updatedAt).take(8).forEach { conversation ->
                                var conversationMenuExpanded by remember(conversation.id) { mutableStateOf(false) }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { onConversationClick(conversation) },
                                                onLongClick = { conversationMenuExpanded = true },
                                            )
                                            .padding(horizontal = 15.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(conversation.title, fontWeight = FontWeight.Medium, maxLines = 1)
                                            Text(
                                                conversation.updatedAt.toProjectRelativeLabel(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                    DropdownMenu(
                                        expanded = conversationMenuExpanded,
                                        onDismissRequest = { conversationMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (conversation.pinned) "取消置顶" else "置顶") },
                                            onClick = { conversationMenuExpanded = false; onConversationPin(conversation) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("重命名") },
                                            onClick = { conversationMenuExpanded = false; onConversationRename(conversation) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("移出项目") },
                                            onClick = { conversationMenuExpanded = false; onConversationRemove(conversation) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                                            onClick = { conversationMenuExpanded = false; onConversationDelete(conversation) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(2.dp)) }
        }
        composer()
    }
}

@Composable
private fun ProjectShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun Long.toProjectRelativeLabel(): String {
    val elapsed = (System.currentTimeMillis() - this).coerceAtLeast(0L)
    return when {
        elapsed < 60_000L -> "刚刚"
        elapsed < 3_600_000L -> "${elapsed / 60_000L} 分钟前"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L} 小时前"
        else -> "${elapsed / 86_400_000L} 天前"
    }
}
