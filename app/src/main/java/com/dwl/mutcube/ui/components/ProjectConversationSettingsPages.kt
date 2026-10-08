package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.SpaceModelOverrides

@Composable
fun ProjectSettingsPage(
    space: Space,
    globalSettings: ModelSettings,
    providers: ProviderConfiguration,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
    onSave: (String, String?, SpaceModelOverrides) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(space.id) { mutableStateOf(space.name) }
    var profileId by remember(space.id) { mutableStateOf(space.modelProfileId) }
    var modelId by remember(space.id) { mutableStateOf(space.modelIdOverride.orEmpty()) }
    val availableProviders = providers.profiles.filter {
        it.enabled && it.id in configuredProviderIds && it.modelId.isNotBlank()
    }
    val selectionAvailable = profileId == null || availableProviders.any { it.id == profileId }
    var systemPrompt by remember(space.id) { mutableStateOf(space.systemPromptOverride.orEmpty()) }
    var maxContext by remember(space.id) { mutableStateOf(space.maxContextMessagesOverride?.toString().orEmpty()) }
    var temperature by remember(space.id) { mutableStateOf(space.temperatureOverride?.toString().orEmpty()) }
    var topP by remember(space.id) { mutableStateOf(space.topPOverride?.toString().orEmpty()) }
    var maxOutputTokens by remember(space.id) { mutableStateOf(space.maxOutputTokensOverride?.toString().orEmpty()) }
    var imageMode by remember(space.id) {
        mutableStateOf(when (space.imageInputEnabledOverride) { true -> 1; false -> 2; null -> 0 })
    }

    Column(modifier.fillMaxSize()) {
        SettingsPageHeader("项目设置", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "这些设置只影响当前项目，新建会话会自动继承。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                SettingsCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("项目名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it.take(8_000) },
                        label = { Text("项目系统提示词") },
                        supportingText = { Text("留空时继承全局系统提示词") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            item {
                SectionTitle("模型与 Provider")
                SettingsCard {
                    ProviderOption(
                        title = "继承全局",
                        summary = "${providers.activeProfile.name} · ${providers.activeProfile.modelId}",
                        selected = profileId == null,
                        onClick = { profileId = null },
                    )
                    if (!selectionAvailable) {
                        Text("当前绑定的 Provider 已停用、删除或未配置，请重新选择。", color = MaterialTheme.colorScheme.error)
                    }
                    availableProviders.forEach { profile ->
                        ProviderOption(
                            title = profile.name,
                            summary = profile.modelId,
                            selected = profileId == profile.id,
                            onClick = { profileId = profile.id },
                        )
                    }
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        supportingText = { Text("留空时使用所选 Provider 的默认模型") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            item {
                SectionTitle("生成参数")
                SettingsCard {
                    NumericField(maxContext, { maxContext = it.filter(Char::isDigit).take(3) }, "上下文消息数", globalSettings.maxContextMessages.toString())
                    NumericField(temperature, { temperature = decimalInput(it) }, "Temperature", globalSettings.temperature?.toString() ?: "服务默认")
                    NumericField(topP, { topP = decimalInput(it) }, "Top P", globalSettings.topP?.toString() ?: "服务默认")
                    NumericField(maxOutputTokens, { maxOutputTokens = it.filter(Char::isDigit).take(6) }, "最大输出 Token", globalSettings.maxOutputTokens?.toString() ?: "服务默认")
                    Text("图片输入", fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "继承", 1 to "允许", 2 to "禁止").forEach { (mode, label) ->
                            Surface(
                                onClick = { imageMode = mode },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (imageMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            ) {
                                Text(label, modifier = Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                onSave(
                    name.trim(),
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
            enabled = name.isNotBlank() && selectionAvailable,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) { Text("保存项目设置") }
    }
}

@Composable
fun ConversationSettingsPage(
    conversation: Conversation,
    spaces: List<Space>,
    onBack: () -> Unit,
    onSave: (String, SpaceId?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember(conversation.id) { mutableStateOf(conversation.title) }
    var selectedSpaceId by remember(conversation.id) { mutableStateOf(conversation.spaceId) }
    var prompt by remember(conversation.id) { mutableStateOf(conversation.systemPrompt.orEmpty()) }
    val selectedSpace = spaces.firstOrNull { it.id == selectedSpaceId }

    Column(modifier.fillMaxSize()) {
        SettingsPageHeader("对话设置", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "只影响当前对话；未覆盖的模型和上下文设置继续继承所属项目或全局设置。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                SettingsCard {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("对话标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(8_000) },
                        label = { Text("对话级系统提示词") },
                        supportingText = { Text("优先于项目和全局系统提示词") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            item {
                SectionTitle("所属项目")
                SettingsCard {
                    ProviderOption("不加入项目", "使用全局模型和设置", selectedSpaceId == null) { selectedSpaceId = null }
                    spaces.forEach { space ->
                        ProviderOption(space.name, "继承该项目的模型、记忆和上下文", selectedSpaceId == space.id) {
                            selectedSpaceId = space.id
                        }
                    }
                }
            }
            item {
                SectionTitle("当前继承关系")
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(selectedSpace?.name ?: "全局设置", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (selectedSpace == null) "Provider、模型和工具策略由全局设置提供" else "Provider、模型和工具策略优先由该项目提供",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onSave(title.trim(), selectedSpaceId, prompt.trim().takeIf(String::isNotEmpty)) },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) { Text("保存对话设置") }
    }
}

@Composable
fun NewProjectPage(
    providers: ProviderConfiguration,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
    onCreate: (String, String?, SpaceModelOverrides) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var profileId by remember { mutableStateOf<String?>(null) }
    var modelId by remember { mutableStateOf("") }
    val availableProviders = providers.profiles.filter {
        it.enabled && it.id in configuredProviderIds && it.modelId.isNotBlank()
    }

    Column(modifier.fillMaxSize()) {
        SettingsPageHeader("新建项目", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "项目用于组织会话，并提供可选的模型、提示词与记忆上下文。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                SettingsCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("项目名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it.take(8_000) },
                        label = { Text("系统提示词（可选）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            item {
                SectionTitle("模型与 Provider")
                SettingsCard {
                    ProviderOption(
                        "继承全局",
                        "${providers.activeProfile.name} · ${providers.activeProfile.modelId}",
                        profileId == null,
                    ) { profileId = null }
                    availableProviders.forEach { profile ->
                        ProviderOption(profile.name, profile.modelId, profileId == profile.id) { profileId = profile.id }
                    }
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID（可选）") },
                        supportingText = { Text("留空时使用所选 Provider 的默认模型") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("项目记忆", fontWeight = FontWeight.SemiBold)
                        Text(
                            "项目会话可读取全局记忆和本项目记忆；删除项目不会删除用户拥有的记忆。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
        Button(
            onClick = {
                onCreate(
                    name.trim(),
                    profileId,
                    SpaceModelOverrides(
                        modelId = modelId.trim().takeIf(String::isNotEmpty),
                        systemPrompt = prompt.trim().takeIf(String::isNotEmpty),
                    ),
                )
            },
            enabled = name.isNotBlank() && (profileId == null || availableProviders.any { it.id == profileId }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        ) { Text("创建项目") }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
    )
}

@Composable
private fun ProviderOption(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NumericField(value: String, onChange: (String) -> Unit, label: String, inherited: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text("留空继承：$inherited") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

private fun decimalInput(value: String): String = value.filter { it.isDigit() || it == '.' }.take(5)
