package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.feature.chat.ConversationExportFormat

@Composable
fun ExportConversationDialog(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onSelect: (ConversationExportFormat) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出对话") },
        text = {
            Column {
                Text(conversation.title, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedButton(
                    onClick = { onSelect(ConversationExportFormat.MARKDOWN) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Markdown（便于阅读）") }
                OutlinedButton(
                    onClick = { onSelect(ConversationExportFormat.JSON) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("JSON（结构化备份）") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
