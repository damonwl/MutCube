package com.dwl.mutcube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.ToolCallStatus
import com.dwl.mutcube.storage.TemplateChatCapability
import com.dwl.mutcube.storage.TemplateDataToolService
import org.json.JSONObject

/** Only a real paired ToolResult, tied to the declared hashed tool name, can offer navigation. */
internal fun templateResult(call: MessageContentPart.ToolCall, parts: List<MessageContentPart>): JSONObject? {
    val result = parts.filterIsInstance<MessageContentPart.ToolResult>().lastOrNull { it.callId == call.callId }
        ?.takeUnless { it.isError } ?: return null
    return runCatching { JSONObject(result.output).getJSONObject("presentation") }.getOrNull()?.takeIf {
        TemplateDataToolService.toolName(it.optString("templateId"), it.optString("actionId")) == call.toolName
    }
}

@Composable
internal fun TemplateCapabilitiesPanel(capabilities: List<TemplateChatCapability>, onDismiss: () -> Unit,
    onChoose: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("模板能力") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("选择能力，将示例请求放入输入框；补充要求后发送。查询不会修改数据，生成结果需回到模板确认。")
            if (capabilities.isEmpty()) Text("当前没有可调用的模板能力。请确认项目模板授权、开启工具调用，并选择支持工具调用的模型。")
            capabilities.forEach { capability ->
                Surface(onClick = { onChoose(capability.example) }, shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(capability.title, style = MaterialTheme.typography.titleSmall)
                        Text(capability.templateName, style = MaterialTheme.typography.labelSmall)
                        Text(capability.example, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
internal fun TemplateOperationCards(parts: List<MessageContentPart>, capabilities: List<TemplateChatCapability>,
    onOpen: ((JSONObject) -> Unit)?) {
    val calls = parts.filterIsInstance<MessageContentPart.ToolCall>().filter { it.toolName.startsWith("template_action_") }
    calls.forEach { call ->
        val result = templateResult(call, parts)
        val capability = capabilities.firstOrNull { it.toolName == call.toolName }
        val title = result?.optString("title")?.takeIf { it.isNotBlank() } ?: capability?.title ?: "模板操作"
        val running = call.status == ToolCallStatus.RUNNING || call.status == ToolCallStatus.PENDING
        var expanded by remember(call.callId) { mutableStateOf(false) }
        val failed = call.status == ToolCallStatus.FAILED || call.status == ToolCallStatus.DENIED
        Surface(shape = MaterialTheme.shapes.medium, color = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text((if (running) "正在执行 · " else if (failed) "执行失败 · " else "已完成 · ") + title,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(if (expanded) "收起" else "详情", style = MaterialTheme.typography.labelSmall)
                }
                val pending = result?.optBoolean("pendingConfirmation") == true
                if (pending) Text("候选已生成，尚未启用。", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp))
                if (expanded) {
                    val output = parts.filterIsInstance<MessageContentPart.ToolResult>().lastOrNull { it.callId == call.callId }
                    val detail = if (failed) output?.output ?: "操作未完成，可能已停止。请查看运行记录后重试。"
                        else if (running) "正在调用模板能力，请稍候。"
                        else runCatching {
                            val data = JSONObject(output!!.output).getJSONObject("data")
                            when {
                                data.has("records") -> "已读取 ${data.getJSONArray("records").length()} 条记录（本次分页）。"
                                data.has("name") -> data.optString("name") + "\n" + data.optString("description")
                                data.isNull("data") -> "当前没有已启用记录。"
                                else -> "已读取模板数据；可进入模板查看详情。"
                            }
                        }.getOrDefault("执行结果已保存在消息和模板运行记录中。")
                    Text(detail.take(1200), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (result != null && !running && !failed && onOpen != null) {
                    TextButton(onClick = { onOpen(result) }) { Text(if (pending) "查看并启用" else "打开模板查看") }
                }
            }
        }
    }
}
