package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import java.text.DateFormat
import java.util.Date

@Composable
fun TrashPage(
    conversations: List<Conversation>,
    onBack: () -> Unit,
    onRestore: (ConversationId) -> Unit,
    onRestoreAll: () -> Unit,
    onDeletePermanently: (ConversationId) -> Unit,
    onEmpty: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("回收站", onBack)
        if (conversations.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("回收站为空", style = MaterialTheme.typography.titleMedium)
                Text("删除的对话会保留在这里，直到你永久删除。", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(conversations, key = { it.id.value }) { conversation ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(conversation.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(
                                    "删除于 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.deletedAt ?: 0L))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRestore(conversation.id) }) {
                                Icon(Icons.Rounded.Restore, "恢复对话")
                            }
                            IconButton(onClick = { deleteTarget = conversation }) {
                                Icon(Icons.Rounded.DeleteForever, "永久删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onRestoreAll, modifier = Modifier.weight(1f)) { Text("全部恢复") }
                Button(onClick = { confirmEmpty = true }, modifier = Modifier.weight(1f)) { Text("清空回收站") }
            }
        }
    }
    deleteTarget?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("永久删除对话？") },
            text = { Text("“${conversation.title}”及其消息和附件引用将无法恢复。") },
            confirmButton = { TextButton(onClick = { onDeletePermanently(conversation.id); deleteTarget = null }) { Text("永久删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("清空回收站？") },
            text = { Text("回收站中的所有对话都会被永久删除，此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { onEmpty(); confirmEmpty = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("取消") } },
        )
    }
}
