package com.dwl.mutcube.ui

import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.MessageId
import com.dwl.mutcube.core.model.MessageNodeId
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.AttachmentKind
import com.dwl.mutcube.ui.components.MarkdownMessage
import com.dwl.mutcube.ui.components.MutCubeMark
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderModel
import com.dwl.mutcube.storage.ChatBackground
import com.dwl.mutcube.storage.BubbleStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File

@Composable
internal fun ChatSurface(
    templateCapabilities: List<com.dwl.mutcube.storage.TemplateChatCapability> = emptyList(),
    liveToolTrace: List<MessageContentPart> = emptyList(),
    onTemplateCapabilities: () -> Unit = {},
    onOpenTemplateResult: ((org.json.JSONObject) -> Unit)? = null,
    messages: List<ChatMessage>,
    providers: ProviderConfiguration,
    streamingText: String,
    isGenerating: Boolean,
    regeneratingNodeId: MessageNodeId?,
    modelError: String?,
    canRetryGeneration: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onRegenerate: (MessageNodeId) -> Unit,
    onSelectVariant: (MessageNodeId, MessageId) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onFork: (ChatMessage) -> Unit,
    onFavorite: (ChatMessage) -> Unit,
    onCopy: (ChatMessage) -> Unit,
    onShare: (ChatMessage) -> Unit,
    onTranslate: (ChatMessage) -> Unit,
    onSpeak: (ChatMessage) -> Unit,
    speakingMessageId: String? = null,
    speechPreparingMessageId: String? = null,
    showMessageTime: Boolean,
    autoScroll: Boolean,
    chatBackground: ChatBackground,
    bubbleStyle: BubbleStyle,
    showAvatars: Boolean,
    showModelLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    var showErrorDetails by remember { mutableStateOf(false) }
    if (messages.isEmpty() && streamingText.isEmpty() && modelError == null && !isGenerating) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 82.dp)) {
                MutCubeMark(size = 42.dp)
                Text(
                    "有什么可以帮你？",
                    modifier = Modifier.padding(top = 20.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "一种智能，无限形态",
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (templateCapabilities.isNotEmpty()) TextButton(onClick = onTemplateCapabilities) {
                    Text("已连接${templateCapabilities.first().templateName} · 查看模板能力")
                }
            }
        }
        return
    }
    val visibleMessages = messages.filterNot { it.nodeId == regeneratingNodeId }
    val latestPersistedFailure = visibleMessages.lastOrNull()
        ?.parts
        ?.filterIsInstance<MessageContentPart.Error>()
        ?.lastOrNull()
    val showTransientModelError = modelError != null && latestPersistedFailure == null
    val regeneratableNodeId = messages.lastOrNull()
        ?.takeIf { it.role == MessageRole.AI && !isGenerating }
        ?.nodeId
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var followOutput by remember { mutableStateOf(true) }
    LaunchedEffect(isDragged) {
        if (isDragged) followOutput = false
    }
    LaunchedEffect(isDragged, listState.canScrollForward) {
        if (!isDragged && !listState.canScrollForward) followOutput = true
    }
    val newestMessage = visibleMessages.lastOrNull()
    LaunchedEffect(newestMessage?.id) {
        if (newestMessage?.role == MessageRole.USER) {
            followOutput = true
            listState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(streamingText, isGenerating, liveToolTrace, modelError, autoScroll, followOutput) {
        if (autoScroll && followOutput) {
            val tailIndex = visibleMessages.size +
                (if (streamingText.isNotEmpty()) 1 else 0) +
                (if (isGenerating && streamingText.isEmpty() && liveToolTrace.none { it is MessageContentPart.ToolCall && it.toolName.startsWith("template_action_") && it.status == com.dwl.mutcube.core.model.ToolCallStatus.RUNNING }) 1 else 0) +
                (if (isGenerating && liveToolTrace.isNotEmpty()) 1 else 0) +
                (if (showTransientModelError) 1 else 0)
            listState.scrollToItem(tailIndex)
        }
    }
    if (showErrorDetails && modelError != null && latestPersistedFailure == null) {
        AlertDialog(
            onDismissRequest = { showErrorDetails = false },
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
            title = { Text("消息生成失败") },
            text = { Text(modelError) },
            confirmButton = {
                if (canRetryGeneration) {
                    TextButton(onClick = { showErrorDetails = false; onRetry() }) { Text("重新尝试") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDetails = false; onOpenSettings() }) { Text("检查设置") }
            },
        )
    }
    val chatBackgroundColor = when (chatBackground) {
        ChatBackground.DEFAULT -> MaterialTheme.colorScheme.background
        ChatBackground.WARM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ChatBackground.COOL -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    }
    Box(modifier.fillMaxWidth().background(chatBackgroundColor)) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
    ) {
        items(visibleMessages, key = { it.nodeId.value }) { message ->
            val model = message.modelId?.let { id ->
                providers.profiles.asSequence().flatMap { it.models.asSequence() }.firstOrNull { it.id == id }
            }
            MessageBubble(
                templateCapabilities = templateCapabilities,
                onOpenTemplateResult = onOpenTemplateResult,
                text = message.text,
                parts = message.parts,
                isUser = message.role == MessageRole.USER,
                createdAt = message.createdAt,
                showMessageTime = showMessageTime,
                status = message.status,
                modelId = message.modelId,
                model = model,
                inputTokens = message.inputTokens,
                cachedInputTokens = message.cachedInputTokens,
                outputTokens = message.outputTokens,
                generationDurationMs = message.generationDurationMs,
                variantIndex = message.selectedVariantIndex,
                variantCount = message.variantIds.size,
                onPreviousVariant = message.variantIds.getOrNull(message.selectedVariantIndex - 1)?.let { variantId ->
                    { onSelectVariant(message.nodeId, variantId) }
                },
                onNextVariant = message.variantIds.getOrNull(message.selectedVariantIndex + 1)?.let { variantId ->
                    { onSelectVariant(message.nodeId, variantId) }
                },
                onRegenerate = if (message.nodeId == regeneratableNodeId) {
                    { onRegenerate(message.nodeId) }
                } else {
                    null
                },
                onEdit = if (message.role == MessageRole.USER) { { onEdit(message) } } else null,
                onDelete = { onDelete(message) },
                onFork = { onFork(message) },
                onFavorite = { onFavorite(message) },
                onCopy = { onCopy(message) },
                onShare = { onShare(message) },
                favorite = message.favorite,
                translation = message.translation,
                onTranslate = { onTranslate(message) },
                onSpeak = { onSpeak(message) },
                isSpeaking = speakingMessageId == message.id.value,
                isSpeechPreparing = speechPreparingMessageId == message.id.value,
                bubbleStyle = bubbleStyle,
                showAvatar = showAvatars,
                showModelLabel = showModelLabel,
            )
        }
        if (isGenerating && liveToolTrace.isNotEmpty()) {
            item(key = "live-template-operations") {
                TemplateOperationCards(liveToolTrace, templateCapabilities, onOpenTemplateResult)
            }
        }
        if (isGenerating && streamingText.isEmpty() && liveToolTrace.none { it is MessageContentPart.ToolCall && it.toolName.startsWith("template_action_") && it.status == com.dwl.mutcube.core.model.ToolCallStatus.RUNNING }) {
            item(key = "thinking") {
                Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在思考…", Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (streamingText.isNotEmpty()) {
            item(key = "streaming") {
                MessageBubble(
                    text = streamingText,
                    isUser = false,
                    bubbleStyle = bubbleStyle,
                    showAvatar = showAvatars,
                    showModelLabel = false,
                )
            }
        }
        modelError?.takeIf { latestPersistedFailure == null }?.let {
            item(key = "model-error") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    Surface(
                        onClick = { showErrorDetails = true },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = "查看生成失败原因",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(6.dp).size(18.dp),
                        )
                    }
                }
            }
        }
        item(key = "scroll-tail") { Spacer(Modifier.height(1.dp)) }
    }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(chatBackgroundColor, chatBackgroundColor.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(chatBackgroundColor.copy(alpha = 0f), chatBackgroundColor),
                    ),
                ),
        )
    }
}

@Composable
private fun MessageBubble(
    templateCapabilities: List<com.dwl.mutcube.storage.TemplateChatCapability> = emptyList(),
    onOpenTemplateResult: ((org.json.JSONObject) -> Unit)? = null,
    text: String,
    isUser: Boolean,
    parts: List<MessageContentPart> = emptyList(),
    createdAt: Long? = null,
    showMessageTime: Boolean = false,
    status: MessageStatus = MessageStatus.COMPLETE,
    modelId: String? = null,
    model: ProviderModel? = null,
    inputTokens: Long? = null,
    cachedInputTokens: Long? = null,
    outputTokens: Long? = null,
    generationDurationMs: Long? = null,
    variantIndex: Int = 0,
    variantCount: Int = 1,
    onPreviousVariant: (() -> Unit)? = null,
    onNextVariant: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onFork: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    favorite: Boolean = false,
    translation: String? = null,
    onTranslate: (() -> Unit)? = null,
    onSpeak: (() -> Unit)? = null,
    isSpeaking: Boolean = false,
    isSpeechPreparing: Boolean = false,
    bubbleStyle: BubbleStyle = BubbleStyle.STANDARD,
    showAvatar: Boolean = false,
    showModelLabel: Boolean = true,
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var showTechnicalDetails by remember { mutableStateOf(false) }
    var showFailureDetails by remember { mutableStateOf(false) }
    val attachments = parts.filterIsInstance<MessageContentPart.Attachment>()
    val failure = parts.filterIsInstance<MessageContentPart.Error>().lastOrNull()
    val technicalParts = parts.filter {
        it is MessageContentPart.Reasoning || it is MessageContentPart.ToolCall || it is MessageContentPart.ToolResult
    }
    if (showFailureDetails && failure != null) {
        AlertDialog(
            onDismissRequest = { showFailureDetails = false },
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) },
            title = { Text("消息生成失败") },
            text = { Text(failure.message) },
            confirmButton = {
                onRegenerate?.let { regenerate ->
                    TextButton(onClick = { showFailureDetails = false; regenerate() }) { Text("重新尝试") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showFailureDetails = false }) { Text("关闭") }
            },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser) TemplateOperationCards(parts, templateCapabilities, onOpenTemplateResult)
        if (showAvatar || (!isUser && showModelLabel && modelId != null)) {
            Text(
                when {
                    isUser -> "我"
                    showModelLabel && modelId != null -> listOfNotNull(
                        model?.icon?.takeIf(String::isNotBlank),
                        model?.displayName ?: modelId,
                    ).joinToString("  ")
                    else -> "AI"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp),
            )
        }
        val userContainer = MaterialTheme.colorScheme.primary
        val assistantContainer = when (bubbleStyle) {
            BubbleStyle.STANDARD -> MaterialTheme.colorScheme.surface
            BubbleStyle.TONAL -> MaterialTheme.colorScheme.surfaceVariant
            BubbleStyle.MINIMAL -> androidx.compose.ui.graphics.Color.Transparent
        }
        val userForeground = MaterialTheme.colorScheme.onPrimary
        val hasVisibleBubbleContent = isUser || text.isNotBlank() || attachments.isNotEmpty() ||
            technicalParts.isNotEmpty() || status != MessageStatus.FAILED
        if (hasVisibleBubbleContent) Surface(
            modifier = if (isUser) Modifier.padding(vertical = 6.dp).combinedClickable(
                onClick = {},
                onLongClick = { actionMenuExpanded = true },
            ) else Modifier.padding(vertical = 6.dp),
            shape = RoundedCornerShape(if (bubbleStyle == BubbleStyle.MINIMAL) 12.dp else 20.dp),
            color = if (isUser) userContainer else assistantContainer,
        ) {
            if (isUser) {
                Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                    attachments.forEach { attachment ->
                        AttachmentCard(attachment, isUser = true)
                    }
                    if (text.isNotBlank()) {
                        Text(
                            text,
                            modifier = Modifier.padding(top = if (attachments.isEmpty()) 0.dp else 8.dp),
                            color = userForeground,
                        )
                    }
                    if (showMessageTime && createdAt != null) MessageTime(createdAt, true)
                }
            } else {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                    attachments.forEach { attachment -> AttachmentCard(attachment, isUser = false) }
                    if (technicalParts.isNotEmpty()) {
                        TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                            Text(if (showTechnicalDetails) "收起执行详情" else "查看执行详情")
                        }
                        if (showTechnicalDetails) {
                            technicalParts.forEach { part -> TechnicalPartCard(part) }
                        }
                    }
                    SelectionContainer {
                        MarkdownMessage(markdown = text)
                    }
                    if (showMessageTime && createdAt != null) MessageTime(createdAt, false)
                    translation?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (status == MessageStatus.STOPPED) {
                        Text(
                            "已停止生成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else if (status == MessageStatus.FAILED) {
                        Text(
                            "回复未完成，已有消息与工具调用记录已保留",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        if (failure != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Surface(
                    onClick = { showFailureDetails = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = "查看生成失败原因",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
            }
        }
        val hasActions = listOf(onCopy, onShare, onRegenerate, onSpeak, onTranslate, onFavorite, onEdit, onFork, onDelete)
            .any { it != null } || variantCount > 1
        if (hasActions) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onCopy?.let { MessageActionIcon(Icons.Rounded.ContentCopy, "复制", it) }
                onShare?.let { MessageActionIcon(Icons.Rounded.IosShare, "分享", it) }
                if (!isUser) {
                    onRegenerate?.let { MessageActionIcon(Icons.Rounded.Refresh, "重新生成", it) }
                    onSpeak?.let {
                        MessageActionIcon(if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            if (isSpeaking) "停止朗读" else "朗读", it)
                    }
                    if (isSpeechPreparing) Text("正在生成语音…", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    onTranslate?.let {
                        MessageActionIcon(
                            Icons.Rounded.Translate,
                            if (translation == null) "翻译" else "隐藏译文",
                            it,
                        )
                    }
                }
                if (variantCount > 1) {
                    IconButton(
                        onClick = { onPreviousVariant?.invoke() },
                        enabled = onPreviousVariant != null,
                        modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一版本", modifier = Modifier.size(19.dp)) }
                    Text("${variantIndex + 1}/$variantCount", style = MaterialTheme.typography.labelSmall)
                    IconButton(
                        onClick = { onNextVariant?.invoke() },
                        enabled = onNextVariant != null,
                        modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Rounded.ChevronRight, contentDescription = "下一版本", modifier = Modifier.size(19.dp)) }
                }
                Box {
                    MessageActionIcon(Icons.Rounded.MoreVert, "更多操作") { actionMenuExpanded = true }
                    DropdownMenu(
                        expanded = actionMenuExpanded,
                        onDismissRequest = { actionMenuExpanded = false },
                    ) {
                        onFavorite?.let {
                            DropdownMenuItem(
                                text = { Text(if (favorite) "取消收藏" else "收藏") },
                                onClick = { actionMenuExpanded = false; it() },
                            )
                        }
                        onEdit?.let {
                            DropdownMenuItem(text = { Text("编辑") }, onClick = { actionMenuExpanded = false; it() })
                        }
                        onFork?.let {
                            DropdownMenuItem(
                                text = { Text("从此处派生对话") },
                                onClick = { actionMenuExpanded = false; it() },
                            )
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = { Text("删除此处及后续") },
                                onClick = { actionMenuExpanded = false; it() },
                            )
                        }
                    }
                }
            }
        }
        if (!isUser && (modelId != null || inputTokens != null || outputTokens != null || generationDurationMs != null)) {
            val inputUsage = inputTokens?.let { input ->
                buildString {
                    append("⇧ ${formatTokenCount(input)} tokens")
                    cachedInputTokens?.takeIf { it > 0 }?.let { append(" (${formatTokenCount(it)} cached)") }
                }
            }
            val speed = if (outputTokens != null && generationDurationMs != null && generationDurationMs > 0) {
                outputTokens * 1_000.0 / generationDurationMs
            } else {
                null
            }
            val usage = listOfNotNull(
                inputUsage,
                outputTokens?.let { "⇩ ${formatTokenCount(it)} tokens" },
                speed?.let { "ϟ %.1f tok/s".format(Locale.US, it) },
                generationDurationMs?.let { "◷ ${formatDuration(it)}" },
                model?.displayName ?: modelId,
            ).joinToString("  ·  ")
            Text(
                usage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun MessageActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(Locale.US, value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(Locale.US, value / 1_000.0)
    else -> value.toString()
}

private fun formatDuration(durationMs: Long): String = if (durationMs < 1_000) {
    "${durationMs}ms"
} else {
    "%.1fs".format(Locale.US, durationMs / 1_000.0)
}

@Composable
private fun TechnicalPartCard(part: MessageContentPart) {
    val (title, detail) = when (part) {
        is MessageContentPart.Reasoning -> "模型思考" to part.text
        is MessageContentPart.ToolCall -> "工具 · ${part.toolName}" to
            "状态：${part.status.name.lowercase()}\n参数：${part.argumentsJson}"
        is MessageContentPart.ToolResult -> "工具结果" to part.output
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageTime(createdAt: Long, isUser: Boolean) {
    Text(
        text = remember(createdAt) { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(createdAt)) },
        style = MaterialTheme.typography.labelSmall,
        color = if (isUser) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun AttachmentCard(attachment: MessageContentPart.Attachment, isUser: Boolean) {
    val foreground = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val background = if (isUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    if (attachment.kind == AttachmentKind.IMAGE) {
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = attachment.uri) {
            value = withContext(Dispatchers.IO) { decodeAttachmentPreview(attachment.uri) }
        }
        bitmap?.let { preview ->
            val previewWidth = 250.dp
            val previewHeight = (250f * preview.height / preview.width.coerceAtLeast(1))
                .coerceIn(140f, 320f)
                .dp
            Surface(
                color = background,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Column {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(previewWidth).height(previewHeight),
                    )
                    Text(
                        attachment.displayName,
                        color = foreground.copy(alpha = 0.78f),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            return
        }
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = when (attachment.kind) {
                    AttachmentKind.IMAGE -> Icons.Rounded.Image
                    AttachmentKind.AUDIO -> Icons.Rounded.AudioFile
                    AttachmentKind.VIDEO -> Icons.Rounded.VideoFile
                    AttachmentKind.DOCUMENT -> Icons.Rounded.AttachFile
                },
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(attachment.displayName, color = foreground, maxLines = 1)
                Text(
                    formatFileSize(attachment.sizeBytes),
                    color = foreground.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun decodeAttachmentPreview(uri: String): Bitmap? = runCatching {
    val file = File(java.net.URI(uri))
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1_200 || bounds.outHeight / sampleSize > 1_200) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}.getOrNull()

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
