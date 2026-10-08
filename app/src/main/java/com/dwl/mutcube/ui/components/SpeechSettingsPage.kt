package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.DisplaySettings
import com.dwl.mutcube.storage.SpeechProvider
import com.dwl.mutcube.storage.SpeechServiceSettings
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProtocol
import com.dwl.mutcube.core.ai.CapabilityState
import com.dwl.mutcube.core.ai.SpeechProtocols
import com.dwl.mutcube.core.ai.TtsVoiceCatalog

@Composable
fun SpeechSettingsPage(
    settings: DisplaySettings,
    serviceSettings: SpeechServiceSettings,
    providers: ProviderConfiguration,
    engines: List<Pair<String, String>>,
    voices: List<Pair<String, String>>,
    onBack: () -> Unit,
    onSave: (DisplaySettings, SpeechServiceSettings, String, String, (Result<Unit>) -> Unit) -> Unit,
    onDeleteTtsKey: ((Result<Unit>) -> Unit) -> Unit,
    onDeleteAsrKey: ((Result<Unit>) -> Unit) -> Unit,
    onNotice: (String) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var serviceDraft by remember(serviceSettings) { mutableStateOf(serviceSettings) }
    var ttsKey by remember { mutableStateOf("") }
    var asrKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("语音", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text("朗读（TTS）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            ProviderSelector(serviceDraft.ttsProvider) { serviceDraft = serviceDraft.copy(ttsProvider = it) }
            SettingSwitch("自动朗读 AI 回复", draft.autoReadReplies) { draft = draft.copy(autoReadReplies = it) }
            Text("朗读速度 ${"%.1f".format(draft.ttsSpeechRate)}×", modifier = Modifier.padding(top = 16.dp))
            Slider(
                value = draft.ttsSpeechRate,
                onValueChange = { draft = draft.copy(ttsSpeechRate = it) },
                valueRange = 0.5f..2f,
                steps = 5,
            )
            if (serviceDraft.ttsProvider == SpeechProvider.SYSTEM) {
                SelectionField(
                    label = "TTS 引擎",
                    value = engines.firstOrNull { it.first == draft.ttsEnginePackage }?.second ?: "系统默认",
                    choices = listOf("" to "系统默认") + engines,
                    onSelect = { draft = draft.copy(ttsEnginePackage = it, ttsVoiceName = "") },
                )
            } else {
                SpeechModelSelection(providers, serviceDraft.ttsProfileId, serviceDraft.ttsModel, false) { id, model, url ->
                    val selectedModel = providers.profiles.firstOrNull { it.id == id }?.models?.firstOrNull { it.id == model }
                    val defaultVoice = TtsVoiceCatalog.resolve(url, model, selectedModel)?.defaultVoice
                    serviceDraft = serviceDraft.copy(
                        ttsProfileId = id, ttsModel = model, ttsBaseUrl = url,
                        ttsVoice = if (id == serviceDraft.ttsProfileId && model == serviceDraft.ttsModel) serviceDraft.ttsVoice
                            else defaultVoice.orEmpty(),
                    )
                }
                if (serviceDraft.ttsProfileId.isBlank()) {
                OutlinedTextField(serviceDraft.ttsBaseUrl, { url ->
                    serviceDraft = serviceDraft.copy(ttsBaseUrl = url, ttsVoice = TtsVoiceCatalog.resolve(url, serviceDraft.ttsModel)?.defaultVoice.orEmpty())
                }, label = { Text("TTS API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                OutlinedTextField(serviceDraft.ttsModel, { model ->
                    serviceDraft = serviceDraft.copy(ttsModel = model, ttsVoice = TtsVoiceCatalog.resolve(serviceDraft.ttsBaseUrl, model)?.defaultVoice.orEmpty())
                }, label = { Text("TTS 模型") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(ttsKey, { ttsKey = it }, label = { Text("TTS API Key") }, placeholder = { Text("留空表示不修改") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteTtsKey { result -> onNotice(result.fold({ "已清除 TTS API Key" }, { "清除失败" })) }
                }) { Text("清除已保存的 TTS Key") }
                }
                val selectedModel = providers.profiles.firstOrNull { it.id == serviceDraft.ttsProfileId }
                    ?.models?.firstOrNull { it.id == serviceDraft.ttsModel }
                val voiceOptions = TtsVoiceCatalog.resolve(serviceDraft.ttsBaseUrl, serviceDraft.ttsModel, selectedModel)
                if (voiceOptions != null) {
                    SelectionField(
                        label = "音色",
                        value = if (serviceDraft.ttsVoice == voiceOptions.defaultVoice) "默认（${voiceOptions.defaultVoice}）"
                            else serviceDraft.ttsVoice.ifBlank { "请选择音色" },
                        choices = voiceOptions.voices.map { it to if (it == voiceOptions.defaultVoice) "默认（$it）" else it },
                        onSelect = { serviceDraft = serviceDraft.copy(ttsVoice = it) },
                    )
                    if (serviceDraft.ttsVoice.isNotBlank() && serviceDraft.ttsVoice !in voiceOptions.voices) {
                        Text("当前音色不在此模型的内置音色列表中，请重新选择。", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedTextField(serviceDraft.ttsVoice, { serviceDraft = serviceDraft.copy(ttsVoice = it) }, label = { Text("音色 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text("此模型没有已知的内置音色列表，请填写服务商提供的音色 ID，或在 Provider 的模型资料中配置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                draft.ttsLanguageTag,
                { draft = draft.copy(ttsLanguageTag = it) },
                label = { Text("朗读语言标签（留空跟随系统）") },
                placeholder = { Text("例如 zh-CN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (serviceDraft.ttsProvider == SpeechProvider.SYSTEM) SelectionField(
                label = "声音",
                value = voices.firstOrNull { it.first == draft.ttsVoiceName }?.second ?: "自动选择",
                choices = listOf("" to "自动选择") + voices,
                onSelect = { draft = draft.copy(ttsVoiceName = it) },
            )
            Text("语音输入（ASR）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            ProviderSelector(serviceDraft.asrProvider, systemLabel = "系统默认") { serviceDraft = serviceDraft.copy(asrProvider = it) }
            if (serviceDraft.asrProvider == SpeechProvider.OPENAI_COMPATIBLE) {
                SpeechModelSelection(providers, serviceDraft.asrProfileId, serviceDraft.asrModel, true) { id, model, url ->
                    serviceDraft = serviceDraft.copy(asrProfileId = id, asrModel = model, asrBaseUrl = url)
                }
                if (serviceDraft.asrProfileId.isBlank()) {
                OutlinedTextField(serviceDraft.asrBaseUrl, { serviceDraft = serviceDraft.copy(asrBaseUrl = it) }, label = { Text("ASR API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                OutlinedTextField(serviceDraft.asrModel, { serviceDraft = serviceDraft.copy(asrModel = it) }, label = { Text("ASR 模型") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(asrKey, { asrKey = it }, label = { Text("ASR API Key") }, placeholder = { Text("留空表示不修改") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteAsrKey { result -> onNotice(result.fold({ "已清除 ASR API Key" }, { "清除失败" })) }
                }) { Text("清除已保存的 ASR Key") }
                }
            } else {
                Text("调用手机系统识别，无需配置 Provider。联网、音量回调和停顿结束策略由系统服务提供，部分设备可能忽略停顿设置或出现超时。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("说完后停顿 ${serviceDraft.asrSilenceSeconds} 秒自动转写", Modifier.padding(top = 16.dp))
            Slider(serviceDraft.asrSilenceSeconds.toFloat(), { serviceDraft = serviceDraft.copy(asrSilenceSeconds = it.toInt()) }, valueRange = 2f..10f, steps = 7)
            OutlinedTextField(
                draft.asrLanguageTag,
                { draft = draft.copy(asrLanguageTag = it) },
                label = { Text("识别语言标签（留空跟随系统）") },
                placeholder = { Text("例如 zh-CN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            SettingSwitch("识别成功后直接发送", draft.asrAutoSend) { draft = draft.copy(asrAutoSend = it) }
            Text(
                "网络语音复用 Provider 的地址与密钥；ASR 接口由 Provider 声明，模型可以覆盖。独立配置使用标准表单转写接口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                onSave(draft, serviceDraft, ttsKey, asrKey) { result ->
                    busy = false
                    result.onSuccess { onNotice("语音设置已保存"); onBack() }
                        .onFailure { onNotice("保存失败：${it.message ?: "未知错误"}") }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text("保存") }
    }
}

@Composable
private fun ProviderSelector(value: SpeechProvider, systemLabel: String = "系统服务", onChange: (SpeechProvider) -> Unit) {
    Row(Modifier.padding(top = 8.dp)) {
        FilterChip(selected = value == SpeechProvider.SYSTEM, onClick = { onChange(SpeechProvider.SYSTEM) }, label = { Text(systemLabel) })
        Spacer(Modifier.size(8.dp))
        FilterChip(selected = value == SpeechProvider.OPENAI_COMPATIBLE, onClick = { onChange(SpeechProvider.OPENAI_COMPATIBLE) }, label = { Text("OpenAI 兼容") })
    }
}

@Composable
private fun SpeechModelSelection(
    providers: ProviderConfiguration,
    profileId: String,
    modelId: String,
    asr: Boolean,
    onSelect: (String, String, String) -> Unit,
) {
    val profiles = providers.profiles.filter { it.enabled && it.protocol == ProviderProtocol.OPENAI_CHAT_COMPLETIONS }
    val profile = profiles.firstOrNull { it.id == profileId }
    SelectionField("服务商", if (profileId.isBlank()) "独立配置" else profile?.name ?: "Provider 不可用，请重新选择",
        listOf("" to "独立配置") + profiles.map { it.id to it.name }) { id ->
        val selected = profiles.firstOrNull { it.id == id }
        onSelect(id, if (selected != null) "" else modelId, selected?.baseUrl ?: "https://api.openai.com/v1")
    }
    if (profile != null) {
        val models = profile.models.filter {
            (if (asr) it.capabilities.speechRecognition else it.capabilities.speechSynthesis) != CapabilityState.UNSUPPORTED
        }
        SelectionField("模型", profile.models.firstOrNull { it.id == modelId }?.displayName ?: modelId.ifBlank { "请选择音频模型" },
            models.map { it.id to (it.displayName + if ((if (asr) it.capabilities.speechRecognition else it.capabilities.speechSynthesis) == CapabilityState.UNKNOWN) "（能力未声明）" else "") }) { model ->
            onSelect(profile.id, model, profile.baseUrl)
        }
        Text(if (asr) "ASR 协议：${SpeechProtocols.resolveAsr(profile, modelId).label}。可在 Provider 中修改。能力未声明的模型保留以兼容已有配置，请确认其支持语音。" else "TTS 协议：${SpeechProtocols.resolveTts(profile, modelId).label}。切换模型后会自动选用该模型的默认音色。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectionField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        androidx.compose.material3.TextButton(onClick = { expanded = true }) { Text(value) }
        androidx.compose.material3.DropdownMenu(expanded, { expanded = false }) {
            choices.distinctBy { it.first }.forEach { (key, name) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(key); expanded = false },
                )
            }
        }
    }
}
