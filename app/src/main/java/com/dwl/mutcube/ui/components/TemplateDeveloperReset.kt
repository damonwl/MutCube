package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.database.TemplateResetPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class TemplateResetTarget(val projectId: String, val templateId: String, val projectName: String, val templateName: String)

@Composable
fun TemplateDeveloperReset(
    enabled: Boolean,
    targets: List<TemplateResetTarget>,
    onPreview: suspend (TemplateResetTarget) -> TemplateResetPreview,
    onReset: suspend (TemplateResetTarget, String) -> TemplateResetPreview,
) {
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TemplateResetTarget?>(null) }
    var preview by remember { mutableStateOf<TemplateResetPreview?>(null) }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun load(target: TemplateResetTarget) {
        busy = true
        try {
            preview = onPreview(target)
            selected = target
            confirmation = ""
            choosing = false
            message = null
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            message = failure.message ?: "读取失败，请重试"
        } finally { busy = false }
    }
    OutlinedButton(onClick = { choosing = true; message = null }, enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("重置项目模板数据") }
    Text("仅用于重新测试。永久清除所选项目当前模板的数据，保留聊天、项目、模型配置、密钥、模板绑定与授权。建议先在数据管理中导出备份。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!enabled) Text("请先保存“启用开发者模式”设置。", style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
    if (choosing) AlertDialog(
        onDismissRequest = { if (!busy) choosing = false },
        title = { Text("选择需要重置的项目") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (targets.isEmpty()) Text("还没有启用模板的项目")
                targets.forEach { target ->
                    TextButton(enabled = !busy, onClick = { scope.launch { load(target) } }, modifier = Modifier.fillMaxWidth()) {
                        Text("${target.projectName} · ${target.templateName}")
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(enabled = !busy, onClick = { choosing = false }) { Text("取消") } },
    )
    selected?.let { target -> AlertDialog(
        onDismissRequest = { if (!busy) selected = null },
        title = { Text("永久重置模板数据") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("项目：${target.projectName}\n模板：${target.templateName}\n当前有 ${preview?.records ?: 0} 条数据、${preview?.runs ?: 0} 条执行记录。")
            Text("清除全部资料、草稿、历史记录、计划版本、摘要缓存及该模板的执行/审计记录。无法撤销，其他项目不受影响。执行时清除该范围的全部数据，包括预览后新增的数据。")
            OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, enabled = !busy,
                label = { Text("输入项目名称确认") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = enabled && !busy && confirmation.isNotBlank() && confirmation == target.projectName,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            onClick = { scope.launch {
                busy = true
                try {
                    val removed = onReset(target, confirmation)
                    selected = null
                    message = "已重置 ${target.projectName} 的 ${target.templateName}：清除 ${removed.records} 条数据、${removed.runs} 条执行记录。数据无法撤销；重新打开模板即可从首次使用开始。"
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    message = failure.message ?: "重置失败，未清除数据"
                } finally { busy = false }
            } }) { Text(if (busy) "重置中…" else "永久清除") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { selected = null; message = null }) { Text("取消") } },
    ) }
}
