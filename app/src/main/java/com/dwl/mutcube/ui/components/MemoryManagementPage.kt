package com.dwl.mutcube.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.MemoryEntry
import com.dwl.mutcube.core.model.MemoryId
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId

@Composable
fun MemoryManagementPage(
    memories: List<MemoryEntry>,
    spaces: List<Space>,
    onDelete: (MemoryId) -> Unit,
    onAdd: (SpaceId?, String) -> Unit,
    onUpdate: (MemoryId, String) -> Unit,
    onBack: () -> Unit,
    initialSpaceId: SpaceId? = null,
) {
    val spaceNames = spaces.associate { it.id to it.name }
    var filter by remember(initialSpaceId) { mutableStateOf(if (initialSpaceId == null) "all" else initialSpaceId.value) }
    var editing by remember { mutableStateOf<MemoryEntry?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MemoryEntry?>(null) }
    var draft by remember { mutableStateOf("") }
    val visible = memories.filter { memory ->
        when (filter) {
            "all" -> true
            "global" -> memory.spaceId == null
            else -> memory.spaceId?.value == filter
        }
    }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader(if (initialSpaceId == null) "记忆管理" else "项目记忆", onBack)
        Text(
            "记忆保存长期事实与偏好；训练记录等精确业务数据仍以模板为准。会话摘要不在此处显示。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter == "all", { filter = "all" }, label = { Text("全部") })
            FilterChip(filter == "global", { filter = "global" }, label = { Text("全局") })
            spaces.forEach { space ->
                FilterChip(filter == space.id.value, { filter = space.id.value }, label = { Text(space.name) })
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${visible.size} 条记忆", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { draft = ""; adding = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加记忆")
            }
        }
        if (visible.isEmpty()) {
            Text("这里还没有记忆", modifier = Modifier.padding(22.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp)) {
                items(visible, key = { it.id.value }) { memory ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                                Text(memory.spaceId?.let { spaceNames[it] ?: "已移除项目" } ?: "全局记忆",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            IconButton(onClick = { draft = memory.content; editing = memory }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "修改记忆")
                            }
                            IconButton(onClick = { deleting = memory }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除记忆")
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val target = editing
        var scopeId by remember(adding, target, filter) {
            mutableStateOf(target?.spaceId ?: spaces.firstOrNull { it.id.value == filter }?.id)
        }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (target == null) "添加记忆" else "修改记忆") },
            text = {
                Column {
                    OutlinedTextField(draft, { draft = it.take(2_000) }, Modifier.fillMaxWidth(),
                        label = { Text("长期事实或偏好") }, minLines = 3)
                    if (target == null) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(scopeId == null, { scopeId = null }, label = { Text("全局") })
                            spaces.forEach { space ->
                                FilterChip(scopeId == space.id, { scopeId = space.id }, label = { Text(space.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    if (target == null) onAdd(scopeId, draft.trim()) else onUpdate(target.id, draft.trim())
                    adding = false; editing = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("取消") } },
        )
    }
    deleting?.let { memory ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记忆？") },
            text = { Text("删除后，AI 将无法再从记忆中检索这条内容；原始聊天记录不受影响。") },
            confirmButton = { TextButton(onClick = { onDelete(memory.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}
