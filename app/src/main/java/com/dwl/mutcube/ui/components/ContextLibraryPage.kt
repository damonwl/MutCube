package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.dwl.mutcube.storage.ContextLibraryState
import com.dwl.mutcube.storage.ContextMode
import com.dwl.mutcube.storage.KnowledgeEntry
import com.dwl.mutcube.storage.QuickPrompt
import java.util.UUID

private enum class LibraryTab(val label: String) {
    PROMPTS("快捷提示词"), MODES("对话模式"), KNOWLEDGE("知识条目"),
}

@Composable
fun ContextLibraryPage(
    state: ContextLibraryState,
    onBack: () -> Unit,
    onSavePrompt: (QuickPrompt) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onSaveMode: (ContextMode) -> Unit,
    onDeleteMode: (String) -> Unit,
    onSaveKnowledge: (KnowledgeEntry) -> Unit,
    onDeleteKnowledge: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.PROMPTS) }
    var promptEditor by remember { mutableStateOf<QuickPrompt?>(null) }
    var modeEditor by remember { mutableStateOf<ContextMode?>(null) }
    var knowledgeEditor by remember { mutableStateOf<KnowledgeEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("提示词与上下文", onBack)
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LibraryTab.entries) { item ->
                FilterChip(tab == item, { tab = item }, { Text(item.label) })
            }
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (tab) {
                LibraryTab.PROMPTS -> {
                    if (state.quickPrompts.isEmpty()) item { EmptyLibrary("还没有快捷提示词", "常用问题可一键填入输入框。") }
                    items(state.quickPrompts, key = QuickPrompt::id) { prompt ->
                        LibraryCard(prompt.title, prompt.content, { promptEditor = prompt }) { onDeletePrompt(prompt.id) }
                    }
                }
                LibraryTab.MODES -> {
                    if (state.modes.isEmpty()) item { EmptyLibrary("还没有对话模式", "一次只启用一个，用于切换 AI 的整体工作方式。") }
                    items(state.modes, key = ContextMode::id) { mode ->
                        ToggleLibraryCard(mode.name, mode.description, mode.active,
                            { onSaveMode(mode.copy(active = it)) }, { modeEditor = mode }) { onDeleteMode(mode.id) }
                    }
                }
                LibraryTab.KNOWLEDGE -> {
                    if (state.knowledgeEntries.isEmpty()) item { EmptyLibrary("还没有知识条目", "按关键词命中后注入；关键词留空表示始终注入。") }
                    items(state.knowledgeEntries, key = KnowledgeEntry::id) { entry ->
                        val summary = entry.keywords.takeIf { it.isNotEmpty() }?.joinToString("、")?.let { "关键词：$it" } ?: "始终注入"
                        ToggleLibraryCard(entry.title, summary, entry.enabled,
                            { onSaveKnowledge(entry.copy(enabled = it)) }, { knowledgeEditor = entry }) { onDeleteKnowledge(entry.id) }
                    }
                }
            }
        }
        Button(
            onClick = {
                when (tab) {
                    LibraryTab.PROMPTS -> promptEditor = QuickPrompt(UUID.randomUUID().toString(), "", "")
                    LibraryTab.MODES -> modeEditor = ContextMode(UUID.randomUUID().toString(), "", "", "")
                    LibraryTab.KNOWLEDGE -> knowledgeEditor = KnowledgeEntry(UUID.randomUUID().toString(), "", "")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        ) {
            Icon(Icons.Rounded.Add, null)
            Text("添加${tab.label}", Modifier.padding(start = 8.dp))
        }
    }
    promptEditor?.let { PromptEditor(it, { promptEditor = null }) { value -> onSavePrompt(value); promptEditor = null } }
    modeEditor?.let { ModeEditor(it, { modeEditor = null }) { value -> onSaveMode(value); modeEditor = null } }
    knowledgeEditor?.let { KnowledgeEditor(it, { knowledgeEditor = null }) { value -> onSaveKnowledge(value); knowledgeEditor = null } }
}

@Composable
private fun EmptyLibrary(title: String, summary: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LibraryCard(title: String, content: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(content, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
            EditorActions(onEdit, onDelete)
        }
    }
}

@Composable
private fun ToggleLibraryCard(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary.isNotBlank()) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked, onCheckedChange)
            }
            EditorActions(onEdit, onDelete)
        }
    }
}

@Composable
private fun EditorActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    Row {
        TextButton(onClick = onEdit) { Text("编辑") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除") }
    }
}

@Composable
private fun PromptEditor(initial: QuickPrompt, onDismiss: () -> Unit, onSave: (QuickPrompt) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var content by remember(initial) { mutableStateOf(initial.content) }
    EditorDialog("快捷提示词", onDismiss, title.isNotBlank() && content.isNotBlank(), { onSave(initial.copy(title = title.trim(), content = content.trim())) }) {
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(content, { content = it }, label = { Text("内容") }, minLines = 4, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun ModeEditor(initial: ContextMode, onDismiss: () -> Unit, onSave: (ContextMode) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var instructions by remember(initial) { mutableStateOf(initial.instructions) }
    EditorDialog("对话模式", onDismiss, name.isNotBlank() && instructions.isNotBlank(), { onSave(initial.copy(name = name.trim(), description = description.trim(), instructions = instructions.trim())) }) {
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("说明") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        OutlinedTextField(instructions, { instructions = it }, label = { Text("模式指令") }, minLines = 5, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun KnowledgeEditor(initial: KnowledgeEntry, onDismiss: () -> Unit, onSave: (KnowledgeEntry) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var keywords by remember(initial) { mutableStateOf(initial.keywords.joinToString("，")) }
    var content by remember(initial) { mutableStateOf(initial.content) }
    EditorDialog("知识条目", onDismiss, title.isNotBlank() && content.isNotBlank(), {
        onSave(initial.copy(title = title.trim(), content = content.trim(), keywords = keywords.split(',', '，', '\n').map(String::trim).filter(String::isNotEmpty).distinct()))
    }) {
        OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(keywords, { keywords = it }, label = { Text("触发关键词（逗号分隔，可留空）") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        OutlinedTextField(content, { content = it }, label = { Text("知识内容") }, minLines = 6, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

@Composable
private fun EditorDialog(title: String, onDismiss: () -> Unit, canSave: Boolean, onSave: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
