package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.storage.RequestLogEntry
import com.dwl.mutcube.storage.RequestLogState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsStatsPage(
    messages: List<ChatMessage>,
    logs: RequestLogState,
    developerMode: Boolean,
    onBack: () -> Unit,
    onLoggingEnabled: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
) {
    val aiMessages = messages.filter { it.role == MessageRole.AI }
    val inputTokens = aiMessages.sumOf { it.inputTokens ?: 0 }
    val outputTokens = aiMessages.sumOf { it.outputTokens ?: 0 }
    val cachedTokens = aiMessages.sumOf { it.cachedInputTokens ?: 0 }
    val durationMs = aiMessages.sumOf { it.generationDurationMs ?: 0 }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("统计与调试", onBack)
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "stats") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("当前对话", style = MaterialTheme.typography.titleMedium)
                        StatRow("消息", "${messages.size} 条")
                        StatRow("输入 Token", inputTokens.toString())
                        StatRow("缓存 Token", cachedTokens.toString())
                        StatRow("输出 Token", outputTokens.toString())
                        StatRow("累计生成耗时", formatDuration(durationMs))
                        val speed = if (durationMs > 0) outputTokens * 1_000.0 / durationMs else 0.0
                        StatRow("平均生成速度", "%.1f token/s".format(speed))
                    }
                }
            }
            item(key = "switch") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (developerMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("请求调试日志", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "仅记录请求规模、端点、模型、Token、耗时和脱敏错误，不保存消息正文或 API Key。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(logs.enabled, onLoggingEnabled)
                            }
                            if (logs.entries.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(top = 12.dp))
                                TextButton(onClick = onClearLogs, modifier = Modifier.align(Alignment.End)) { Text("清空日志") }
                            }
                        } else {
                            Text("详细诊断已隐藏", style = MaterialTheme.typography.titleMedium)
                            Text("在“开发者选项”中启用开发者模式后，可查看脱敏请求日志和原始错误。")
                        }
                    }
                }
            }
            if (developerMode) items(logs.entries, key = RequestLogEntry::id) { entry -> RequestLogCard(entry) }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun RequestLogCard(entry: RequestLogEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(entry.modelId, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (entry.error == null) "成功" else "失败",
                    color = if (entry.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "${formatTime(entry.startedAt)} · ${entry.durationMs} ms · ${entry.protocol}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.providerUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                "${entry.messageCount} 条消息 · ${entry.inputCharacters} 字符 · ${entry.toolCount} 个工具",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Token ${entry.inputTokens ?: "—"} → ${entry.outputTokens ?: "—"} · 输出 ${entry.responseCharacters} 字符",
                style = MaterialTheme.typography.bodySmall,
            )
            entry.error?.let {
                Text("脱敏原始错误", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String = when {
    milliseconds < 1_000 -> "$milliseconds ms"
    milliseconds < 60_000 -> "%.1f 秒".format(milliseconds / 1_000.0)
    else -> "%.1f 分钟".format(milliseconds / 60_000.0)
}

private fun formatTime(timestamp: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
