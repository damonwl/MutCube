package com.dwl.mutcube.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.ai.*
import com.dwl.mutcube.R
import java.net.URI
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private sealed interface ModelPage {
    data object List : ModelPage
    data object Generation : ModelPage
    data class Edit(val profile: ProviderProfile?, val preset: ProviderPreset? = null) : ModelPage
}

@Composable
fun ModelSettingsPage(
    effectiveProviderId: String?, configuredProviderIds: Set<String>, settings: ModelSettings,
    providers: ProviderConfiguration, inspectionLoading: Boolean, inspectionMessage: String?,
    discoveredModels: List<String>, onBack: () -> Unit,
    onSaveProvider: (String?, String, String, String, String, ProviderProtocol, List<ProviderModel>, AsrProtocol, TtsProtocol, Boolean) -> Unit,
    onInspectProvider: (String?, String, String, String, String, ProviderProtocol) -> Unit,
    onSelectProvider: (String) -> Unit,
    onProviderEnabledChange: (String, Boolean) -> Unit,
    onMoveProvider: (String, Int) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onDeleteCredential: (String) -> Unit, onSaveSettings: (ModelSettings) -> Unit,
    onImport: () -> Unit, onExport: () -> Unit, onShare: () -> Unit,
) {
    var page: ModelPage by remember { mutableStateOf(ModelPage.List) }
    var showPresets by remember { mutableStateOf(false) }
    when (val current = page) {
        ModelPage.List -> ProviderList(providers, effectiveProviderId, configuredProviderIds, onBack,
            { page = ModelPage.Edit(it) }, { showPresets = true }, { page = ModelPage.Generation },
            onProviderEnabledChange, onMoveProvider, onImport, onExport, onShare)
        ModelPage.Generation -> GenerationSettings(settings, { page = ModelPage.List }, onSaveSettings)
        is ModelPage.Edit -> ProviderEditor(current.profile, current.preset, configuredProviderIds, providers,
            inspectionLoading, inspectionMessage, discoveredModels, { page = ModelPage.List }, onSaveProvider,
            onInspectProvider, onSelectProvider, onDeleteProvider, onDeleteCredential)
    }
    if (showPresets) ProviderPresetDialog({ showPresets = false }) { preset ->
        showPresets = false
        page = ModelPage.Edit(null, preset)
    }
}

@Composable
private fun ProviderList(
    providers: ProviderConfiguration, effectiveProviderId: String?, configuredIds: Set<String>,
    onBack: () -> Unit, onEdit: (ProviderProfile) -> Unit, onAdd: () -> Unit, onGeneration: () -> Unit,
    onEnabledChange: (String, Boolean) -> Unit, onMove: (String, Int) -> Unit,
    onImport: () -> Unit, onExport: () -> Unit, onShare: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val order = remember { mutableStateListOf<ProviderProfile>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // The first lazy item is the search/header block. Search mode has no drag handle.
        val source = from.index - 1
        val destination = to.index - 1
        if (search.isBlank() && source in order.indices && destination in order.indices) {
            order.add(destination, order.removeAt(source))
        }
    }
    LaunchedEffect(providers.profiles) {
        if (draggingId == null) {
            order.clear()
            order.addAll(providers.profiles)
        }
    }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("模型服务", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState) {
            item(key = "provider-header") {
                Column {
                    Text("Provider", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        label = { Text("搜索厂商") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) })
                    Text("厂商未配置时不预置模型。填写 Key、获取模型后再启用；其他预置厂商默认停用。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    if (search.isBlank()) Text("长按右侧三横杠，拖动调整顺序。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    if (search.isNotBlank()) Text("清除搜索后可拖动排序",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            items(order.filter { it.name.contains(search, ignoreCase = true) }, key = { it.id }) { profile ->
                ReorderableItem(reorderState, key = profile.id) { isDragging ->
                    val activeModel = profile.models.firstOrNull { it.id == profile.modelId }
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "Provider drag elevation")
                    Surface(onClick = { onEdit(profile) }, shape = MaterialTheme.shapes.large,
                        color = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = elevation,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProviderLogo(profile.baseUrl, profile.name)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(profile.name)
                                Text(listOfNotNull(if (profile.models.isEmpty()) "尚未获取模型" else "${profile.models.size} 个模型", activeModel?.displayName,
                                    if (profile.id in configuredIds) "已配置" else "未配置",
                                    if (profile.id == effectiveProviderId) "当前使用" else null).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val dragModifier = if (search.isBlank()) Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    dragStartIndex = order.indexOfFirst { it.id == profile.id }
                                    draggingId = profile.id
                                },
                                onDragStopped = {
                                    val endIndex = order.indexOfFirst { it.id == profile.id }
                                    if (dragStartIndex >= 0 && endIndex >= 0 && endIndex != dragStartIndex) {
                                        onMove(profile.id, endIndex - dragStartIndex)
                                    }
                                    draggingId = null
                                    dragStartIndex = -1
                                },
                            ) else Modifier
                            IconButton(onClick = {}, modifier = dragModifier, enabled = search.isBlank()) {
                                Icon(Icons.Rounded.Menu, contentDescription = "长按拖动调整 ${profile.name} 顺序")
                            }
                            Switch(profile.enabled, { enabled ->
                                if (enabled && (profile.modelId.isBlank() || profile.id !in configuredIds)) onEdit(profile)
                                else onEnabledChange(profile.id, enabled)
                            })
                        }
                    }
                }
            }
            item(key = "provider-footer") {
                Column {
                    Surface(onClick = onGeneration, shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null)
                            Text("生成设置", Modifier.weight(1f).padding(horizontal = 14.dp))
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("导入") }
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出") }
                        OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("分享") }
                    }
                    Text("配置文件不包含 API Key；导入后需分别配置密钥。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Rounded.Add, null); Text("添加 Provider", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProviderPresetDialog(onDismiss: () -> Unit, onSelect: (ProviderPreset) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择 Provider") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PROVIDER_PRESETS.forEach { preset ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(preset) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(preset.baseUrl, preset.name)
                    Column(Modifier.padding(start = 12.dp)) { Text(preset.name); Text(preset.protocol.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ProviderEditor(
    profile: ProviderProfile?, preset: ProviderPreset?, configuredIds: Set<String>, providers: ProviderConfiguration,
    inspectionLoading: Boolean, inspectionMessage: String?, discoveredModels: List<String>, onBack: () -> Unit,
    onSave: (String?, String, String, String, String, ProviderProtocol, List<ProviderModel>, AsrProtocol, TtsProtocol, Boolean) -> Unit,
    onInspect: (String?, String, String, String, String, ProviderProtocol) -> Unit,
    onSelect: (String) -> Unit, onDelete: (String) -> Unit, onDeleteCredential: (String) -> Unit,
) {
    var name by remember(profile, preset) { mutableStateOf(profile?.name ?: preset?.name.orEmpty()) }
    var baseUrl by remember(profile, preset) { mutableStateOf(profile?.baseUrl ?: preset?.baseUrl.orEmpty()) }
    var modelId by remember(profile, preset) { mutableStateOf(profile?.modelId ?: preset?.suggestedModels?.firstOrNull().orEmpty()) }
    var models by remember(profile, preset) {
        mutableStateOf(
            profile?.models ?: preset?.suggestedModels.orEmpty().map { ProviderModel(it, capabilities = preset?.modelMetadata?.get(it) ?: ModelCapabilities()) },
        )
    }
    var editingModel by remember { mutableStateOf<ProviderModel?>(null) }
    var apiKey by remember(profile, preset) { mutableStateOf("") }
    var newModelId by remember(profile, preset) { mutableStateOf("") }
    var modelQuery by remember(profile, preset) { mutableStateOf("") }
    var protocol by remember(profile, preset) { mutableStateOf(profile?.protocol ?: preset?.protocol ?: ProviderProtocol.OPENAI_CHAT_COMPLETIONS) }
    var asrProtocol by remember(profile, preset) { mutableStateOf(profile?.asrProtocol ?: preset?.asrProtocol ?: AsrProtocol.AUDIO_TRANSCRIPTIONS) }
    var ttsProtocol by remember(profile, preset) { mutableStateOf(profile?.ttsProtocol ?: preset?.ttsProtocol ?: TtsProtocol.AUDIO_SPEECH) }
    var ttsStreaming by remember(profile, preset) { mutableStateOf(profile?.ttsStreaming ?: preset?.ttsStreaming ?: false) }
    var requestedInspection by remember(profile, preset) { mutableStateOf(false) }
    val valid = name.isNotBlank() && baseUrl.startsWith("https://") && modelId.isNotBlank()
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader(if (profile == null) "添加 Provider" else profile.name, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("接口类型", style = MaterialTheme.typography.labelLarge)
            ProviderProtocol.entries.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable { protocol = item }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(protocol == item, { protocol = item }); Text(item.label())
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("名称") }, singleLine = true)
            Text("ASR 接口协议", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            AsrProtocol.entries.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(asrProtocol == item, { asrProtocol = item }); Text(item.label)
                }
            }
            Text("TTS 接口协议", style = MaterialTheme.typography.labelLarge)
            TtsProtocol.entries.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(ttsProtocol == item, { ttsProtocol = item }); Text(item.label) }
            }
            Text("聊天兼容不代表语音接口相同。MiMo 使用聊天式 ASR/TTS；模型可单独覆盖协议。", style = MaterialTheme.typography.bodySmall)
            SettingSwitch("TTS 实时 PCM16 流式输出（24 kHz 单声道）", ttsStreaming) { ttsStreaming = it }
            OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("API Base URL") }, singleLine = true)
            Text("模型", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 18.dp))
            OutlinedTextField(modelQuery, { modelQuery = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("搜索模型 ID 或别名") }, singleLine = true)
            if (models.size > 100) Text("共 ${models.size} 个模型，当前最多显示 100 项，可搜索定位。", style = MaterialTheme.typography.bodySmall)
            models.filter { it.id.contains(modelQuery, true) || it.displayName.contains(modelQuery, true) }
                .sortedWith(compareByDescending<ProviderModel> { it.favorite }.thenBy { it.displayName.lowercase() }).take(100).forEach { model ->
                Surface(
                    onClick = { modelId = model.id },
                    shape = MaterialTheme.shapes.medium,
                    color = if (model.id == modelId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model.id == modelId, { modelId = model.id })
                        ModelBadge(model, model.displayName)
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName)
                            if (model.alias.isNotBlank()) Text(model.id, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            models = models.map { if (it.id == model.id) it.copy(favorite = !it.favorite) else it }
                        }) {
                            Icon(if (model.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "收藏模型")
                        }
                        IconButton(onClick = { editingModel = model }) { Icon(Icons.Rounded.Edit, "编辑模型") }
                    }
                }
            }
            OutlinedTextField(
                value = newModelId,
                onValueChange = { newModelId = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("添加模型 ID") },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val id = newModelId.trim()
                            if (models.none { it.id == id }) models = models + ProviderModel(id)
                            modelId = id
                            newModelId = ""
                        },
                        enabled = newModelId.isNotBlank(),
                    ) { Icon(Icons.Rounded.Add, "添加模型") }
                },
            )
            OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text(if (profile?.id in configuredIds) "API Key（已配置，留空不变）" else "API Key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedButton(onClick = { requestedInspection = true; onInspect(profile?.id, name, baseUrl, modelId, apiKey, protocol) }, enabled = name.isNotBlank() && baseUrl.startsWith("https://") && !inspectionLoading, modifier = Modifier.padding(top = 12.dp)) {
                Text(if (inspectionLoading) "正在连接…" else "测试连接并读取模型")
            }
            if (requestedInspection) inspectionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            if (requestedInspection && !inspectionLoading && discoveredModels.isNotEmpty()) TextButton(onClick = {
                models = (models + discoveredModels.map { ProviderModel(it, capabilities = preset?.modelMetadata?.get(it) ?: ModelCapabilities()) }).distinctBy { it.id }
                if (modelId.isBlank()) modelId = models.first().id
            }) { Text("添加全部 ${discoveredModels.size} 个模型") }
            if (requestedInspection && discoveredModels.size > 100) Text("模型目录较多，可用上方搜索定位或添加全部。", style = MaterialTheme.typography.bodySmall)
            discoveredModels.filter { requestedInspection && !inspectionLoading && it.contains(modelQuery, true) && models.none { model -> model.id == it } }.take(100).forEach { id ->
                TextButton(onClick = { models = models + ProviderModel(id); modelId = id }) { Text("添加 $id") }
            }
            profile?.let { existing ->
                if (existing.id != providers.activeProfileId) TextButton(onClick = { onSelect(existing.id) }) { Text("设为全局默认") }
                if (existing.id in configuredIds) TextButton(onClick = { onDeleteCredential(existing.id) }) { Text("删除 API Key") }
                if (providers.profiles.size > 1) TextButton(onClick = { onDelete(existing.id); onBack() }) { Text("删除 Provider", color = MaterialTheme.colorScheme.error) }
            }
        }
        Button(onClick = { onSave(profile?.id, name, baseUrl, modelId, apiKey, protocol, models, asrProtocol, ttsProtocol, ttsStreaming); onBack() }, enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text(if (profile == null) "添加并启用" else "保存") }
    }
    editingModel?.let { model ->
        ModelMetadataDialog(
            model = model,
            onDismiss = { editingModel = null },
            onSave = { changed ->
                models = models.map { if (it.id == changed.id) changed else it }
                editingModel = null
            },
            onDelete = {
                if (models.size > 1) {
                    models = models.filterNot { it.id == model.id }
                    if (modelId == model.id) modelId = models.first().id
                }
                editingModel = null
            },
        )
    }
}

@Composable
private fun ModelMetadataDialog(
    model: ProviderModel,
    onDismiss: () -> Unit,
    onSave: (ProviderModel) -> Unit,
    onDelete: () -> Unit,
) {
    var alias by remember(model) { mutableStateOf(model.alias) }
    var icon by remember(model) { mutableStateOf(model.icon) }
    var contextWindow by remember(model) { mutableStateOf(model.contextWindow?.toString().orEmpty()) }
    var maxOutput by remember(model) { mutableStateOf(model.maxOutputTokens?.toString().orEmpty()) }
    var capabilities by remember(model) { mutableStateOf(model.capabilities) }
    var asrProtocol by remember(model) { mutableStateOf(model.asrProtocol) }
    var ttsProtocol by remember(model) { mutableStateOf(model.ttsProtocol) }
    var ttsStreaming by remember(model) { mutableStateOf(model.ttsStreaming) }
    var ttsDefaultVoice by remember(model) { mutableStateOf(model.ttsDefaultVoice) }
    var ttsVoices by remember(model) { mutableStateOf(model.ttsVoices.joinToString("、")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.id) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(), label = { Text("模型别名") })
                OutlinedTextField(icon, { icon = it.take(4) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("模型图标（Emoji 或短文本）") })
                OutlinedTextField(contextWindow, { contextWindow = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("上下文窗口") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(maxOutput, { maxOutput = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("最大输出 Token") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                CapabilitySelector("图片输入", capabilities.imageInput) { capabilities = capabilities.copy(imageInput = it) }
                CapabilitySelector("工具调用", capabilities.toolCalling) { capabilities = capabilities.copy(toolCalling = it) }
                CapabilitySelector("推理", capabilities.reasoning) { capabilities = capabilities.copy(reasoning = it) }
                CapabilitySelector("语音识别", capabilities.speechRecognition) { capabilities = capabilities.copy(speechRecognition = it) }
                CapabilitySelector("语音合成", capabilities.speechSynthesis) { capabilities = capabilities.copy(speechSynthesis = it) }
                Text("ASR 协议覆盖")
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(asrProtocol == null, { asrProtocol = null }); Text("跟随 Provider") }
                AsrProtocol.entries.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(asrProtocol == item, { asrProtocol = item }); Text(item.label) }
                }
                TextButton(onClick = onDelete) { Text("删除模型", color = MaterialTheme.colorScheme.error) }
                Text("TTS 协议覆盖")
                Text("TTS 流式能力")
                listOf(null to "跟随 Provider", true to "支持实时 PCM16", false to "不支持（分段朗读）").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(ttsStreaming == value, { ttsStreaming = value }); Text(label) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(ttsProtocol == null, { ttsProtocol = null }); Text("跟随 Provider") }
                TtsProtocol.entries.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(ttsProtocol == item, { ttsProtocol = item }); Text(item.label) }
                }
                OutlinedTextField(ttsDefaultVoice, { ttsDefaultVoice = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("TTS 默认音色 ID") }, singleLine = true)
                OutlinedTextField(ttsVoices, { ttsVoices = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("TTS 内置音色 ID（用逗号分隔）") }, placeholder = { Text("留空则使用已知模型预设或手动填写") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(model.copy(alias = alias, icon = icon, capabilities = capabilities, asrProtocol = asrProtocol, ttsProtocol = ttsProtocol, ttsStreaming = ttsStreaming,
                    ttsDefaultVoice = ttsDefaultVoice.trim(), ttsVoices = ttsVoices.split(',', '，', '、', '\n').map(String::trim).filter(String::isNotEmpty).distinct(),
                    contextWindow = contextWindow.toIntOrNull(), maxOutputTokens = maxOutput.toIntOrNull()))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProviderLogo(baseUrl: String, fallback: String) {
    val host = remember(baseUrl) { runCatching { URI(baseUrl).host?.lowercase().orEmpty() }.getOrDefault("") }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val logo = when (host) {
        "api.xiaomimimo.com" -> if (dark) R.drawable.provider_xiaomimimo_dark else R.drawable.provider_xiaomimimo_light
        "api.openai.com" -> if (dark) R.drawable.provider_openai_dark else R.drawable.provider_openai_light
        "generativelanguage.googleapis.com" -> if (dark) R.drawable.provider_gemini_color_dark else R.drawable.provider_gemini_color_light
        "api.anthropic.com" -> if (dark) R.drawable.provider_claude_color_dark else R.drawable.provider_claude_color_light
        "aihubmix.com" -> if (dark) R.drawable.provider_aihubmix_color_dark else R.drawable.provider_aihubmix_color_light
        "api.siliconflow.cn" -> if (dark) R.drawable.provider_siliconcloud_color_dark else R.drawable.provider_siliconcloud_color_light
        "api.deepseek.com" -> if (dark) R.drawable.provider_deepseek_color_dark else R.drawable.provider_deepseek_color_light
        "api.moonshot.cn" -> if (dark) R.drawable.provider_moonshot_dark else R.drawable.provider_moonshot_light
        "openrouter.ai" -> if (dark) R.drawable.provider_openrouter_color_dark else R.drawable.provider_openrouter_color_light
        "ai-gateway.vercel.sh" -> if (dark) R.drawable.provider_vercel_dark else R.drawable.provider_vercel_light
        "dashscope.aliyuncs.com" -> if (dark) R.drawable.provider_bailian_color_dark else R.drawable.provider_bailian_color_light
        "ark.cn-beijing.volces.com" -> if (dark) R.drawable.provider_volcengine_color_dark else R.drawable.provider_volcengine_color_light
        "open.bigmodel.cn" -> if (dark) R.drawable.provider_zhipu_color_dark else R.drawable.provider_zhipu_color_light
        "api.stepfun.com" -> if (dark) R.drawable.provider_stepfun_color_dark else R.drawable.provider_stepfun_color_light
        "api.302.ai" -> if (dark) R.drawable.provider_ai302_color_dark else R.drawable.provider_ai302_color_light
        "api.hunyuan.cloud.tencent.com" -> if (dark) R.drawable.provider_hunyuan_color_dark else R.drawable.provider_hunyuan_color_light
        "api.x.ai" -> if (dark) R.drawable.provider_xai_dark else R.drawable.provider_xai_light
        "api.minimaxi.com" -> if (dark) R.drawable.provider_minimax_color_dark else R.drawable.provider_minimax_color_light
        else -> null
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (logo != null) Image(painterResource(logo), contentDescription = null, modifier = Modifier.size(28.dp))
            else Text(fallback.take(2).uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ModelBadge(model: ProviderModel?, fallback: String) {
    val label = model?.icon?.takeIf(String::isNotBlank)?.take(4)
        ?: model?.displayName?.firstOrNull()?.uppercase()
        ?: fallback.firstOrNull()?.uppercase()
        ?: "AI"
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun CapabilitySelector(label: String, value: CapabilityState, onChange: (CapabilityState) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row {
            CapabilityState.entries.forEach { state ->
                FilterChip(
                    selected = value == state,
                    onClick = { onChange(state) },
                    label = { Text(state.label()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

private fun CapabilityState.label() = when (this) {
    CapabilityState.SUPPORTED -> "支持"
    CapabilityState.UNSUPPORTED -> "不支持"
    CapabilityState.UNKNOWN -> "未知"
}

@Composable
private fun GenerationSettings(settings: ModelSettings, onBack: () -> Unit, onSave: (ModelSettings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var context by remember(settings) { mutableStateOf(settings.maxContextMessages.toString()) }
    var temperature by remember(settings) { mutableStateOf(settings.temperature?.toString().orEmpty()) }
    var topP by remember(settings) { mutableStateOf(settings.topP?.toString().orEmpty()) }
    var maxTokens by remember(settings) { mutableStateOf(settings.maxOutputTokens?.toString().orEmpty()) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("生成设置", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            OutlinedTextField(context, { context = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("最多上下文消息数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(draft.systemPrompt, { draft = draft.copy(systemPrompt = it) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("全局系统提示词") }, minLines = 2)
            OutlinedTextField(temperature, { temperature = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Temperature") })
            OutlinedTextField(topP, { topP = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Top P") })
            OutlinedTextField(maxTokens, { maxTokens = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("最大输出 Token") })
            SettingSwitch("允许发送图片给模型", draft.imageInputEnabled) { draft = draft.copy(imageInputEnabled = it) }
            SettingSwitch("自动生成标题", draft.autoTitleEnabled) { draft = draft.copy(autoTitleEnabled = it) }
            SettingSwitch("允许内置工具调用", draft.toolCallingEnabled) { draft = draft.copy(toolCallingEnabled = it, memoryWriteEnabled = draft.memoryWriteEnabled && it) }
            SettingSwitch("允许 AI 写入记忆", draft.memoryWriteEnabled, draft.toolCallingEnabled) { draft = draft.copy(memoryWriteEnabled = it) }
        }
        Button(onClick = { onSave(draft.copy(maxContextMessages = context.toIntOrNull() ?: draft.maxContextMessages,
            temperature = temperature.toDoubleOrNull(), topP = topP.toDoubleOrNull(), maxOutputTokens = maxTokens.toIntOrNull())); onBack() },
            Modifier.fillMaxWidth().padding(16.dp)) { Text("保存") }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label); Switch(checked, onChange, enabled = enabled)
    }
}

private fun ProviderProtocol.label() = when (this) {
    ProviderProtocol.OPENAI_CHAT_COMPLETIONS -> "OpenAI 兼容"
    ProviderProtocol.GOOGLE_GENERATIVE_LANGUAGE -> "Google Gemini"
    ProviderProtocol.ANTHROPIC_MESSAGES -> "Anthropic Claude"
}
