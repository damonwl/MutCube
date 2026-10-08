package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.Conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationActionsSheet(
    conversation: Conversation,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onTemplateCapabilities: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
            Text(conversation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "管理当前对话，不影响同项目中的其他会话。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            ActionRow(Icons.Rounded.Settings, "对话设置", "标题、系统提示词和项目归属") { onDismiss(); onSettings() }
            onTemplateCapabilities?.let { open ->
                ActionRow(Icons.Rounded.Folder, "模板能力", "查看 AI 可调用的查询与生成能力") { onDismiss(); open() }
            }
            ActionRow(Icons.Rounded.Folder, "移动到项目", "加入其他项目，或移出当前项目") { onDismiss(); onMove() }
            ActionRow(Icons.Rounded.QueryStats, "统计与调试", "Token 用量、生成速度与请求日志") { onDismiss(); onStatistics() }
            ActionRow(Icons.Rounded.Download, "导出对话", "保存为 Markdown 或结构化 JSON") { onDismiss(); onExport() }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            ActionRow(Icons.Rounded.DeleteOutline, "删除对话", "移入回收站，可在设置中恢复", destructive = true) {
                onDismiss(); onDelete()
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    summary: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp).size(21.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
