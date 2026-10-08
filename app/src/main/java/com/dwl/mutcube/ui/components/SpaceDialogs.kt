package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.SpaceModelOverrides
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ProviderConfiguration

@Composable
fun RenameConversationDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("对话标题") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = title.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun RenameSpaceDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名项目") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("项目名称") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun EditMessageDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("消息内容") },
                minLines = 3,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = text.isNotBlank()) { Text("保存并发送") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun CreateSpaceDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建项目") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("项目名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun MoveConversationDialog(
    conversationTitle: String,
    spaces: List<Space>,
    selectedSpaceId: SpaceId?,
    onDismiss: () -> Unit,
    onMove: (SpaceId?) -> Unit,
) {
    var selectedSpace by remember(conversationTitle, selectedSpaceId) { mutableStateOf(selectedSpaceId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到项目") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("“$conversationTitle”")
                Text(
                    "移动后，对话将继承目标项目的模型、记忆和上下文设置。",
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                SpaceOption(
                    name = "不加入项目",
                    selected = selectedSpace == null,
                    onClick = { selectedSpace = null },
                )
                spaces.forEach { space ->
                    SpaceOption(
                        name = space.name,
                        selected = selectedSpace == space.id,
                        onClick = { selectedSpace = space.id },
                    )
                }
                if (spaces.isEmpty()) {
                    Text("还没有项目，请先在侧边栏创建项目。", modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selectedSpace) }) { Text("移动") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConversationSpaceDialog(
    spaces: List<Space>,
    selectedSpaceId: SpaceId?,
    systemPrompt: String?,
    onDismiss: () -> Unit,
    onSave: (SpaceId?, String?) -> Unit,
) {
    var selectedSpace by remember(selectedSpaceId) { mutableStateOf(selectedSpaceId) }
    var prompt by remember(systemPrompt) { mutableStateOf(systemPrompt.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对话设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("所属项目")
                SpaceOption(
                    name = "不加入项目",
                    selected = selectedSpace == null,
                    onClick = { selectedSpace = null },
                )
                spaces.forEach { space ->
                    SpaceOption(
                        name = space.name,
                        selected = selectedSpace == space.id,
                        onClick = { selectedSpace = space.id },
                    )
                }
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it.take(8_000) },
                    label = { Text("会话级系统提示词") },
                    supportingText = { Text("仅作用于当前对话，优先于项目和全局设置") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedSpace, prompt.trim().takeIf(String::isNotEmpty)) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun SpaceSettingsDialog(
    space: Space,
    globalSettings: ModelSettings,
    providers: ProviderConfiguration,
    onDismiss: () -> Unit,
    onSave: (String?, SpaceModelOverrides) -> Unit,
) {
    var profileId by remember(space) { mutableStateOf(space.modelProfileId) }
    var modelId by remember(space) { mutableStateOf(space.modelIdOverride.orEmpty()) }
    var systemPrompt by remember(space) { mutableStateOf(space.systemPromptOverride.orEmpty()) }
    var maxContext by remember(space) { mutableStateOf(space.maxContextMessagesOverride?.toString().orEmpty()) }
    var temperature by remember(space) { mutableStateOf(space.temperatureOverride?.toString().orEmpty()) }
    var topP by remember(space) { mutableStateOf(space.topPOverride?.toString().orEmpty()) }
    var maxOutputTokens by remember(space) { mutableStateOf(space.maxOutputTokensOverride?.toString().orEmpty()) }
    var imageMode by remember(space) {
        mutableStateOf(
            when (space.imageInputEnabledOverride) {
                true -> 1
                false -> 2
                null -> 0
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${space.name} · 模型设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("留空表示继承全局设置", modifier = Modifier.padding(bottom = 8.dp))
                Text("Provider")
                listOf(null to "继承全局（${providers.activeProfile.name}）")
                    .plus(providers.profiles.map { it.id to it.name })
                    .forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { profileId = id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = profileId == id, onClick = { profileId = id })
                            Text(label)
                        }
                    }
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID（留空使用 Provider 默认值）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxContext,
                    onValueChange = { maxContext = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("上下文消息数（全局：${globalSettings.maxContextMessages}）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("项目系统提示词") },
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it.filter { char -> char.isDigit() || char == '.' }.take(4) },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("Temperature（全局：${globalSettings.temperature ?: "服务默认"}）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = topP,
                    onValueChange = { topP = it.filter { char -> char.isDigit() || char == '.' }.take(4) },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("Top P（全局：${globalSettings.topP ?: "服务默认"}）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxOutputTokens,
                    onValueChange = { maxOutputTokens = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.padding(top = 8.dp),
                    label = { Text("最大输出 Token（全局：${globalSettings.maxOutputTokens ?: "服务默认"}）") },
                    singleLine = true,
                )
                Text("图片输入", modifier = Modifier.padding(top = 10.dp))
                listOf(0 to "继承全局", 1 to "允许", 2 to "禁止").forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { imageMode = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = imageMode == value, onClick = { imageMode = value })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profileId,
                        SpaceModelOverrides(
                            modelId = modelId.trim().takeIf(String::isNotEmpty),
                            systemPrompt = systemPrompt.trim().takeIf(String::isNotEmpty),
                            maxContextMessages = maxContext.toIntOrNull(),
                            imageInputEnabled = when (imageMode) { 1 -> true; 2 -> false; else -> null },
                            temperature = temperature.toDoubleOrNull(),
                            topP = topP.toDoubleOrNull(),
                            maxOutputTokens = maxOutputTokens.toIntOrNull(),
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SpaceOption(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name, modifier = Modifier.padding(start = 8.dp))
    }
}
