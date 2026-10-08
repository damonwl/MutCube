package com.dwl.mutcube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.ai.ReasoningLevel
import com.dwl.mutcube.storage.QuickPrompt
import com.dwl.mutcube.storage.InstalledSkill
import com.dwl.mutcube.storage.SkillInvocation
import com.dwl.mutcube.core.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.io.File
import java.net.URI

@Composable
fun FollowUpSuggestions(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    LazyRow(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        items(suggestions) { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
fun MutCubeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onAttachmentClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceCancel: () -> Unit = {},
    voiceRecording: Boolean = false,
    voiceTranscribing: Boolean = false,
    voiceAmplitude: Float = 0f,
    voiceDurationSeconds: Int = 0,
    reasoningLevel: ReasoningLevel,
    onReasoningLevelChange: (ReasoningLevel) -> Unit,
    onSendClick: () -> Unit,
    isGenerating: Boolean,
    onStopClick: () -> Unit,
    enterToSend: Boolean = false,
    attachments: List<MessageContentPart.Attachment> = emptyList(),
    onRemoveAttachment: (MessageContentPart.Attachment) -> Unit = {},
    quickPrompts: List<QuickPrompt> = emptyList(),
    skills: List<InstalledSkill> = emptyList(),
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var showReasoningPicker by remember { mutableStateOf(false) }
    var showSkillPicker by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val activeFocusRequester = focusRequester ?: internalFocusRequester
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val voiceActive = voiceRecording || voiceTranscribing
    val availableSkills = skills.filter { it.invocation != SkillInvocation.OFF }
    val selectedSkill = availableSkills.firstOrNull { value.startsWith("\$${it.name} ") }
    val inputValue = selectedSkill?.let { value.removePrefix("\$${it.name} ") } ?: value
    val hasInput = inputValue.isNotBlank()

    fun selectSkill(skill: InstalledSkill) {
        onValueChange("\$${skill.name} $inputValue")
        showSkillPicker = false
    }

    fun clearSkill() {
        onValueChange(inputValue)
    }

    LaunchedEffect(expanded) {
        if (expanded) activeFocusRequester.requestFocus()
    }

    LaunchedEffect(voiceActive) {
        if (voiceActive) {
            expanded = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    if (showReasoningPicker) {
        ReasoningPicker(
            selected = reasoningLevel,
            onSelect = onReasoningLevelChange,
            onDismiss = { showReasoningPicker = false },
        )
    }
    if (showSkillPicker) {
        SkillPicker(
            skills = availableSkills,
            selectedId = selectedSkill?.id,
            onSelect = ::selectSkill,
            onDismiss = { showSkillPicker = false },
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (expanded && !voiceActive) 24.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        if (voiceActive) {
            VoiceInputBar(
                recording = voiceRecording,
                amplitude = voiceAmplitude,
                durationSeconds = voiceDurationSeconds,
                onCancel = onVoiceCancel,
                onFinish = onVoiceClick,
            )
        } else if (!expanded && attachments.isEmpty() && selectedSkill == null) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComposerTextField(
                    value = inputValue,
                    onValueChange = onValueChange,
                    enterToSend = enterToSend,
                    canSend = hasInput,
                    onSendClick = onSendClick,
                    focusRequester = activeFocusRequester,
                    onFocusChanged = { if (it) expanded = true },
                    modifier = Modifier.weight(1f),
                )
                ComposerIcon(Icons.Rounded.Mic, "语音输入", onVoiceClick)
                PrimaryComposerIcon(
                    canSend = hasInput,
                    isGenerating = isGenerating,
                    onClick = when {
                        isGenerating -> onStopClick
                        hasInput -> onSendClick
                        else -> onAttachmentClick
                    },
                )
            }
        } else Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (selectedSkill != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = { showSkillPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text("✦ ${selectedSkill.name} · 更换",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                    IconButton(onClick = ::clearSkill, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, "移除已选 Skill", modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (quickPrompts.isNotEmpty() && attachments.isEmpty()) {
                LazyRow(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    items(quickPrompts, key = QuickPrompt::id) { prompt ->
                        Surface(
                            onClick = { onValueChange(prompt.content) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Text(prompt.title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
            if (attachments.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth()) {
                    items(attachments, key = { it.id.value }) { attachment ->
                        if (attachment.kind == AttachmentKind.IMAGE) {
                            DraftImagePreview(attachment, onRemoveAttachment)
                        } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(end = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                            ) {
                                Text(attachment.displayName, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                IconButton(
                                    onClick = { onRemoveAttachment(attachment) },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(Icons.Rounded.Close, "移除附件", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        }
                    }
                    item(key = "add-attachment") {
                        Surface(
                            onClick = onAttachmentClick,
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 6.dp).size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, "增加附件", modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
            ComposerTextField(
                value = inputValue,
                onValueChange = { next ->
                    onValueChange(selectedSkill?.let { "\$${it.name} $next" } ?: next)
                },
                enterToSend = enterToSend,
                canSend = hasInput || attachments.isNotEmpty(),
                onSendClick = onSendClick,
                focusRequester = activeFocusRequester,
                onFocusChanged = {},
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSkillPicker = true }.padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Skill", modifier = Modifier.padding(start = 6.dp))
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.weight(1f))
                if (hasInput && attachments.isEmpty()) {
                    ComposerIcon(Icons.Rounded.Add, "添加附件", onAttachmentClick)
                }
                ComposerIcon(Icons.Rounded.GraphicEq, "推理强度：${reasoningLevel.composerLabel()}",
                    { showReasoningPicker = true })
                ComposerIcon(Icons.Rounded.Mic, "语音输入", onVoiceClick)
                PrimaryComposerIcon(
                    canSend = hasInput || attachments.isNotEmpty(),
                    isGenerating = isGenerating,
                    onClick = when {
                        isGenerating -> onStopClick
                        hasInput || attachments.isNotEmpty() -> onSendClick
                        else -> onAttachmentClick
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SkillPicker(
    skills: List<InstalledSkill>,
    selectedId: String?,
    onSelect: (InstalledSkill) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val duplicates = skills.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    val filtered = skills.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.description.contains(query.trim(), ignoreCase = true)
    }.sortedBy { it.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择 Skill", style = MaterialTheme.typography.titleLarge)
            Text("仅用于这条消息；自动匹配的 Skill 无需在这里选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索名称或用途") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (filtered.isEmpty()) item {
                    Text("没有找到当前会话可用的 Skill。",
                        Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(filtered, key = InstalledSkill::id) { skill ->
                    val ambiguous = skill.name in duplicates
                    Surface(
                        onClick = { onSelect(skill) },
                        enabled = !ambiguous,
                        shape = RoundedCornerShape(14.dp),
                        color = if (skill.id == selectedId) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                    ) {
                        ListItem(
                            headlineContent = { Text(skill.name) },
                            supportingContent = { Column {
                                Text(skill.description, maxLines = 2)
                                Text(if (ambiguous) "存在同名 Skill，暂不能明确选择，请先处理重名"
                                    else if (skill.invocation == SkillInvocation.AUTO) "自动匹配 · 也可手动选择"
                                    else "仅手动选择",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ambiguous) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                            } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceInputBar(
    recording: Boolean,
    amplitude: Float,
    durationSeconds: Int,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    val waveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + amplitude * 0.45f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.Close, "取消语音输入", modifier = Modifier.size(24.dp))
        }
        if (recording) {
            val levels = remember { androidx.compose.runtime.mutableStateListOf<Float>().apply { repeat(30) { add(0f) } } }
            val latestAmplitude by rememberUpdatedState(amplitude)
            LaunchedEffect(Unit) {
                while (true) {
                    levels.removeAt(0)
                    levels.add(latestAmplitude.coerceIn(0f, 1f))
                    kotlinx.coroutines.delay(80)
                }
            }
            Canvas(Modifier.weight(1f).padding(horizontal = 16.dp).heightIn(min = 32.dp, max = 32.dp)) {
                val bars = 30
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(2.dp.toPx())
                repeat(bars) { index ->
                    val strength = 0.12f + levels[index] * 0.88f
                    val barHeight = (size.height * strength).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = waveColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            index * (barWidth + gap),
                            (size.height - barHeight) / 2f,
                        ),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
            Surface(
                onClick = onFinish,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Stop,
                        "停止录音并转写",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "正在转录…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftImagePreview(
    attachment: MessageContentPart.Attachment,
    onRemove: (MessageContentPart.Attachment) -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, attachment.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val path = File(URI(attachment.uri)).path
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 256).coerceAtLeast(1)
                }
                BitmapFactory.decodeFile(path, options)
            }.getOrNull()
        }
    }
    Box(Modifier.padding(end = 6.dp).size(72.dp).clip(RoundedCornerShape(14.dp))) {
        bitmap?.let {
            Image(it.asImageBitmap(), attachment.displayName, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } ?: Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
        Surface(
            onClick = { onRemove(attachment) },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "删除图片", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ComposerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enterToSend: Boolean,
    canSend: Boolean,
    onSendClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier
            .heightIn(min = 40.dp, max = 120.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default),
        keyboardActions = KeyboardActions(onSend = { if (canSend) onSendClick() }),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("发消息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun ReasoningPicker(selected: ReasoningLevel, onSelect: (ReasoningLevel) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("推理强度", style = MaterialTheme.typography.titleLarge)
            Text("强度越高，通常思考更充分，但响应会更慢、消耗更多 Token。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
            androidx.compose.material3.Slider(
                value = ReasoningLevel.entries.indexOf(selected).toFloat(),
                onValueChange = { onSelect(ReasoningLevel.entries[kotlin.math.round(it).toInt().coerceIn(0, ReasoningLevel.entries.lastIndex)]) },
                valueRange = 0f..ReasoningLevel.entries.lastIndex.toFloat(),
                steps = ReasoningLevel.entries.size - 2,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                ReasoningLevel.entries.forEach { level ->
                    Text(level.shortLabel(), style = MaterialTheme.typography.labelSmall,
                        color = if (level == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onSelect(level) }.padding(4.dp))
                }
            }
        }
    }
}

private fun ReasoningLevel.composerLabel() = when (this) {
    ReasoningLevel.OFF -> "关闭推理"
    ReasoningLevel.AUTO -> "自动"
    ReasoningLevel.LOW -> "轻度"
    ReasoningLevel.MEDIUM -> "标准"
    ReasoningLevel.HIGH -> "重度"
    ReasoningLevel.XHIGH -> "极高"
}

private fun ReasoningLevel.shortLabel() = when (this) {
    ReasoningLevel.OFF -> "关闭"
    ReasoningLevel.AUTO -> "自动"
    ReasoningLevel.LOW -> "轻度"
    ReasoningLevel.MEDIUM -> "中度"
    ReasoningLevel.HIGH -> "重度"
    ReasoningLevel.XHIGH -> "极高"
}

@Composable
private fun ComposerIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PrimaryComposerIcon(
    canSend: Boolean,
    isGenerating: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when {
                    isGenerating -> Icons.Rounded.Stop
                    canSend -> Icons.AutoMirrored.Rounded.Send
                    else -> Icons.Rounded.Add
                },
                contentDescription = when {
                    isGenerating -> "停止生成"
                    canSend -> "发送"
                    else -> "添加附件"
                },
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
