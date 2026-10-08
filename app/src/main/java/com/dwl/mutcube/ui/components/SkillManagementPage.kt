package com.dwl.mutcube.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.storage.InstalledSkill
import com.dwl.mutcube.storage.SkillAuditEvent
import com.dwl.mutcube.storage.SkillInvocation

@Composable
fun SkillManagementPage(
    installed: List<InstalledSkill>,
    audit: List<SkillAuditEvent>,
    spaces: List<Space>,
    onBack: () -> Unit,
    onImport: (Uri, (String?) -> Unit) -> Unit,
    onExport: (String, Uri, (String?) -> Unit) -> Unit,
    onCreate: (String, String, String, (String?) -> Unit) -> Unit,
    onInvocation: (String, SkillInvocation) -> Unit,
    onProject: (String, String?) -> Unit,
    onUninstall: (String) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<InstalledSkill?>(null) }
    var exportTarget by remember { mutableStateOf<InstalledSkill?>(null) }
    var selectedSkillId by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var showAudit by remember { mutableStateOf(false) }
    val filteredSkills = installed.filter {
        search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) ||
            it.description.contains(search.trim(), ignoreCase = true) ||
            it.source.contains(search.trim(), ignoreCase = true) ||
            spaces.any { space -> space.id.value == it.projectId &&
                space.name.contains(search.trim(), ignoreCase = true) }
    }.sortedBy { it.name.lowercase() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it) { result -> error = result } }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = exportTarget
        if (uri != null && target != null) onExport(target.id, uri) { result -> error = result }
        exportTarget = null
    }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("Skill 管理", onBack)
        Text("按需加载的标准工作流程；启用不会授予数据或工具权限。",
            Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text("导入 Skill ZIP")
            }
            OutlinedButton(onClick = { createDialog = true }) { Text("创建 Skill") }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.error) }
        if (installed.isNotEmpty()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("搜索 Skill 名称或用途") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (installed.isEmpty()) item {
                Text("还没有 Skill。可导入符合 Agent Skills 标准的 ZIP，或创建一个本地 Skill。",
                    Modifier.padding(vertical = 50.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (installed.isNotEmpty() && filteredSkills.isEmpty()) item {
                Text("没有找到符合条件的 Skill。", Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filteredSkills, key = InstalledSkill::id) { skill ->
                SkillListItem(skill, spaces, onClick = { selectedSkillId = skill.id })
            }
            if (audit.isNotEmpty()) {
                item { TextButton(onClick = { showAudit = !showAudit }) {
                    Text(if (showAudit) "收起最近调用" else "查看最近调用")
                } }
                if (showAudit) items(audit.takeLast(10).reversed()) { event ->
                    Text("${installed.firstOrNull { it.id == event.skillId }?.name ?: event.skillId.take(8)} · " +
                        "${event.action} · ${if (event.success) "成功" else "失败"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    selectedSkillId?.let { id ->
        installed.firstOrNull { it.id == id }?.let { skill ->
            SkillDetailSheet(
                skill = skill,
                spaces = spaces,
                onDismiss = { selectedSkillId = null },
                onInvocation = onInvocation,
                onProject = onProject,
                onExport = { exportTarget = skill; exporter.launch("${skill.name}.zip") },
                onDelete = { deleteTarget = skill },
            )
        }
    }
    if (createDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { createDialog = false }, title = { Text("创建标准 Skill") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("英文标识，如 workout-review") })
                OutlinedTextField(description, { description = it }, label = { Text("何时使用及用途") })
                OutlinedTextField(instructions, { instructions = it }, label = { Text("SKILL.md 正文") }, minLines = 5)
            } },
            confirmButton = { TextButton(enabled = name.isNotBlank() && description.isNotBlank() && instructions.isNotBlank(),
                onClick = { onCreate(name, description, instructions) { result -> error = result }; createDialog = false }) { Text("创建") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("取消") } })
    }
    deleteTarget?.let { skill ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("卸载 ${skill.name}？") },
            text = { Text("Skill 包会从本机移除；已有聊天和模板数据不会删除。") },
            confirmButton = { TextButton(onClick = {
                onUninstall(skill.id)
                selectedSkillId = null
                deleteTarget = null
            }) { Text("卸载") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
}

@Composable
private fun SkillListItem(skill: InstalledSkill, spaces: List<Space>, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(skill.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(skill.invocation.shortName(), modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(skill.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val scope = spaces.firstOrNull { it.id.value == skill.projectId }?.name
                ?: if (skill.projectId == null) "所有聊天与项目" else "项目已不存在"
            Icon(Icons.Rounded.ChevronRight, contentDescription = "查看 ${skill.name} 设置，$scope")
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SkillDetailSheet(
    skill: InstalledSkill, spaces: List<Space>, onInvocation: (String, SkillInvocation) -> Unit,
    onProject: (String, String?) -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(skill.name, style = MaterialTheme.typography.titleMedium)
            Text(skill.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("来源：${skill.source} · 版本 ${skill.hash.take(8)}" +
                (skill.license?.let { " · 许可 $it" } ?: ""), style = MaterialTheme.typography.labelSmall)
            if (skill.hasScripts) Text("包含脚本；当前 Android 环境不会执行", color = MaterialTheme.colorScheme.error)
            skill.compatibility?.let { Text("环境要求：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("使用方式", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { modeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(skill.invocation.displayName())
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
                Text(skill.invocation.explanation(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenu(modeMenu, { modeMenu = false }, modifier = Modifier.widthIn(min = 280.dp)) {
                    SkillInvocation.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Column {
                                Text(mode.displayName())
                                Text(mode.explanation(), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } },
                            onClick = { onInvocation(skill.id, mode); modeMenu = false },
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("可用范围", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { projectMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(spaces.firstOrNull { it.id.value == skill.projectId }?.let { "仅项目：${it.name}" }
                        ?: if (skill.projectId == null) "所有聊天与项目" else "项目已不存在")
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
                Text("限制此 Skill 在哪些聊天中可用；不会授予项目数据权限。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DropdownMenu(projectMenu, { projectMenu = false }, modifier = Modifier.widthIn(min = 280.dp)) {
                    DropdownMenuItem(text = { Column {
                        Text("所有聊天与项目")
                        Text("在普通聊天和各项目聊天中均可选择", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }, onClick = { onProject(skill.id, null); projectMenu = false })
                    spaces.forEach { space -> DropdownMenuItem(text = { Text("仅项目：${space.name}") },
                        onClick = { onProject(skill.id, space.id.value); projectMenu = false }) }
                }
            }
            Row {
                TextButton(onClick = onExport) { Text("导出") }
                TextButton(onClick = onDelete) { Text("卸载") }
            }
        }
    }
}

private fun SkillInvocation.shortName(): String = when (this) {
    SkillInvocation.AUTO -> "自动与手动"
    SkillInvocation.MANUAL -> "仅手动"
    SkillInvocation.OFF -> "已停用"
}

private fun SkillInvocation.displayName(): String = when (this) {
    SkillInvocation.AUTO -> "自动匹配，也可手动选择"
    SkillInvocation.MANUAL -> "仅手动选择"
    SkillInvocation.OFF -> "停用"
}

private fun SkillInvocation.explanation(): String = when (this) {
    SkillInvocation.AUTO -> "AI 可按任务匹配；你也可以在输入框中主动选择。"
    SkillInvocation.MANUAL -> "只有你主动选择时，AI 才会读取此 Skill。"
    SkillInvocation.OFF -> "不提供给 AI，也不会出现在聊天选择器中。"
}
