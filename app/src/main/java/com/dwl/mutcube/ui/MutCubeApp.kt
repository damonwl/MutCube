package com.dwl.mutcube.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.dwl.mutcube.speech.ContinuousSpeechRecorder
import com.dwl.mutcube.speech.SystemSpeechInput
import com.dwl.mutcube.speech.SpeechPauseDetector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageStatus
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.storage.AttachmentStore
import com.dwl.mutcube.IncomingShare
import com.dwl.mutcube.IncomingShortcut
import com.dwl.mutcube.MainActivity
import com.dwl.mutcube.EXTRA_SHORTCUT_KIND
import com.dwl.mutcube.EXTRA_SHORTCUT_TARGET
import com.dwl.mutcube.ui.components.AttachmentSheet
import com.dwl.mutcube.ui.components.ConversationSettingsPage
import com.dwl.mutcube.ui.components.EditMessageDialog
import com.dwl.mutcube.ui.components.MutCubeComposer
import com.dwl.mutcube.ui.components.FollowUpSuggestions
import com.dwl.mutcube.ui.components.MutCubeDrawer
import com.dwl.mutcube.ui.components.MutCubeTopBar
import com.dwl.mutcube.ui.components.MutCubeNoticeHost
import com.dwl.mutcube.ui.components.ProjectDetailPage
import com.dwl.mutcube.ui.components.MoveConversationDialog
import com.dwl.mutcube.ui.components.RenameConversationDialog
import com.dwl.mutcube.ui.components.RenameSpaceDialog
import com.dwl.mutcube.ui.components.ProjectSettingsPage
import com.dwl.mutcube.ui.components.AllChatsPage
import com.dwl.mutcube.ui.components.FavoritesPage
import com.dwl.mutcube.ui.components.NewProjectPage
import com.dwl.mutcube.ui.components.ConversationActionsSheet
import com.dwl.mutcube.ui.components.ExportConversationDialog
import com.dwl.mutcube.ui.components.TranslationPage
import com.dwl.mutcube.feature.chat.ConversationExport
import com.dwl.mutcube.feature.chat.ConversationExportFormat
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.storage.ToolApprovalDecision
import com.dwl.mutcube.storage.SpeechProvider
import com.dwl.mutcube.storage.BackupPreview
import com.dwl.mutcube.speech.SystemTtsFilePlayer
import com.dwl.mutcube.storage.GenerationForegroundService
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.io.ByteArrayOutputStream
import java.io.File



private enum class LibraryDestination { ALL_CHATS, FAVORITES }

private fun backupPreviewText(preview: BackupPreview): String = buildString {
    append("创建时间：")
    append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(preview.createdAt)))
    append(if (preview.encrypted) "\n加密：已使用备份密码保护" else "\n加密：无（请勿存放在不可信位置）")
    append("\n项目 ${preview.projectCount} · 会话 ${preview.conversationCount} · 记忆 ${preview.memoryCount}")
    append("\n模板记录 ${preview.templateRecordCount} · 附件 ${preview.attachmentCount}")
    append("\n头像文件 ${preview.avatarCount} · Skills ${preview.skillCount} · 已安装模板 ${preview.installedTemplateCount}")
    append("\n共 ${preview.entryCount} 项，${preview.bytes / 1024} KiB")
    if (preview.schemaVersion == 1) append("\n旧版备份不包含头像与 Skills；恢复时会保留本机现有文件。")
    if (preview.legacyVersionWarning) append("\n检测到旧版清单的数据库版本标记错误，已按数据库实际版本校验。")
    append("\n\n恢复将替换当前数据；API Key 和远程存储密码不会从备份恢复。模板授权会撤销，恢复的 Skills 会设为停用。")
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MutCubeApp(
    viewModel: MutCubeViewModel,
    incomingShare: IncomingShare? = null,
    onIncomingShareConsumed: () -> Unit = {},
    incomingShortcut: IncomingShortcut? = null,
    onIncomingShortcutConsumed: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()
    val skillAudit by viewModel.skillAudit.collectAsStateWithLifecycle()
    val latestUiState by rememberUpdatedState(uiState)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var backupRestartRequiredMessage by remember { mutableStateOf<String?>(null) }
    val templateContainer = (context.applicationContext as com.dwl.mutcube.MutCubeApplication).container
    val externalTemplates by templateContainer.installedTemplateStore.templates.collectAsStateWithLifecycle()
    val templateDefinitions = templateContainer.builtinTemplates + externalTemplates.map { it.definition.manifest }
    var templateProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTemplateId by rememberSaveable { mutableStateOf("mutcube.fitness") }
    val selectedTemplate = templateDefinitions.firstOrNull { it.id == selectedTemplateId } ?: templateDefinitions.first()
    LaunchedEffect(selectedTemplateId) {
        if (templateDefinitions.none { it.id == selectedTemplateId }) {
            selectedTemplateId = templateDefinitions.first().id
            templateProjectId = null
        }
    }
    val templateBindings by templateContainer.templateRepository.userBindings.collectAsStateWithLifecycle(initialValue = emptyList())
    val templatePermissions by templateContainer.templateRepository.userPermissions.collectAsStateWithLifecycle(initialValue = emptyList())
    val liveToolTrace by viewModel.liveToolTrace.collectAsStateWithLifecycle()
    val liveToolConversationId by viewModel.liveToolConversationId.collectAsStateWithLifecycle()
    var templateCapabilities by remember { mutableStateOf<List<com.dwl.mutcube.storage.TemplateChatCapability>>(emptyList()) }
    var showTemplateCapabilities by remember { mutableStateOf(false) }
    var templateLaunchTarget by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.selectedSpaceId, templateBindings, externalTemplates, uiState.modelSettings.toolCallingEnabled, uiState.providerConfiguration, uiState.spaces) {
        val project = uiState.spaces.firstOrNull { it.id == uiState.selectedSpaceId }
        val provider = uiState.providerConfiguration.profiles.firstOrNull { it.id == project?.modelProfileId }
            ?: uiState.providerConfiguration.activeProfile
        val model = provider.models.firstOrNull { it.id == (project?.modelIdOverride ?: provider.modelId) }
        templateCapabilities = if (!uiState.modelSettings.toolCallingEnabled || model?.capabilities?.toolCalling == com.dwl.mutcube.core.ai.CapabilityState.UNSUPPORTED) emptyList()
            else com.dwl.mutcube.storage.TemplateDataToolService(templateContainer.templateRepository,
            templateContainer.templateRuntime, templateContainer.conversationRepository, templateDefinitions)
            .capabilities(uiState.selectedSpaceId)
    }
    var choosingTemplateProject by remember { mutableStateOf(false) }
    var choosingExistingTemplateProject by remember { mutableStateOf(false) }
    var choosingBoundTemplateProjectId by remember { mutableStateOf<String?>(null) }
    var templateAuthorizationBusy by remember { mutableStateOf(false) }
    var pendingExternalTemplateProject by remember { mutableStateOf<com.dwl.mutcube.core.model.Space?>(null) }
    var showTemplateData by remember { mutableStateOf(false) }
    var showTemplatePackages by remember { mutableStateOf(false) }
    val appFocusManager = LocalFocusManager.current
    val appKeyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open) {
            appFocusManager.clearFocus(force = true)
            appKeyboardController?.hide()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var textToSpeechReady by remember { mutableStateOf(false) }
    val configuredTtsEngine = uiState.displaySettings.ttsEnginePackage.takeIf(String::isNotBlank)
    val resolvedTtsEngine = remember(configuredTtsEngine) {
        configuredTtsEngine ?: context.packageManager
            .queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            .firstOrNull()
            ?.serviceInfo
            ?.packageName
    }
    val textToSpeech = remember(resolvedTtsEngine) {
        textToSpeechReady = false
        val listener = TextToSpeech.OnInitListener { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (status != TextToSpeech.SUCCESS) {
                scope.launch { snackbarHostState.showSnackbar("系统朗读服务初始化失败，请检查系统 TTS 设置") }
            }
        }
        resolvedTtsEngine?.let { engine ->
            TextToSpeech(context.applicationContext, listener, engine)
        } ?: TextToSpeech(context.applicationContext, listener)
    }
    DisposableEffect(textToSpeech) {
        onDispose { textToSpeech.shutdown() }
    }
    val systemTtsPlayer = remember(resolvedTtsEngine) {
        SystemTtsFilePlayer(context.applicationContext, resolvedTtsEngine)
    }
    DisposableEffect(systemTtsPlayer) {
        onDispose { systemTtsPlayer.stop() }
    }
    LaunchedEffect(textToSpeechReady, uiState.displaySettings.ttsLanguageTag, uiState.displaySettings.ttsVoiceName) {
        if (textToSpeechReady) {
            textToSpeech.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val language = uiState.displaySettings.ttsLanguageTag.takeIf(String::isNotBlank)
                ?.let(Locale::forLanguageTag) ?: Locale.getDefault()
            textToSpeech.language = language
            uiState.displaySettings.ttsVoiceName.takeIf(String::isNotBlank)?.let { name ->
                textToSpeech.voices?.firstOrNull { it.name == name }?.let { textToSpeech.voice = it }
            }
        }
    }
    val attachmentStore = remember(context) { AttachmentStore(context) }
    var message by rememberSaveable { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<MessageContentPart.Attachment>>(emptyList()) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showTemplateSearch by remember { mutableStateOf(false) }
    var settingsStack by remember { mutableStateOf<List<SettingsDestination>>(emptyList()) }
    val settingsStateHolder = rememberSaveableStateHolder()
    var memoryInitialProjectId by remember { mutableStateOf<SpaceId?>(null) }
    var libraryDestination by remember { mutableStateOf<LibraryDestination?>(null) }
    var showCreateSpacePage by remember { mutableStateOf(false) }
    var showConversationSpaceDialog by remember { mutableStateOf(false) }
    var showConversationActions by remember { mutableStateOf(false) }
    var templateQuery by remember { mutableStateOf("") }
    var renamingConversation by remember { mutableStateOf<com.dwl.mutcube.core.model.Conversation?>(null) }
    var movingConversation by remember { mutableStateOf<com.dwl.mutcube.core.model.Conversation?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var renamingSpace by remember { mutableStateOf<com.dwl.mutcube.core.model.Space?>(null) }
    var configuringSpace by remember { mutableStateOf<com.dwl.mutcube.core.model.Space?>(null) }
    var renameSpaceName by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessageText by remember { mutableStateOf("") }
    var exportingConversation by remember { mutableStateOf<com.dwl.mutcube.core.model.Conversation?>(null) }
    var pendingExport by remember { mutableStateOf<ConversationExport?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRemoteRestore by remember { mutableStateOf(false) }
    var pendingRestorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var pendingRemotePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var showBackupPasswordPrompt by remember { mutableStateOf(false) }
    var backupPasswordDraft by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var pendingLocalBackupPassword by remember { mutableStateOf<String?>(null) }
    var restorePasswordDraft by remember { mutableStateOf("") }
    var pendingRestorePassword by remember { mutableStateOf<String?>(null) }
    var pendingRemotePassword by remember { mutableStateOf<String?>(null) }
    var restoreInspecting by remember { mutableStateOf(false) }
    var completionPending by remember { mutableStateOf(false) }
    var pendingOAuthServer by remember { mutableStateOf<McpServer?>(null) }
    var pendingProviderExport by remember { mutableStateOf<String?>(null) }
    var showTranslationPage by remember { mutableStateOf(false) }
    var openedProjectId by remember { mutableStateOf<SpaceId?>(null) }
    var conversationOriginProjectId by remember { mutableStateOf<SpaceId?>(null) }
    val projectComposerFocusRequester = remember { FocusRequester() }
    var skipNextDraftClear by remember { mutableStateOf(false) }
    var networkRecorder by remember { mutableStateOf<ContinuousSpeechRecorder?>(null) }
    val systemSpeechInput = remember(context) { SystemSpeechInput(context) }
    var systemVoiceActive by remember { mutableStateOf(false) }
    var retainedVoiceRecording by remember { mutableStateOf<File?>(null) }
    var voiceTranscriptionError by remember { mutableStateOf<String?>(null) }
    var networkSpeechJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var speechPreparingMessageId by remember { mutableStateOf<String?>(null) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    var speechPlaybackSession by remember { mutableStateOf(0) }
    var voiceTranscribing by remember { mutableStateOf(false) }
    var voiceSessionId by remember { mutableStateOf(0) }
    var voiceAmplitude by remember { mutableStateOf(0f) }
    var voiceDurationSeconds by remember { mutableStateOf(0) }
    var voiceStartedAt by remember { mutableStateOf(0L) }
    var pendingVoicePermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(uiState.selectedConversationId, uiState.speechServiceSettings, uiState.providerConfiguration) {
        speechPlaybackSession += 1
        networkSpeechJob?.cancel()
        networkSpeechJob = null
        systemTtsPlayer.stop()
        speakingMessageId = null
        speechPreparingMessageId = null
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceSessionId += 1
            systemSpeechInput.close()
            networkRecorder?.discard()
            networkSpeechJob?.cancel()
            if (!voiceTranscribing) retainedVoiceRecording?.delete()
        }
    }

    fun applyRecognizedText(recognized: String) {
        val normalized = recognized.trim()
        if (normalized.isEmpty()) return
        val combined = listOf(message.trim(), normalized).filter(String::isNotEmpty).joinToString(" ")
        if (latestUiState.displaySettings.asrAutoSend && !latestUiState.isGenerating) {
            viewModel.sendUserMessage(combined, pendingAttachments)
            message = ""
            pendingAttachments = emptyList()
        } else {
            message = combined
        }
    }


    fun speakText(text: String, utteranceId: String) {
        speechPlaybackSession += 1
        val playbackSession = speechPlaybackSession
        val shouldStop = speakingMessageId == utteranceId
        systemTtsPlayer.stop()
        networkSpeechJob?.cancel()
        networkSpeechJob = null
        speechPreparingMessageId = null
        speakingMessageId = null
        if (shouldStop || text.isBlank()) return
        speakingMessageId = utteranceId
        if (latestUiState.speechServiceSettings.ttsProvider == SpeechProvider.SYSTEM) {
            if (text.isBlank()) return
            val language = latestUiState.displaySettings.ttsLanguageTag.takeIf(String::isNotBlank)
                ?.let(Locale::forLanguageTag) ?: Locale.getDefault()
            systemTtsPlayer.speak(
                markdownText = text,
                locale = language,
                voiceName = latestUiState.displaySettings.ttsVoiceName,
                speechRate = latestUiState.displaySettings.ttsSpeechRate,
                onError = { error ->
                    if (speechPlaybackSession == playbackSession) speakingMessageId = null
                    scope.launch { snackbarHostState.showSnackbar(error) }
                },
                onFinished = { if (speechPlaybackSession == playbackSession) speakingMessageId = null },
            )
            return
        }
        networkSpeechJob = viewModel.speakNetworkSpeech(text,
            onPreparing = { if (speechPlaybackSession == playbackSession) speechPreparingMessageId = utteranceId },
            onPlaying = { if (speechPlaybackSession == playbackSession) speechPreparingMessageId = null },
            onResult = { result ->
                if (speechPlaybackSession == playbackSession) {
                    speakingMessageId = null
                    speechPreparingMessageId = null
                    result.onFailure { scope.launch { snackbarHostState.showSnackbar("朗读失败：${it.message ?: "未知错误"}") } }
                }
            })
    }

    uiState.pendingToolApproval?.let { request ->
        AlertDialog(
            onDismissRequest = {
                viewModel.resolveToolApproval(request.id, ToolApprovalDecision.DENY)
            },
            title = { Text("允许工具执行？") },
            text = {
                Column {
                    Text("${request.serverName} · ${request.toolName}", fontWeight = FontWeight.Medium)
                    request.description?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text("参数", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        request.argumentsJson.take(1_500),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resolveToolApproval(request.id, ToolApprovalDecision.ALLOW_ONCE)
                }) { Text("仅本次允许") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.resolveToolApproval(request.id, ToolApprovalDecision.DENY)
                    }) { Text("拒绝") }
                    TextButton(onClick = {
                        viewModel.resolveToolApproval(request.id, ToolApprovalDecision.ALWAYS_ALLOW)
                    }) { Text("始终允许") }
                }
            },
        )
    }

    movingConversation?.let { conversation ->
        MoveConversationDialog(
            conversationTitle = conversation.title,
            spaces = uiState.spaces,
            selectedSpaceId = conversation.spaceId,
            onDismiss = { movingConversation = null },
            onMove = { spaceId ->
                viewModel.moveConversationToSpace(conversation.id, spaceId)
                movingConversation = null
                scope.launch {
                    val destination = uiState.spaces.firstOrNull { it.id == spaceId }?.name ?: "未加入项目"
                    snackbarHostState.showSnackbar("已移动到：$destination")
                }
            },
        )
    }

    fun writeExport(uri: android.net.Uri?) {
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w").use { output ->
                        requireNotNull(output) { "无法创建导出文件" }
                        output.writer(Charsets.UTF_8).use { it.write(export.content) }
                    }
                }
            }.onSuccess {
                snackbarHostState.showSnackbar("对话已导出")
            }.onFailure {
                snackbarHostState.showSnackbar("导出文件写入失败")
            }
        }
    }

    val markdownExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> writeExport(uri) }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> writeExport(uri) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val password = pendingLocalBackupPassword
        pendingLocalBackupPassword = null
        uri ?: return@rememberLauncherForActivityResult
        val output = runCatching { context.contentResolver.openOutputStream(uri, "w") }.getOrNull()
        if (output == null) {
            scope.launch { snackbarHostState.showSnackbar("无法创建备份文件") }
        } else {
            viewModel.createBackup(output, password) { result ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        result.fold(
                            onSuccess = { "备份完成：${it.entryCount} 个数据项" },
                            onFailure = { "备份失败：${it.message ?: "未知错误"}" },
                        ),
                    )
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> selectedRestoreUri = uri }
    val providerExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingProviderExport
        pendingProviderExport = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w").use { output ->
                        requireNotNull(output) { "无法创建配置文件" }
                        output.writer(Charsets.UTF_8).use { it.write(content) }
                    }
                }
            }.onSuccess { snackbarHostState.showSnackbar("Provider 配置已导出，不包含 API Key") }
                .onFailure { snackbarHostState.showSnackbar("Provider 配置导出失败") }
        }
    }
    val providerImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val content = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取配置文件" }
                        val collected = ByteArrayOutputStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(collected.size() + count <= MAX_PROVIDER_TRANSFER_BYTES) {
                                "Provider 配置文件不能超过 1 MB"
                            }
                            collected.write(buffer, 0, count)
                        }
                        collected.toString(Charsets.UTF_8.name())
                    }
                }
            }.getOrElse {
                snackbarHostState.showSnackbar(it.message ?: "Provider 配置读取失败")
                return@launch
            }
            viewModel.importProviderConfiguration(content) { result ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        result.fold(
                            onSuccess = { "已导入 $it 个 Provider，请为其配置 API Key" },
                            onFailure = { "导入失败：${it.message ?: "配置无效"}" },
                        ),
                    )
                }
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) scope.launch { snackbarHostState.showSnackbar("未授予通知权限，后台完成通知不会显示") }
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingVoicePermissionAction
        pendingVoicePermissionAction = null
        if (granted) action?.invoke()
        else scope.launch { snackbarHostState.showSnackbar("需要麦克风权限才能使用语音输入") }
    }
    val oauthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val server = pendingOAuthServer ?: return@rememberLauncherForActivityResult
        pendingOAuthServer = null
        viewModel.finishMcpOAuth(server, result.data) { success ->
            scope.launch { snackbarHostState.showSnackbar(if (success) "OAuth 授权成功" else "OAuth 授权未完成") }
        }
    }

    LaunchedEffect(uiState.isGenerating, uiState.displaySettings.completionNotification) {
        if (uiState.isGenerating) {
            completionPending = true
            if (latestUiState.displaySettings.completionNotification) {
                runCatching { GenerationForegroundService.start(context) }
            } else {
                GenerationForegroundService.stop(context)
            }
        } else if (completionPending) {
            GenerationForegroundService.stop(context)
            completionPending = false
            delay(150)
            val reply = latestUiState.messages.lastOrNull { it.role == MessageRole.AI && it.status == MessageStatus.COMPLETE }
            if (latestUiState.displaySettings.completionVibration) {
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            if (reply != null && latestUiState.displaySettings.autoReadReplies) {
                speakText(reply.translation ?: reply.text, reply.id.value)
            }
            if (latestUiState.displaySettings.completionNotification &&
                (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            ) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel("generation", "生成完成", NotificationManager.IMPORTANCE_DEFAULT),
                )
                manager.notify(
                    1001,
                    android.app.Notification.Builder(context, "generation")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("MutCube 已完成回复")
                        .setContentText("点击返回应用查看回复")
                        .setContentIntent(
                            PendingIntent.getActivity(
                                context,
                                1,
                                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            ),
                        )
                        .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
    }

    fun importUris(uris: List<android.net.Uri>) {
        val available = (AttachmentStore.MAX_ATTACHMENT_COUNT - pendingAttachments.size).coerceAtLeast(0)
        if (available == 0) {
            scope.launch { snackbarHostState.showSnackbar("每条消息最多添加 8 个附件") }
            return
        }
        scope.launch {
            uris.take(available).forEach { uri ->
                runCatching { attachmentStore.import(uri) }
                    .onSuccess { pendingAttachments = pendingAttachments + it }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "附件读取失败") }
            }
        }
    }

    fun updateComposer(nextValue: String) {
        val looksLikeLongPaste = nextValue.length - message.length >= LONG_PASTE_CHARACTER_THRESHOLD
        if (!looksLikeLongPaste) {
            message = nextValue
            return
        }
        if (pendingAttachments.size >= AttachmentStore.MAX_ATTACHMENT_COUNT) {
            message = nextValue
            scope.launch { snackbarHostState.showSnackbar("附件已满，长文本保留在输入框中") }
            return
        }
        scope.launch {
            runCatching { attachmentStore.saveText(nextValue) }
                .onSuccess {
                    pendingAttachments = pendingAttachments + it
                    message = ""
                    snackbarHostState.showSnackbar("长文本已转为附件，可在发送前移除")
                }
                .onFailure {
                    message = nextValue
                    snackbarHostState.showSnackbar(it.message ?: "长文本转附件失败")
                }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AttachmentStore.MAX_ATTACHMENT_COUNT),
    ) { uris -> importUris(uris) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importUris(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        if (pendingAttachments.size >= AttachmentStore.MAX_ATTACHMENT_COUNT) {
            scope.launch { snackbarHostState.showSnackbar("每条消息最多添加 8 个附件") }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { attachmentStore.saveCameraImage(bitmap) }
                .onSuccess { pendingAttachments = pendingAttachments + it }
                .onFailure { snackbarHostState.showSnackbar(it.message ?: "照片保存失败") }
        }
    }
    fun transcribeRecording(file: File) {
        val sessionId = voiceSessionId
        voiceTranscribing = true
        voiceTranscriptionError = null
        voiceAmplitude = 0f
        viewModel.transcribeSpeech(file, latestUiState.displaySettings.asrLanguageTag) { result ->
            if (sessionId != voiceSessionId) {
                file.delete()
                return@transcribeSpeech
            }
            voiceTranscribing = false
            result.onSuccess {
                file.delete()
                retainedVoiceRecording = null
                applyRecognizedText(it)
            }.onFailure {
                voiceTranscriptionError = "转写失败：${it.message ?: "未知错误"}"
            }
        }
    }

    voiceTranscriptionError?.let { error ->
        AlertDialog(
            onDismissRequest = {
                retainedVoiceRecording?.delete()
                retainedVoiceRecording = null
                voiceTranscriptionError = null
            },
            title = { Text("语音转写失败") },
            text = { Text("$error\n完整录音暂存于本机，可重试；成功或丢弃后清除。") },
            confirmButton = {
                TextButton(onClick = { retainedVoiceRecording?.let(::transcribeRecording) }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = {
                    retainedVoiceRecording?.delete()
                    retainedVoiceRecording = null
                    voiceTranscriptionError = null
                }) { Text("丢弃录音") }
            },
        )
    }

    fun finishNetworkRecording() {
        val active = networkRecorder ?: return
        networkRecorder = null
        runCatching { active.stop() }
            .onSuccess { file ->
                retainedVoiceRecording = file
                transcribeRecording(file)
            }
            .onFailure {
                voiceTranscribing = false
                scope.launch { snackbarHostState.showSnackbar("录音保存失败，请重试") }
            }
    }

    fun beginNetworkRecording() {
        retainedVoiceRecording?.delete()
        retainedVoiceRecording = null
        voiceTranscriptionError = null
        runCatching { ContinuousSpeechRecorder.start(context) }
            .onSuccess {
                voiceSessionId += 1
                voiceTranscribing = false
                networkRecorder = it
                voiceStartedAt = SystemClock.elapsedRealtime()
                voiceDurationSeconds = 0
            }
            .onFailure { error -> scope.launch { snackbarHostState.showSnackbar("无法开始录音：${error.message}") } }
    }


    fun startVoiceInput() {
        if (systemVoiceActive) {
            voiceTranscribing = true
            systemSpeechInput.stop()
            return
        }
        if (networkRecorder != null) {
            finishNetworkRecording()
            return
        }
        val action: () -> Unit = {
            if (latestUiState.speechServiceSettings.asrProvider == SpeechProvider.SYSTEM) {
                voiceSessionId += 1
                systemVoiceActive = true
                voiceTranscribing = false
                voiceStartedAt = SystemClock.elapsedRealtime()
                systemSpeechInput.start(
                    latestUiState.displaySettings.asrLanguageTag.ifBlank { Locale.getDefault().toLanguageTag() },
                    latestUiState.speechServiceSettings.asrSilenceSeconds * 1_000L,
                    onLevel = { voiceAmplitude = it },
                    onProcessing = { voiceTranscribing = true },
                    onResult = {
                        systemVoiceActive = false
                        voiceTranscribing = false
                        voiceAmplitude = 0f
                        applyRecognizedText(it)
                    },
                    onError = {
                        systemVoiceActive = false
                        voiceTranscribing = false
                        voiceAmplitude = 0f
                        scope.launch { snackbarHostState.showSnackbar(it) }
                    },
                )
            } else beginNetworkRecording()
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingVoicePermissionAction = action
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun cancelVoiceInput() {
        systemSpeechInput.cancel()
        systemVoiceActive = false
        voiceSessionId += 1
        networkRecorder?.discard()
        networkRecorder = null
        if (!voiceTranscribing) retainedVoiceRecording?.delete()
        retainedVoiceRecording = null
        voiceTranscriptionError = null
        voiceTranscribing = false
        voiceAmplitude = 0f
        voiceDurationSeconds = 0
    }

    val voiceInputActive = networkRecorder != null || (systemVoiceActive && !voiceTranscribing)
    LaunchedEffect(voiceInputActive, networkRecorder) {
        val pauseDetector = SpeechPauseDetector(latestUiState.speechServiceSettings.asrSilenceSeconds * 1_000L)
        while (voiceInputActive) {
            if (systemVoiceActive && SystemClock.elapsedRealtime() - voiceStartedAt >= 180_000) {
                voiceTranscribing = true
                systemSpeechInput.stop()
                return@LaunchedEffect
            }
            voiceDurationSeconds = ((SystemClock.elapsedRealtime() - voiceStartedAt) / 1_000L).toInt().coerceAtLeast(0)
            networkRecorder?.let { recorder ->
                val healthy = runCatching { recorder.checkHealthy() }
                if (healthy.isFailure) {
                    networkRecorder = null
                    recorder.discard()
                    scope.launch { snackbarHostState.showSnackbar(healthy.exceptionOrNull()?.message ?: "录音失败") }
                    return@LaunchedEffect
                }
                voiceAmplitude = recorder.amplitude
                if (voiceDurationSeconds >= MAX_NETWORK_RECORDING_SECONDS ||
                    pauseDetector.shouldFinish(SystemClock.elapsedRealtime(), recorder.decibels)) {
                    if (!pauseDetector.hasSpeech) {
                        cancelVoiceInput()
                        scope.launch { snackbarHostState.showSnackbar("没有检测到语音，请重试") }
                        return@LaunchedEffect
                    }
                    finishNetworkRecording()
                    return@LaunchedEffect
                }
            }
            delay(100)
        }
    }

    LaunchedEffect(uiState.selectedConversationId) {
        if (networkRecorder != null || systemVoiceActive || voiceTranscribing || retainedVoiceRecording != null) cancelVoiceInput()
        if (skipNextDraftClear) {
            skipNextDraftClear = false
        } else {
            viewModel.discardDraftAttachments(pendingAttachments)
            message = ""
            pendingAttachments = emptyList()
        }
    }

    LaunchedEffect(incomingShare?.id) {
        val share = incomingShare ?: return@LaunchedEffect
        viewModel.discardDraftAttachments(pendingAttachments)
        pendingAttachments = emptyList()
        message = ""
        skipNextDraftClear = latestUiState.selectedConversationId != null
        viewModel.startNewConversation()
        message = share.text
        importUris(share.uris)
        onIncomingShareConsumed()
        snackbarHostState.showSnackbar("分享内容已放入新对话，请确认后发送")
    }

    LaunchedEffect(incomingShortcut?.id) {
        val shortcut = incomingShortcut ?: return@LaunchedEffect
        val onMissing: () -> Unit = {
            scope.launch { snackbarHostState.showSnackbar("快捷方式对应的内容已不存在") }
        }
        when (shortcut.kind) {
            SHORTCUT_KIND_CONVERSATION -> viewModel.openConversationShortcut(
                com.dwl.mutcube.core.model.ConversationId(shortcut.targetId), onMissing,
            )
            SHORTCUT_KIND_SPACE -> viewModel.openSpaceShortcut(
                com.dwl.mutcube.core.model.SpaceId(shortcut.targetId), onMissing,
            )
            else -> onMissing()
        }
        onIncomingShortcutConsumed()
    }

    fun requestDesktopShortcut(kind: String, targetId: String, title: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            scope.launch { snackbarHostState.showSnackbar("当前系统不支持固定桌面快捷方式") }
            return
        }
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) {
            scope.launch { snackbarHostState.showSnackbar("当前桌面不支持固定快捷方式") }
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SHORTCUT_KIND, kind)
            putExtra(EXTRA_SHORTCUT_TARGET, targetId)
        }
        val shortcut = ShortcutInfo.Builder(context, "$kind-$targetId")
            .setShortLabel(title.take(10))
            .setLongLabel(title.take(30))
            .setIcon(Icon.createWithResource(context, android.R.drawable.ic_dialog_email))
            .setIntent(intent)
            .build()
        manager.requestPinShortcut(shortcut, null)
    }

    fun closeSettingsPage() {
        settingsStack = settingsStack.dropLast(1)
    }

    if (showTemplatePackages) {
        BackHandler { showTemplatePackages = false }
        com.dwl.mutcube.ui.components.TemplatePackageManagerPage(
            templateContainer.installedTemplateStore,
            templateContainer.templateRepository,
            uiState.spaces,
            onBack = { showTemplatePackages = false },
        )
        return
    }
    if (showTemplateData) {
        BackHandler { showTemplateData = false }
        Surface(color = MaterialTheme.colorScheme.background) {
            com.dwl.mutcube.ui.components.UserTemplateDataPage(
                templateContainer.templateRepository, uiState.spaces,
                onBack = { showTemplateData = false },
            )
        }
        return
    }
    choosingBoundTemplateProjectId?.let { projectId ->
        AlertDialog(
            onDismissRequest = { choosingBoundTemplateProjectId = null },
            title = { Text("选择项目模板") },
            text = {
                Column {
                    templateDefinitions.forEach { definition ->
                        val binding = templateBindings.firstOrNull { it.enabled && it.projectId == projectId && it.templateId == definition.id }
                        if (binding != null) TextButton(onClick = {
                            selectedTemplateId = definition.id
                            choosingBoundTemplateProjectId = null
                            if (binding.version == definition.version && templateBindings.count { it.enabled && it.projectId == projectId } == 1) templateProjectId = projectId
                            else choosingTemplateProject = true
                        }) { Text(definition.name + if (definition.id == selectedTemplateId) " · 最近使用" else "") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingBoundTemplateProjectId = null }) { Text("取消") } },
        )
    }
    if (choosingExistingTemplateProject) {
        val projects = templateEntryProjects(templateBindings, selectedTemplate.id, uiState.spaces.map { it.id.value }.toSet(), null)
        AlertDialog(onDismissRequest = { choosingExistingTemplateProject = false }, title = { Text("选择项目") },
            text = { Column {
                Text("此模板已绑定多个项目，选择要打开的项目，无需再次绑定。")
                uiState.spaces.filter { it.id.value in projects }.forEach { project ->
                    TextButton(onClick = {
                        choosingExistingTemplateProject = false
                        val bound = templateBindings.first { it.enabled && it.templateId == selectedTemplate.id && it.projectId == project.id.value }
                        if (bound.version == selectedTemplate.version) {
                            templateLaunchTarget = null
                            templateProjectId = project.id.value
                        } else choosingTemplateProject = true
                    }) { Text(project.name) }
                }
            } }, confirmButton = { TextButton(onClick = { choosingExistingTemplateProject = false }) { Text("取消") } })
    }
    if (choosingTemplateProject) {
        var showTemplatePermissions by remember { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { if (!templateAuthorizationBusy) choosingTemplateProject = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("选择使用项目", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "选择项目后即可使用${selectedTemplate.name}。每个项目只能启用一个模板；替换时会撤销旧模板权限，但不会删除它的数据。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                if (uiState.spaces.isEmpty()) Text("还没有项目，请先创建一个项目。")
                uiState.spaces.forEach { project ->
                    val current = templateBindings.firstOrNull { it.enabled && it.projectId == project.id.value }
                    val subtitle = when {
                        current?.templateId == selectedTemplate.id -> "已启用 · 点击打开"
                        current != null -> "将替换此项目的现有模板"
                        else -> "点击启用"
                    }
                    Surface(
                        onClick = {
                            val existingBinding = templateBindings.firstOrNull {
                                it.enabled && it.projectId == project.id.value && it.templateId == selectedTemplate.id && it.version == selectedTemplate.version
                            }
                            if (externalTemplates.any { it.definition.manifest.id == selectedTemplate.id } &&
                                (existingBinding == null || templateAuthorizationRequired(existingBinding, templatePermissions))) {
                                choosingTemplateProject = false
                                pendingExternalTemplateProject = project
                                return@Surface
                            }
                            templateAuthorizationBusy = true
                            scope.launch {
                                try {
                                    if (existingBinding == null || templateAuthorizationRequired(existingBinding, templatePermissions)) {
                                        templateContainer.templateRuntime.enable(selectedTemplate, project.id)
                                    }
                                    templateProjectId = project.id.value
                                    choosingTemplateProject = false
                                } catch (error: Exception) {
                                    snackbarHostState.showSnackbar("启用失败：${error.message ?: "请检查项目状态"}")
                                } finally { templateAuthorizationBusy = false }
                            }
                        },
                        enabled = !templateAuthorizationBusy,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(project.name, fontWeight = FontWeight.Medium)
                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
                TextButton(onClick = { showTemplatePermissions = !showTemplatePermissions }) {
                    Text(if (showTemplatePermissions) "收起数据权限" else "查看数据权限")
                }
                if (showTemplatePermissions) {
                    Text("聊天只能使用模板开放的查询与生成功能，不能直接修改或删除记录。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    selectedTemplate.collections.forEach { collection ->
                        val description = when (collection.policy) {
                            com.dwl.mutcube.template.core.CollectionPolicy.MUTABLE -> "允许通过模板新增、修改和删除"
                            com.dwl.mutcube.template.core.CollectionPolicy.IMMUTABLE_HISTORY -> "历史只追加，不允许覆盖或删除"
                            com.dwl.mutcube.template.core.CollectionPolicy.VERSIONED -> "保留所有候选，确认后启用"
                        }
                        Text("${collection.id}：$description", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    pendingExternalTemplateProject?.let { project ->
        val pkg = externalTemplates.firstOrNull { it.definition.manifest.id == selectedTemplate.id }?.definition
        AlertDialog(onDismissRequest = { if (!templateAuthorizationBusy) pendingExternalTemplateProject = null },
            title = { Text("授权 ${selectedTemplate.name} 使用「${project.name}」？") },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("第三方模板 · ${pkg?.developer?.name ?: "未知开发者"} · v${selectedTemplate.version}。每个项目只能启用一个模板。")
                Text("声明的数据集合：${selectedTemplate.collections.joinToString("、") { it.id }}")
                pkg?.permissions?.sorted()?.forEach { permission -> Text("${if (com.dwl.mutcube.template.core.TemplateHostCapabilities.supported[permission] == com.dwl.mutcube.template.core.TemplateCapabilityTier.SENSITIVE) "敏感" else "基础"} · ${com.dwl.mutcube.ui.components.permissionLabel(permission)}") }
                if (pkg?.networkDomains?.isNotEmpty() == true) Text("允许网络域名：${pkg.networkDomains.joinToString("、")}")
                Text("授权只对当前项目有效，解绑后立即撤销。替换原模板不删除旧数据。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { TextButton(enabled = !templateAuthorizationBusy, onClick = { scope.launch {
                templateAuthorizationBusy = true
                try {
                    templateContainer.templateRuntime.enable(selectedTemplate, project.id)
                    templateProjectId = project.id.value
                    pendingExternalTemplateProject = null
                } catch (failure: Exception) {
                    snackbarHostState.showSnackbar("授权失败：${failure.message ?: "请检查模板包与项目"}")
                } finally { templateAuthorizationBusy = false }
            } }) { Text("确认授权并打开") } },
            dismissButton = { TextButton(enabled = !templateAuthorizationBusy, onClick = { pendingExternalTemplateProject = null }) { Text("取消") } })
    }
    val templateProject = templateProjectId?.let { id -> uiState.spaces.firstOrNull { it.id.value == id } }
    val activeTemplateBinding = templateProject?.let { project ->
        templateBindings.firstOrNull { it.enabled && it.projectId == project.id.value && it.templateId == selectedTemplate.id }
    }
    val requiresTemplateAuthorization = templateAuthorizationRequired(activeTemplateBinding, templatePermissions) ||
        activeTemplateBinding?.version != selectedTemplate.version
    if (templateProject != null) {
        BackHandler(enabled = !templateAuthorizationBusy) { templateProjectId = null; templateLaunchTarget = null }
        if (requiresTemplateAuthorization) {
            AlertDialog(
                onDismissRequest = { if (!templateAuthorizationBusy) { templateProjectId = null; templateLaunchTarget = null } },
                title = { Text("重新授权${selectedTemplate.name}模板") },
                text = { Text("“${templateProject.name}”的模板绑定和记录已恢复，但恢复备份时撤销了模板的数据访问权限。确认后会重新授予此模板声明的能力，已有记录不会被清除；取消则暂不打开模板。") },
                confirmButton = {
                    TextButton(enabled = !templateAuthorizationBusy, onClick = {
                        templateAuthorizationBusy = true
                        scope.launch {
                            try {
                                templateContainer.templateRuntime.enable(selectedTemplate, templateProject.id)
                            } catch (error: Exception) {
                                snackbarHostState.showSnackbar("重新授权失败：${error.message ?: "请检查模板版本和项目状态"}")
                            } finally { templateAuthorizationBusy = false }
                        }
                    }) { Text(if (templateAuthorizationBusy) "授权中…" else "重新授权并打开") }
                },
                dismissButton = { TextButton(enabled = !templateAuthorizationBusy, onClick = {
                    templateProjectId = null; templateLaunchTarget = null
                }) { Text("取消") } },
            )
        } else Surface(color = MaterialTheme.colorScheme.background) {
            com.dwl.mutcube.template.ui.TemplateRuntimePage(
                selectedTemplate, templateProject,
                templateContainer.templateRepository, templateContainer.templateRuntime,
                templateContainer.templateActions.engine,
                trustedGuiVersionSelection = templateContainer.builtinTemplates.any { it.id == selectedTemplate.id },
                initialTarget = templateLaunchTarget,
                installedResource = if (externalTemplates.any { it.definition.manifest.id == selectedTemplate.id })
                    ({ path -> templateContainer.installedTemplateStore.resource(selectedTemplate.id, path) }) else null,
                hostPermissions = externalTemplates.firstOrNull { it.definition.manifest.id == selectedTemplate.id }
                    ?.definition?.permissions.orEmpty(),
                networkDomains = externalTemplates.firstOrNull { it.definition.manifest.id == selectedTemplate.id }
                    ?.definition?.networkDomains.orEmpty(),
                speechHost = remember(selectedTemplate.id, templateProject.id) {
                    com.dwl.mutcube.speech.AppTemplateSpeechHost(context.applicationContext,
                        templateContainer.speechServiceSettingsStore, templateContainer.displaySettingsStore,
                        templateContainer.speechService)
                },
                onBack = { templateProjectId = null; templateLaunchTarget = null },
                onOpenChat = {
                    templateProjectId = null
                    templateLaunchTarget = null
                    openedProjectId = null
                    conversationOriginProjectId = null
                    viewModel.selectSpace(templateProject.id)
                    if (uiState.conversations.none { it.id == uiState.selectedConversationId && it.spaceId == templateProject.id }) {
                        viewModel.startNewConversation()
                    }
                    viewModel.selectDestination(RootDestination.CHAT)
                },
            )
        }
        return
    }
    if (showTranslationPage) {
        BackHandler { showTranslationPage = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TranslationPage(
                    onBack = { showTranslationPage = false },
                    onTranslate = viewModel::translateStandalone,
                    onCopy = { translated ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MutCube 译文", translated))
                        scope.launch { snackbarHostState.showSnackbar("译文已复制") }
                    },
                )
                MutCubeNoticeHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        return
    }

    if (libraryDestination != null) {
        fun closeLibrary() {
            viewModel.setConversationSearchQuery("")
            libraryDestination = null
        }
        BackHandler { closeLibrary() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                when (libraryDestination) {
                    LibraryDestination.ALL_CHATS -> AllChatsPage(
                        conversations = uiState.conversations,
                        searchResults = uiState.conversationSearchResults,
                        spaces = uiState.spaces,
                        onSearchQueryChange = viewModel::setConversationSearchQuery,
                        onBack = ::closeLibrary,
                        onConversationClick = { conversation ->
                            conversationOriginProjectId = null
                            openedProjectId = null
                            viewModel.selectConversation(conversation.id)
                            closeLibrary()
                        },
                        onRename = { conversation ->
                            renamingConversation = conversation
                            renameTitle = conversation.title
                        },
                        onPin = { conversation, pinned -> viewModel.setConversationPinned(conversation.id, pinned) },
                        onMove = { movingConversation = it },
                        onDelete = { conversation ->
                            viewModel.trashConversation(conversation.id) {
                                scope.launch {
                                    if (snackbarHostState.showSnackbar("对话已删除", "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreConversation(conversation.id)
                                    }
                                }
                            }
                        },
                        onOpenTrash = {
                            closeLibrary()
                            settingsStack = listOf(SettingsDestination.TRASH)
                        },
                        onNewChat = {
                            openedProjectId = null
                            conversationOriginProjectId = null
                            viewModel.selectSpace(null)
                            viewModel.startNewConversation()
                            closeLibrary()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    LibraryDestination.FAVORITES -> FavoritesPage(
                        favorites = uiState.favoriteMessages,
                        onBack = ::closeLibrary,
                        onOpenConversation = { favorite ->
                            conversationOriginProjectId = null
                            openedProjectId = null
                            viewModel.selectConversation(favorite.conversationId)
                            closeLibrary()
                        },
                        onRemove = { favorite -> viewModel.setMessageFavorite(favorite.messageId, false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    null -> Unit
                }
                renamingConversation?.let { conversation ->
                    RenameConversationDialog(
                        title = renameTitle,
                        onTitleChange = { renameTitle = it },
                        onDismiss = { renamingConversation = null },
                        onConfirm = {
                            viewModel.renameConversation(conversation.id, renameTitle)
                            renamingConversation = null
                        },
                    )
                }
                MutCubeNoticeHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
        return
    }

    if (showCreateSpacePage) {
        BackHandler { showCreateSpacePage = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NewProjectPage(
                providers = uiState.providerConfiguration,
                configuredProviderIds = uiState.configuredProviderIds,
                onBack = { showCreateSpacePage = false },
                onCreate = { name, profileId, overrides ->
                    viewModel.createSpace(name) { id ->
                        viewModel.updateSpaceModelSettings(id, profileId, overrides)
                        openedProjectId = id
                    }
                    showCreateSpacePage = false
                    conversationOriginProjectId = null
                    scope.launch { snackbarHostState.showSnackbar("项目已创建") }
                },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            )
        }
        return
    }

    val activeConfiguringSpace = configuringSpace?.id?.let { id -> uiState.spaces.firstOrNull { it.id == id } }
    if (activeConfiguringSpace != null) {
        BackHandler { configuringSpace = null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ProjectSettingsPage(
                space = activeConfiguringSpace,
                globalSettings = uiState.modelSettings,
                providers = uiState.providerConfiguration,
                configuredProviderIds = uiState.configuredProviderIds,
                onBack = { configuringSpace = null },
                onSave = { name, profileId, overrides ->
                    if (name != activeConfiguringSpace.name) {
                        viewModel.renameSpace(activeConfiguringSpace.id, name)
                    }
                    viewModel.updateSpaceModelSettings(activeConfiguringSpace.id, profileId, overrides)
                    configuringSpace = null
                    scope.launch { snackbarHostState.showSnackbar("项目设置已保存") }
                },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            )
        }
        return
    }

    val configuringConversation = uiState.conversations.firstOrNull { it.id == uiState.selectedConversationId }
    if (showConversationSpaceDialog && configuringConversation != null) {
        BackHandler { showConversationSpaceDialog = false }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ConversationSettingsPage(
                conversation = configuringConversation,
                spaces = uiState.spaces,
                onBack = { showConversationSpaceDialog = false },
                onSave = { title, spaceId, systemPrompt ->
                    if (title != configuringConversation.title) {
                        viewModel.renameConversation(configuringConversation.id, title)
                    }
                    viewModel.updateSelectedConversationSettings(spaceId, systemPrompt)
                    showConversationSpaceDialog = false
                    scope.launch { snackbarHostState.showSnackbar("对话设置已保存") }
                },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            )
        }
        return
    }

    val settingsDestination = settingsStack.lastOrNull()
    BackHandler(enabled = settingsDestination != null, onBack = ::closeSettingsPage)
    if (settingsDestination != null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                SettingsDestinationContent(
                    destination = settingsDestination, uiState = uiState, viewModel = viewModel,
                    onBack = ::closeSettingsPage,
                    onNavigate = { settingsStack = settingsStack + it },
                    actions = SettingsHostActions(
                        onResetMemoryScope = { memoryInitialProjectId = null },
                        onImportProviders = { providerImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onExportProviders = {
                            viewModel.exportProviderConfiguration { content ->
                                pendingProviderExport = content
                                providerExportLauncher.launch("mutcube-providers.json")
                            }
                        },
                        onTemplateData = { showTemplateData = true },
                        onCreateBackup = { showBackupPasswordPrompt = true },
                        onRestoreBackup = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        onNotificationPermission = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRemoteRestore = { password ->
                            pendingRemotePassword = password
                            viewModel.inspectRemoteBackup(password) { result ->
                                result.onSuccess { preview ->
                                    pendingRemotePreview = preview
                                    pendingRemoteRestore = true
                                }.onFailure { failure ->
                                    pendingRemotePassword = null
                                    scope.launch { snackbarHostState.showSnackbar("远程备份校验失败：${failure.message ?: "未知错误"}") }
                                }
                            }
                        },
                        onAuthorize = { server ->
                            pendingOAuthServer = server
                            viewModel.beginMcpOAuth(server, oauthLauncher::launch) {
                                pendingOAuthServer = null
                                scope.launch { snackbarHostState.showSnackbar("无法启动 OAuth 授权") }
                            }
                        },
                    ),
                    settingsStateHolder = settingsStateHolder, memoryInitialProjectId = memoryInitialProjectId,
                    installedSkills = installedSkills, skillAudit = skillAudit,
                    engines = if (textToSpeechReady) textToSpeech.engines.map { it.name to it.label.toString() } else emptyList(),
                    voices = if (textToSpeechReady) textToSpeech.voices.orEmpty().sortedBy { it.locale.displayName }
                        .map { it.name to "${it.locale.displayName} · ${it.name}" } else emptyList(),
                    templateBindings = templateBindings, templateDefinitions = templateDefinitions,
                    templateRepository = templateContainer.templateRepository,
                    onNotice = { notice -> scope.launch { snackbarHostState.showSnackbar(notice) } },
                )
                if (showBackupPasswordPrompt) {
                    AlertDialog(
                        onDismissRequest = { showBackupPasswordPrompt = false; backupPasswordDraft = ""; backupPasswordConfirm = "" },
                        title = { Text("创建本地备份") },
                        text = {
                            Column {
                                Text("可设置至少 12 个字符的备份密码。留空则生成未加密 ZIP，切勿存放在不可信位置。密码不会保存在设备，丢失后无法恢复加密备份。")
                                OutlinedTextField(backupPasswordDraft, { backupPasswordDraft = it }, label = { Text("备份密码（可留空）") },
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                                if (backupPasswordDraft.isNotBlank()) OutlinedTextField(backupPasswordConfirm, { backupPasswordConfirm = it },
                                    label = { Text("再次输入密码") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = backupPasswordDraft.isEmpty() ||
                                (backupPasswordDraft.length >= 12 && backupPasswordDraft == backupPasswordConfirm), onClick = {
                                pendingLocalBackupPassword = backupPasswordDraft.ifBlank { null }
                                backupPasswordDraft = ""
                                backupPasswordConfirm = ""
                                showBackupPasswordPrompt = false
                                val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                                backupLauncher.launch("mutcube-backup-$date.${if (pendingLocalBackupPassword == null) "zip" else "mutcube"}")
                            }) { Text("选择保存位置") }
                        },
                        dismissButton = { TextButton(onClick = { showBackupPasswordPrompt = false; backupPasswordDraft = ""; backupPasswordConfirm = "" }) { Text("取消") } },
                    )
                }
                selectedRestoreUri?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { selectedRestoreUri = null; restorePasswordDraft = "" },
                        title = { Text("检查备份") },
                        text = {
                            Column {
                                Text("恢复前会检查文件完整性与内容。加密备份请输入创建时的密码；旧版未加密备份可留空。")
                                OutlinedTextField(restorePasswordDraft, { restorePasswordDraft = it }, label = { Text("备份密码（如有）") },
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
                            }
                        },
                        confirmButton = {
                            TextButton(enabled = !restoreInspecting, onClick = {
                                val password = restorePasswordDraft.ifBlank { null }
                                val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                                if (input == null) scope.launch { snackbarHostState.showSnackbar("无法读取备份文件") }
                                else {
                                    restoreInspecting = true
                                    viewModel.inspectBackup(input, password) { result ->
                                        restoreInspecting = false
                                        result.onSuccess { preview ->
                                            if (selectedRestoreUri == uri) {
                                                pendingRestorePreview = preview
                                                pendingRestorePassword = password
                                                pendingRestoreUri = uri
                                                selectedRestoreUri = null
                                                restorePasswordDraft = ""
                                            }
                                        }.onFailure { failure ->
                                            scope.launch { snackbarHostState.showSnackbar("备份校验失败：${failure.message ?: "未知错误"}") }
                                        }
                                    }
                                }
                            }) { Text(if (restoreInspecting) "检查中…" else "检查备份") }
                        },
                        dismissButton = { TextButton(onClick = { selectedRestoreUri = null; restorePasswordDraft = "" }) { Text("取消") } },
                    )
                }
                pendingRestoreUri?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { pendingRestoreUri = null; pendingRestorePreview = null; pendingRestorePassword = null },
                        title = { Text("恢复完整备份？") },
                        text = { Text(pendingRestorePreview?.let(::backupPreviewText) ?: "正在检查备份…") },
                        confirmButton = {
                            TextButton(onClick = {
                                val password = pendingRestorePassword
                                pendingRestoreUri = null
                                pendingRestorePreview = null
                                pendingRestorePassword = null
                                val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
                                if (input == null) {
                                    scope.launch { snackbarHostState.showSnackbar("无法读取备份文件") }
                                } else {
                                    viewModel.restoreBackup(input, password) { result ->
                                        result.onSuccess {
                                            (context as? Activity)?.finishAffinity()
                                            android.os.Process.killProcess(android.os.Process.myPid())
                                        }.onFailure {
                                            if (it is com.dwl.mutcube.storage.BackupRestoreRequiresRestartException) {
                                                backupRestartRequiredMessage = it.message
                                            } else scope.launch {
                                                snackbarHostState.showSnackbar("恢复失败：${it.message ?: "未知错误"}")
                                            }
                                        }
                                    }
                                }
                            }) { Text("恢复并关闭") }
                        },
                        dismissButton = { TextButton(onClick = { pendingRestoreUri = null; pendingRestorePreview = null; pendingRestorePassword = null }) { Text("取消") } },
                    )
                }
                if (pendingRemoteRestore) {
                    AlertDialog(
                        onDismissRequest = { pendingRemoteRestore = false; pendingRemotePreview = null; pendingRemotePassword = null },
                        title = { Text("从远程备份恢复？") },
                        text = { Text(pendingRemotePreview?.let(::backupPreviewText) ?: "正在检查远程备份…") },
                        confirmButton = {
                            TextButton(onClick = {
                                val password = pendingRemotePassword
                                pendingRemoteRestore = false
                                pendingRemotePreview = null
                                pendingRemotePassword = null
                                viewModel.restoreRemoteBackup(password) { result ->
                                    result.onSuccess {
                                        (context as? Activity)?.finishAffinity()
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                    }.onFailure {
                                        if (it is com.dwl.mutcube.storage.BackupRestoreRequiresRestartException) {
                                            backupRestartRequiredMessage = it.message
                                        } else scope.launch { snackbarHostState.showSnackbar("恢复失败：${it.message ?: "未知错误"}") }
                                    }
                                }
                            }) { Text("恢复并关闭") }
                        },
                        dismissButton = { TextButton(onClick = { pendingRemoteRestore = false; pendingRemotePreview = null; pendingRemotePassword = null }) { Text("取消") } },
                    )
                }
                backupRestartRequiredMessage?.let { message ->
                    AlertDialog(onDismissRequest = {}, title = { Text("需要重新启动") },
                        text = { Text(message) }, confirmButton = {
                            TextButton(onClick = {
                                (context as? Activity)?.finishAffinity()
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }) { Text("关闭应用") }
                        })
                }
                MutCubeNoticeHost(
                    snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
        return
    }

    val openedProject = openedProjectId?.let { projectId -> uiState.spaces.firstOrNull { it.id == projectId } }
    BackHandler(enabled = openedProject != null) {
        openedProjectId = null
        viewModel.selectSpace(null)
    }
    BackHandler(
        enabled = openedProject == null && conversationOriginProjectId != null && uiState.selectedConversationId != null,
    ) {
        val origin = conversationOriginProjectId ?: return@BackHandler
        conversationOriginProjectId = null
        viewModel.selectSpace(origin)
        viewModel.startNewConversation()
        openedProjectId = origin
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MutCubeDrawer(
                userNickname = uiState.displaySettings.userDisplayName,
                userAvatarFile = uiState.displaySettings.userAvatarFile,
                onProfileClick = {
                    scope.launch { drawerState.close() }
                    settingsStack = listOf(SettingsDestination.HOME, SettingsDestination.PROFILE)
                },
                spaces = uiState.spaces,
                conversations = uiState.conversations,
                searchQuery = uiState.conversationSearchQuery,
                searchResults = uiState.conversationSearchResults,
                selectedConversationId = uiState.selectedConversationId,
                selectedSpaceId = uiState.selectedSpaceId,
                onConversationClick = { id ->
                    openedProjectId = null
                    conversationOriginProjectId = null
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onConversationRename = { conversation ->
                    renamingConversation = conversation
                    renameTitle = conversation.title
                },
                onConversationRegenerateTitle = viewModel::regenerateConversationTitle,
                onConversationPin = viewModel::setConversationPinned,
                onConversationMove = { conversation ->
                    movingConversation = conversation
                    scope.launch { drawerState.close() }
                },
                onConversationDelete = { id ->
                    viewModel.trashConversation(id) {
                        scope.launch {
                            if (
                                snackbarHostState.showSnackbar(
                                    message = "对话已删除",
                                    actionLabel = "撤销",
                                    duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                viewModel.restoreConversation(id)
                            }
                        }
                    }
                },
                onConversationExport = { exportingConversation = it },
                onSearchQueryChange = viewModel::setConversationSearchQuery,
                onAllChatsClick = {
                    openedProjectId = null
                    conversationOriginProjectId = null
                    libraryDestination = LibraryDestination.ALL_CHATS
                    scope.launch { drawerState.close() }
                },
                onSpaceClick = { spaceId ->
                    conversationOriginProjectId = null
                    viewModel.selectSpace(spaceId)
                    viewModel.startNewConversation()
                    openedProjectId = spaceId
                    scope.launch { drawerState.close() }
                },
                onSpaceRename = { space ->
                    renamingSpace = space
                    renameSpaceName = space.name
                },
                onSpacePin = viewModel::setSpacePinned,
                onSpaceDelete = viewModel::deleteSpace,
                onSpaceSettings = {
                    configuringSpace = it
                    scope.launch { drawerState.close() }
                },
                onSpaceShortcut = { space ->
                    requestDesktopShortcut(SHORTCUT_KIND_SPACE, space.id.value, space.name)
                },
                onCreateSpace = {
                    showCreateSpacePage = true
                    scope.launch { drawerState.close() }
                },
                onFavoritesClick = {
                    libraryDestination = LibraryDestination.FAVORITES
                    scope.launch { drawerState.close() }
                },
                onSettingsClick = {
                    viewModel.clearProviderInspection()
                    settingsStack = listOf(SettingsDestination.HOME)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    message = ""
                    openedProjectId = null
                    conversationOriginProjectId = null
                    viewModel.selectSpace(null)
                    viewModel.startNewConversation()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
        if (openedProject != null) {
            ProjectDetailPage(
                space = openedProject,
                conversations = uiState.conversations.filter { it.spaceId == openedProject.id },
                memoryCount = uiState.memories.count { it.spaceId == openedProject.id },
                onBack = {
                    openedProjectId = null
                    viewModel.selectSpace(null)
                },
                onOpenTemplates = {
                    val boundOptions = templateBindings.filter { it.enabled && it.projectId == openedProject.id.value && templateDefinitions.any { definition -> definition.id == it.templateId } }
                    val bound = boundOptions.firstOrNull()
                    if (bound != null) {
                        if (boundOptions.size > 1) choosingBoundTemplateProjectId = openedProject.id.value
                        else {
                            selectedTemplateId = bound.templateId
                            if (bound.version == templateDefinitions.first { it.id == bound.templateId }.version) templateProjectId = bound.projectId
                            else choosingTemplateProject = true
                        }
                    } else {
                        openedProjectId = null
                        viewModel.selectDestination(RootDestination.TEMPLATES)
                    }
                },
                onFocusComposer = { projectComposerFocusRequester.requestFocus() },
                onConversationPin = { conversation ->
                    viewModel.setConversationPinned(conversation.id, !conversation.pinned)
                },
                onConversationRename = { conversation ->
                    renamingConversation = conversation
                    renameTitle = conversation.title
                },
                onConversationRemove = { conversation ->
                    val originalSpaceId = openedProject.id
                    viewModel.moveConversationToSpace(conversation.id, null) {
                        scope.launch {
                            if (snackbarHostState.showSnackbar(
                                    "已移出项目", "撤销", duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                viewModel.moveConversationToSpace(conversation.id, originalSpaceId)
                            }
                        }
                    }
                },
                onConversationDelete = { conversation ->
                    viewModel.trashConversation(conversation.id) {
                        scope.launch {
                            if (snackbarHostState.showSnackbar(
                                    "对话已删除", "撤销", duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                viewModel.restoreConversation(conversation.id)
                            }
                        }
                    }
                },
                onConversationClick = { conversation ->
                    conversationOriginProjectId = openedProject.id
                    openedProjectId = null
                    viewModel.selectConversation(conversation.id)
                },
                onSettings = { configuringSpace = openedProject },
                onMemory = {
                    memoryInitialProjectId = openedProject.id
                    settingsStack = listOf(SettingsDestination.MEMORY)
                },
                onRename = {
                    renamingSpace = openedProject
                    renameSpaceName = openedProject.name
                },
                onShortcut = {
                    requestDesktopShortcut(SHORTCUT_KIND_SPACE, openedProject.id.value, openedProject.name)
                },
                onDelete = {
                    viewModel.deleteSpace(openedProject.id)
                    openedProjectId = null
                },
                composer = {
                    MutCubeComposer(
                        value = message,
                        onValueChange = ::updateComposer,
                        onAttachmentClick = { showAttachmentSheet = true },
                        onVoiceClick = ::startVoiceInput,
                        onVoiceCancel = ::cancelVoiceInput,
                        voiceRecording = voiceInputActive,
                        voiceTranscribing = voiceTranscribing,
                        voiceAmplitude = voiceAmplitude,
                        voiceDurationSeconds = voiceDurationSeconds,
                        reasoningLevel = uiState.modelSettings.reasoningLevel,
                        onReasoningLevelChange = { level ->
                            viewModel.saveModelSettings(uiState.modelSettings.copy(reasoningLevel = level))
                        },
                        onSendClick = {
                            if (!uiState.isGenerating) {
                                conversationOriginProjectId = openedProject.id
                                viewModel.selectSpace(openedProject.id)
                                viewModel.sendUserMessage(message, pendingAttachments)
                                message = ""
                                pendingAttachments = emptyList()
                                openedProjectId = null
                            }
                        },
                        isGenerating = uiState.isGenerating,
                        onStopClick = viewModel::stopGeneration,
                        enterToSend = uiState.displaySettings.enterToSend,
                        attachments = pendingAttachments,
                        onRemoveAttachment = { attachment ->
                            pendingAttachments = pendingAttachments.filterNot { it.id == attachment.id }
                            viewModel.deleteDraftAttachment(attachment)
                        },
                        quickPrompts = uiState.contextLibrary.quickPrompts,
                        skills = installedSkills.filter { it.projectId == null || it.projectId == openedProject.id.value },
                        focusRequester = projectComposerFocusRequester,
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 18.dp),
                    )
                },
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                MutCubeTopBar(
                    selected = uiState.destination,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSurfaceSelected = {
                        viewModel.selectDestination(it)
                        if (it == RootDestination.CHAT) showTemplateSearch = false
                    },
                    onContextClick = {
                        if (uiState.destination == RootDestination.TEMPLATES) {
                            showTemplateSearch = !showTemplateSearch
                        } else {
                            showConversationActions = true
                        }
                    },
                )

                val boundTemplates = templateBindings.filter { it.enabled && it.projectId == uiState.selectedSpaceId?.value && templateDefinitions.any { definition -> definition.id == it.templateId } }
                val boundTemplate = boundTemplates.firstOrNull { it.templateId == selectedTemplateId } ?: boundTemplates.firstOrNull()
                if (uiState.destination == RootDestination.CHAT && boundTemplate != null) {
                    TextButton(onClick = {
                        selectedTemplateId = boundTemplate.templateId
                        if (boundTemplate.version == templateDefinitions.first { it.id == boundTemplate.templateId }.version) templateProjectId = boundTemplate.projectId
                        else choosingTemplateProject = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("返回${templateDefinitions.first { it.id == boundTemplate.templateId }.name}")
                    }
                    if (boundTemplates.size > 1) TextButton(onClick = { choosingBoundTemplateProjectId = boundTemplate.projectId }, modifier = Modifier.fillMaxWidth()) {
                        Text("选择其他模板")
                    }
                }
                when (uiState.destination) {
                    RootDestination.CHAT -> ChatSurface(
                        templateCapabilities = templateCapabilities,
                        liveToolTrace = liveToolTrace.takeIf { liveToolConversationId == uiState.selectedConversationId && it.any { part -> part is MessageContentPart.ToolCall && part.toolName.startsWith("template_action_") } } ?: emptyList(),
                        onTemplateCapabilities = { showTemplateCapabilities = true },
                        onOpenTemplateResult = { target ->
                            val bound = templateBindings.firstOrNull { it.enabled && it.projectId == uiState.selectedSpaceId?.value && it.templateId == target.optString("templateId") }
                            val manifest = templateDefinitions.firstOrNull { it.id == target.optString("templateId") }
                            val action = manifest?.actions?.firstOrNull { it.id == target.optString("actionId") && com.dwl.mutcube.template.core.ActionChannel.CHAT in it.channels && it.collection == target.optString("collection") }
                            if (bound != null && manifest != null && action != null && bound.version == manifest.version) {
                                selectedTemplateId = manifest.id
                                templateLaunchTarget = target.toString()
                                templateProjectId = bound.projectId
                            } else scope.launch { snackbarHostState.showSnackbar("模板未启用或授权已变更，请重新确认项目模板授权。") }
                        },
                        messages = uiState.messages,
                        providers = uiState.providerConfiguration,
                        streamingText = uiState.streamingText,
                        isGenerating = uiState.isGenerating,
                        regeneratingNodeId = uiState.regeneratingNodeId,
                        modelError = uiState.modelError,
                        canRetryGeneration = uiState.canRetryGeneration,
                        onRetry = viewModel::retryLastGeneration,
                        onOpenSettings = { settingsStack = listOf(SettingsDestination.MODEL) },
                        onRegenerate = viewModel::regenerateLastResponse,
                        onSelectVariant = viewModel::selectMessageVariant,
                        onEdit = { chatMessage ->
                            editingMessage = chatMessage
                            editingMessageText = chatMessage.text
                        },
                        onDelete = { chatMessage -> viewModel.deleteMessageBranch(chatMessage.nodeId) },
                        onFork = { chatMessage -> viewModel.forkConversation(chatMessage.nodeId) },
                        onFavorite = { chatMessage ->
                            viewModel.setMessageFavorite(chatMessage.id, !chatMessage.favorite)
                        },
                        onCopy = { chatMessage ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("MutCube 消息", chatMessage.text))
                        },
                        onShare = { chatMessage ->
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, chatMessage.text)
                                    },
                                    "分享消息",
                                ),
                            )
                        },
                        onTranslate = viewModel::translateMessage,
                        onSpeak = { chatMessage ->
                            speakText(chatMessage.translation ?: chatMessage.text, chatMessage.id.value)
                        },
                        speakingMessageId = speakingMessageId,
                        speechPreparingMessageId = speechPreparingMessageId,
                        showMessageTime = uiState.displaySettings.showMessageTime,
                        autoScroll = uiState.displaySettings.autoScroll,
                        chatBackground = uiState.displaySettings.chatBackground,
                        bubbleStyle = uiState.displaySettings.bubbleStyle,
                        showAvatars = uiState.displaySettings.showMessageAvatars,
                        showModelLabel = uiState.displaySettings.showModelLabel,
                        modifier = Modifier.weight(1f),
                    )
                    RootDestination.TEMPLATES -> TemplateSurface(
                        templates = templateDefinitions,
                        onManage = { showTemplatePackages = true },
                        projectCount = uiState.spaces.size,
                        boundProjectNames = templateBindings.filter { it.enabled }.groupBy { it.templateId }.mapValues { (_, bindings) ->
                            bindings.mapNotNull { binding -> uiState.spaces.firstOrNull { it.id.value == binding.projectId }?.name }
                        },
                        onBindTemplate = { template -> selectedTemplateId = template.id; choosingTemplateProject = true },
                        query = templateQuery,
                        showSearch = showTemplateSearch,
                        onQueryChange = { templateQuery = it },
                        onTemplateClick = { template ->
                            selectedTemplateId = template.id
                            val projects = templateEntryProjects(templateBindings, template.id,
                                uiState.spaces.map { it.id.value }.toSet(), uiState.selectedSpaceId?.value)
                            when (projects.size) {
                                0 -> choosingTemplateProject = true
                                1 -> {
                                    val bound = templateBindings.first { it.enabled && it.templateId == template.id && it.projectId == projects.single() }
                                    if (bound.version == templateDefinitions.first { it.id == template.id }.version) {
                                        templateLaunchTarget = null
                                        templateProjectId = bound.projectId
                                    } else choosingTemplateProject = true
                                }
                                else -> choosingExistingTemplateProject = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (uiState.destination == RootDestination.CHAT) {
                    FollowUpSuggestions(
                        suggestions = uiState.followUpSuggestions,
                        onSelect = { suggestion -> viewModel.sendUserMessage(suggestion) },
                    )
                    MutCubeComposer(
                        value = message,
                        onValueChange = ::updateComposer,
                        onAttachmentClick = { showAttachmentSheet = true },
                        onVoiceClick = ::startVoiceInput,
                        onVoiceCancel = ::cancelVoiceInput,
                        voiceRecording = voiceInputActive,
                        voiceTranscribing = voiceTranscribing,
                        voiceAmplitude = voiceAmplitude,
                        voiceDurationSeconds = voiceDurationSeconds,
                        reasoningLevel = uiState.modelSettings.reasoningLevel,
                        onReasoningLevelChange = { level ->
                            viewModel.saveModelSettings(uiState.modelSettings.copy(reasoningLevel = level))
                        },
                        onSendClick = {
                            if (!uiState.isGenerating) {
                                viewModel.sendUserMessage(message, pendingAttachments)
                                message = ""
                                pendingAttachments = emptyList()
                            }
                        },
                        isGenerating = uiState.isGenerating,
                        onStopClick = viewModel::stopGeneration,
                        enterToSend = uiState.displaySettings.enterToSend,
                        attachments = pendingAttachments,
                        onRemoveAttachment = { attachment ->
                            pendingAttachments = pendingAttachments.filterNot { it.id == attachment.id }
                            viewModel.deleteDraftAttachment(attachment)
                        },
                        quickPrompts = uiState.contextLibrary.quickPrompts,
                        skills = installedSkills.filter { it.projectId == null || it.projectId == uiState.selectedSpaceId?.value },
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 18.dp),
                    )
                }
            }
        }

            if (showConversationActions) {
                uiState.conversations.firstOrNull { it.id == uiState.selectedConversationId }?.let { conversation ->
                    ConversationActionsSheet(
                        conversation = conversation,
                        onDismiss = { showConversationActions = false },
                        onSettings = { showConversationSpaceDialog = true },
                        onTemplateCapabilities = if (uiState.selectedSpaceId != null) ({ showTemplateCapabilities = true }) else null,
                        onMove = { movingConversation = conversation },
                        onStatistics = { settingsStack = listOf(SettingsDestination.STATS) },
                        onExport = { exportingConversation = conversation },
                        onDelete = {
                            viewModel.trashConversation(conversation.id) {
                                scope.launch {
                                    if (
                                        snackbarHostState.showSnackbar(
                                            message = "对话已删除",
                                            actionLabel = "撤销",
                                            duration = SnackbarDuration.Short,
                                        ) == SnackbarResult.ActionPerformed
                                    ) {
                                        viewModel.restoreConversation(conversation.id)
                                    }
                                }
                            }
                        },
                    )
                } ?: run { showConversationActions = false; showTemplateCapabilities = true }
            }
            if (showTemplateCapabilities) TemplateCapabilitiesPanel(templateCapabilities,
                onDismiss = { showTemplateCapabilities = false },
                onChoose = { request -> updateComposer(if (message.isBlank()) request else message.trimEnd() + "\n" + request); showTemplateCapabilities = false })

            if (showAttachmentSheet) {
                AttachmentSheet(
                    onDismiss = { showAttachmentSheet = false },
                    onCamera = {
                        showAttachmentSheet = false
                        cameraLauncher.launch(null)
                    },
                    onGallery = {
                        showAttachmentSheet = false
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onFiles = {
                        showAttachmentSheet = false
                        fileLauncher.launch(arrayOf("image/*", "audio/*", "video/*", "application/pdf", "text/*"))
                    },
                    onRecentImage = { uri ->
                        showAttachmentSheet = false
                        importUris(listOf(uri))
                    },
                )
            }

            exportingConversation?.let { conversation ->
                ExportConversationDialog(
                    conversation = conversation,
                    onDismiss = { exportingConversation = null },
                    onSelect = { format ->
                        viewModel.exportConversation(
                            conversation.id,
                            format,
                            onReady = { export ->
                                pendingExport = export
                                if (format == ConversationExportFormat.MARKDOWN) {
                                    markdownExportLauncher.launch(export.fileName)
                                } else {
                                    jsonExportLauncher.launch(export.fileName)
                                }
                            },
                            onError = { error -> scope.launch { snackbarHostState.showSnackbar(error) } },
                        )
                        exportingConversation = null
                    },
                )
            }

            renamingConversation?.let { conversation ->
                RenameConversationDialog(
                    title = renameTitle,
                    onTitleChange = { renameTitle = it },
                    onDismiss = { renamingConversation = null },
                    onConfirm = {
                        viewModel.renameConversation(conversation.id, renameTitle)
                        renamingConversation = null
                    },
                )
            }

            renamingSpace?.let { space ->
                RenameSpaceDialog(
                    name = renameSpaceName,
                    onNameChange = { renameSpaceName = it },
                    onDismiss = { renamingSpace = null },
                    onConfirm = {
                        viewModel.renameSpace(space.id, renameSpaceName)
                        renamingSpace = null
                    },
                )
            }

            editingMessage?.let { chatMessage ->
                EditMessageDialog(
                    text = editingMessageText,
                    onTextChange = { editingMessageText = it },
                    onDismiss = { editingMessage = null },
                    onConfirm = {
                        viewModel.editUserMessage(chatMessage.nodeId, editingMessageText)
                        editingMessage = null
                    },
                )
            }

            MutCubeNoticeHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 110.dp),
            )
        }
    }
}

private const val LONG_PASTE_CHARACTER_THRESHOLD = 2_000
private const val MAX_NETWORK_RECORDING_SECONDS = 180
private const val MAX_PROVIDER_TRANSFER_BYTES = 1_000_000
private const val SHORTCUT_KIND_CONVERSATION = "conversation"
private const val SHORTCUT_KIND_SPACE = "space"
