package com.dwl.mutcube.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.model.ChatMessage
import com.dwl.mutcube.core.model.Conversation
import com.dwl.mutcube.core.model.ConversationId
import com.dwl.mutcube.core.model.FavoriteMessage
import com.dwl.mutcube.core.model.MessageId
import com.dwl.mutcube.core.model.MessageNodeId
import com.dwl.mutcube.core.model.MessageRole
import com.dwl.mutcube.core.model.MessageContentPart
import com.dwl.mutcube.core.model.MessagePartId
import com.dwl.mutcube.core.model.MemoryEntry
import com.dwl.mutcube.core.model.MemoryId
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.core.model.SpaceModelOverrides
import com.dwl.mutcube.core.ai.ModelSettings
import com.dwl.mutcube.core.ai.ModelSettingsStore
import com.dwl.mutcube.core.ai.ProviderConfiguration
import com.dwl.mutcube.core.ai.ProviderProfile
import com.dwl.mutcube.core.ai.ProviderProfileStore
import com.dwl.mutcube.core.ai.ProviderProtocol
import com.dwl.mutcube.core.ai.ProviderConfigurationTransfer
import com.dwl.mutcube.core.ai.withoutUnconfiguredMimoStarterModel
import com.dwl.mutcube.storage.DisplaySettings
import com.dwl.mutcube.storage.DisplaySettingsStore
import com.dwl.mutcube.storage.StartupMode
import com.dwl.mutcube.core.extensions.McpClientService
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.extensions.McpServerStore
import com.dwl.mutcube.core.extensions.McpTool
import com.dwl.mutcube.core.extensions.McpAuthType
import com.dwl.mutcube.storage.McpOAuthManager
import com.dwl.mutcube.core.extensions.ExtensionAuditEvent
import com.dwl.mutcube.core.extensions.ExtensionAuditStore
import com.dwl.mutcube.storage.SecurityDiagnosticReport
import com.dwl.mutcube.storage.SecurityDiagnostics
import com.dwl.mutcube.storage.AttachmentStore
import com.dwl.mutcube.storage.BackupService
import com.dwl.mutcube.storage.BackupSummary
import com.dwl.mutcube.storage.RemoteBackupConfig
import com.dwl.mutcube.storage.RemoteBackupConfigStore
import com.dwl.mutcube.storage.RemoteBackupService
import com.dwl.mutcube.storage.BackupReminderScheduler
import com.dwl.mutcube.storage.BackupReminderStore
import com.dwl.mutcube.storage.SpeechService
import com.dwl.mutcube.storage.SpeechServiceSettings
import com.dwl.mutcube.storage.SpeechServiceSettingsStore
import java.io.File
import com.dwl.mutcube.storage.PendingToolApproval
import com.dwl.mutcube.storage.ToolApprovalCoordinator
import com.dwl.mutcube.storage.ToolApprovalDecision
import com.dwl.mutcube.storage.ContextLibraryState
import com.dwl.mutcube.storage.ContextLibraryStore
import com.dwl.mutcube.storage.SkillStore
import com.dwl.mutcube.storage.SkillInvocation
import android.net.Uri
import com.dwl.mutcube.storage.ContextMode
import com.dwl.mutcube.storage.KnowledgeEntry
import com.dwl.mutcube.storage.QuickPrompt
import com.dwl.mutcube.storage.RequestLogState
import com.dwl.mutcube.storage.RequestLogStore
import com.dwl.mutcube.storage.StorageSummary
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.feature.chat.ChatGenerationException
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.feature.chat.ConversationExport
import com.dwl.mutcube.feature.chat.ConversationExportFormat
import com.dwl.mutcube.feature.chat.ConversationExportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private const val STREAMING_RENDER_INTERVAL_NANOS = 75_000_000L

private data class GenerationErrorState(
    val message: String?,
    val retryable: Boolean,
)

private data class GenerationPresentationState(
    val streamingText: String,
    val generating: Boolean,
    val regeneratingNodeId: MessageNodeId?,
    val toolApproval: PendingToolApproval?,
    val followUpSuggestions: List<String>,
)

private data class RootSelectionState(
    val destination: RootDestination,
    val conversationId: ConversationId?,
    val spaceId: SpaceId?,
)

private data class ModelConfigurationState(
    val configuredProviderIds: Set<String>,
    val settings: ModelSettings,
    val providers: ProviderConfiguration,
)

private data class ProviderInspectionState(
    val loading: Boolean = false,
    val message: String? = null,
    val models: List<String> = emptyList(),
)

private data class SettingsPresentationState(
    val model: ModelConfigurationState,
    val display: DisplaySettings,
    val security: SecurityDiagnosticReport?,
    val providerInspection: ProviderInspectionState,
    val mcpServers: List<McpServer>,
    val mcpBusyServerId: String?,
    val auditEvents: List<ExtensionAuditEvent>,
    val contextLibrary: ContextLibraryState,
    val requestLogs: RequestLogState,
    val remoteBackup: RemoteBackupConfig,
    val backupReminderDays: Int,
    val speechServiceSettings: SpeechServiceSettings,
)

private data class ExtensionPresentationState(
    val servers: List<McpServer>,
    val busyServerId: String?,
    val auditEvents: List<ExtensionAuditEvent>,
    val contextLibrary: ContextLibraryState,
    val requestLogs: RequestLogState,
)

enum class RootDestination {
    CHAT,
    TEMPLATES,
}

data class MutCubeUiState(
    val destination: RootDestination = RootDestination.CHAT,
    val spaces: List<Space> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val trashedConversations: List<Conversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val selectedSpaceId: SpaceId? = null,
    val messages: List<ChatMessage> = emptyList(),
    val conversationSearchQuery: String = "",
    val conversationSearchResults: List<Conversation> = emptyList(),
    val favoriteMessages: List<FavoriteMessage> = emptyList(),
    val memories: List<MemoryEntry> = emptyList(),
    val streamingText: String = "",
    val isGenerating: Boolean = false,
    val regeneratingNodeId: MessageNodeId? = null,
    val credentialConfigured: Boolean = false,
    val effectiveProviderId: String? = null,
    val configuredProviderIds: Set<String> = emptySet(),
    val modelError: String? = null,
    val canRetryGeneration: Boolean = false,
    val modelSettings: ModelSettings = ModelSettings(),
    val providerConfiguration: ProviderConfiguration = ProviderConfiguration(),
    val displaySettings: DisplaySettings = DisplaySettings(),
    val securityReport: SecurityDiagnosticReport? = null,
    val providerInspectionLoading: Boolean = false,
    val providerInspectionMessage: String? = null,
    val discoveredModels: List<String> = emptyList(),
    val mcpServers: List<McpServer> = emptyList(),
    val mcpBusyServerId: String? = null,
    val extensionAuditEvents: List<ExtensionAuditEvent> = emptyList(),
    val pendingToolApproval: PendingToolApproval? = null,
    val contextLibrary: ContextLibraryState = ContextLibraryState(),
    val requestLogs: RequestLogState = RequestLogState(),
    val followUpSuggestions: List<String> = emptyList(),
    val remoteBackupConfig: RemoteBackupConfig = RemoteBackupConfig(),
    val backupReminderDays: Int = 0,
    val speechServiceSettings: SpeechServiceSettings = SpeechServiceSettings(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MutCubeViewModel(
    private val repository: ConversationRepository,
    private val chatGenerationService: ChatGenerationService,
    private val credentialStore: CredentialStore,
    private val modelSettingsStore: ModelSettingsStore,
    private val providerProfileStore: ProviderProfileStore,
    private val displaySettingsStore: DisplaySettingsStore,
    private val conversationExportService: ConversationExportService,
    private val securityDiagnostics: SecurityDiagnostics,
    private val attachmentStore: AttachmentStore,
    private val backupService: BackupService,
    private val remoteBackupConfigStore: RemoteBackupConfigStore,
    private val remoteBackupService: RemoteBackupService,
    private val backupReminderStore: BackupReminderStore,
    private val backupReminderScheduler: BackupReminderScheduler,
    private val speechServiceSettingsStore: SpeechServiceSettingsStore,
    private val speechService: SpeechService,
    private val mcpServerStore: McpServerStore,
    private val mcpClientService: McpClientService,
    private val mcpOAuthManager: McpOAuthManager,
    private val extensionAuditStore: ExtensionAuditStore,
    private val toolApprovalCoordinator: ToolApprovalCoordinator,
    private val contextLibraryStore: ContextLibraryStore,
    private val skillStore: SkillStore,
    private val requestLogStore: RequestLogStore,
) : ViewModel() {
    val installedSkills = skillStore.skills
    val skillAudit = skillStore.audit
    private val destination = MutableStateFlow(RootDestination.CHAT)
    private val selectedConversationId = MutableStateFlow<ConversationId?>(null)
    private val selectedSpaceId = MutableStateFlow<SpaceId?>(null)
    private val conversationSearchQuery = MutableStateFlow("")
    private val sendMutex = Mutex()
    private val streamingText = MutableStateFlow("")
    val liveToolTrace = MutableStateFlow<List<com.dwl.mutcube.core.model.MessageContentPart>>(emptyList())
    val liveToolConversationId = MutableStateFlow<ConversationId?>(null)
    private val isGenerating = MutableStateFlow(false)
    private val regeneratingNodeId = MutableStateFlow<MessageNodeId?>(null)
    private val configuredProviderIds = MutableStateFlow<Set<String>>(emptySet())
    private val modelError = MutableStateFlow<String?>(null)
    private val canRetryGeneration = MutableStateFlow(false)
    private val securityReport = MutableStateFlow<SecurityDiagnosticReport?>(null)
    private val providerInspection = MutableStateFlow(ProviderInspectionState())
    private val mcpBusyServerId = MutableStateFlow<String?>(null)
    private val followUpSuggestions = MutableStateFlow<List<String>>(emptyList())
    private var generationJob: Job? = null
    private var activeGenerationAttempt: String? = null
    private var retryRegenerationNodeId: MessageNodeId? = null
    private var userChoseSelection = false

    private val operationErrorHandler = CoroutineExceptionHandler { context, failure ->
        modelError.value = "操作失败，请重试或查看运行记录（${failure.javaClass.simpleName}）"
        canRetryGeneration.value = false
        if (generationJob === context[Job]) {
            isGenerating.value = false
            regeneratingNodeId.value = null
            liveToolTrace.value = emptyList()
            liveToolConversationId.value = null
        }
    }

    private fun launchSafe(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch(context + operationErrorHandler, block = block)

    private val messages = selectedConversationId.flatMapLatest { conversationId ->
        conversationId?.let(repository::observeMessages) ?: flowOf(emptyList())
    }

    private val conversationSearchResults = conversationSearchQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else repository.searchConversations(query)
    }

    private val rootSelectionState = combine(
        destination,
        selectedConversationId,
        selectedSpaceId,
    ) { destination, conversationId, spaceId ->
        RootSelectionState(destination, conversationId, spaceId)
    }

    private val baseState = combine(
        rootSelectionState,
        repository.spaces,
        repository.conversations,
        messages,
        conversationSearchResults,
    ) { selection, spaces, conversations, messages, searchResults ->
        MutCubeUiState(
            destination = selection.destination,
            spaces = spaces,
            conversations = conversations,
            selectedConversationId = selection.conversationId,
            selectedSpaceId = selection.spaceId,
            messages = messages,
            conversationSearchQuery = conversationSearchQuery.value,
            conversationSearchResults = searchResults,
        )
    }

    private val generationErrorState = combine(modelError, canRetryGeneration) { message, retryable ->
        GenerationErrorState(message, retryable)
    }

    private val generationPresentationState = combine(
        streamingText,
        isGenerating,
        regeneratingNodeId,
        toolApprovalCoordinator.pending,
        followUpSuggestions,
    ) { streamingText, generating, messageId, toolApproval, suggestions ->
        GenerationPresentationState(streamingText, generating, messageId, toolApproval, suggestions)
    }

    private val modelConfigurationState = combine(
        configuredProviderIds,
        modelSettingsStore.settings,
        providerProfileStore.configuration,
    ) { configuredIds, settings, providers -> ModelConfigurationState(configuredIds, settings, providers) }

    private val mcpPresentation = combine(
        mcpServerStore.servers, mcpBusyServerId, extensionAuditStore.events, contextLibraryStore.state,
        requestLogStore.state,
    ) { servers, busy, audit, library, requestLogs ->
        ExtensionPresentationState(servers, busy, audit, library, requestLogs)
    }

    private val extensionAndBackupState = combine(
        mcpPresentation,
        remoteBackupConfigStore.config,
        backupReminderStore.days,
    ) { extensions, remoteBackup, reminderDays -> Triple(extensions, remoteBackup, reminderDays) }

    private val settingsExtras = combine(
        extensionAndBackupState,
        speechServiceSettingsStore.settings,
    ) { extensionAndBackup, speech -> extensionAndBackup to speech }

    private val settingsState = combine(
        modelConfigurationState,
        displaySettingsStore.settings,
        securityReport,
        providerInspection,
        settingsExtras,
    ) { model, display, security, inspection, extras ->
        val (extensionAndBackup, speech) = extras
        val (mcp, remoteBackup, reminderDays) = extensionAndBackup
        SettingsPresentationState(
            model, display, security, inspection, mcp.servers, mcp.busyServerId, mcp.auditEvents,
            mcp.contextLibrary, mcp.requestLogs, remoteBackup, reminderDays, speech,
        )
    }

    private val savedContentState = combine(
        repository.favoriteMessages,
        repository.memories,
        repository.trashedConversations,
    ) { favorites, memories, trashed -> Triple(favorites, memories, trashed) }

    val uiState: StateFlow<MutCubeUiState> = combine(
        baseState,
        settingsState,
        generationErrorState,
        generationPresentationState,
        savedContentState,
    ) { state, settings, generationError, generationPresentation, savedContent ->
        val modelConfiguration = settings.model
        val displaySettings = settings.display
        val securityReport = settings.security
        val (favorites, memories, trashed) = savedContent
        val selectedConversation = state.conversations.firstOrNull { it.id == state.selectedConversationId }
        val selectedProjectId = selectedConversation?.spaceId ?: state.selectedSpaceId
        val effectiveProviderId = state.spaces.firstOrNull { it.id == selectedProjectId }?.modelProfileId
            ?: modelConfiguration.providers.activeProfileId
        state.copy(
            streamingText = generationPresentation.streamingText,
            isGenerating = generationPresentation.generating,
            regeneratingNodeId = generationPresentation.regeneratingNodeId,
            credentialConfigured = effectiveProviderId in modelConfiguration.configuredProviderIds,
            effectiveProviderId = effectiveProviderId,
            configuredProviderIds = modelConfiguration.configuredProviderIds,
            modelSettings = modelConfiguration.settings,
            providerConfiguration = modelConfiguration.providers,
            displaySettings = displaySettings,
            securityReport = securityReport,
            providerInspectionLoading = settings.providerInspection.loading,
            providerInspectionMessage = settings.providerInspection.message,
            discoveredModels = settings.providerInspection.models,
            mcpServers = settings.mcpServers,
            mcpBusyServerId = settings.mcpBusyServerId,
            extensionAuditEvents = settings.auditEvents,
            pendingToolApproval = generationPresentation.toolApproval,
            contextLibrary = settings.contextLibrary,
            requestLogs = settings.requestLogs,
            remoteBackupConfig = settings.remoteBackup,
            backupReminderDays = settings.backupReminderDays,
            speechServiceSettings = settings.speechServiceSettings,
            followUpSuggestions = generationPresentation.followUpSuggestions,
            modelError = generationError.message,
            canRetryGeneration = generationError.retryable,
            favoriteMessages = favorites,
            memories = memories,
            trashedConversations = trashed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MutCubeUiState())

    init {
        if (displaySettingsStore.settings.value.startupMode == StartupMode.LAST_CONVERSATION) {
            launchSafe {
                val latest = repository.conversations.first().firstOrNull()
                if (!userChoseSelection) selectedConversationId.value = latest?.id
            }
        }
        refreshCredentialState()
        launchSafe {
            providerProfileStore.configuration.collect { refreshCredentialState() }
        }
        launchSafe {
            runCatching { attachmentStore.cleanupUnreferenced(repository.getAllAttachmentUris()) }
        }
    }

    fun selectDestination(value: RootDestination) {
        destination.value = value
    }

    fun startNewConversation() {
        userChoseSelection = true
        selectedConversationId.value = null
        destination.value = RootDestination.CHAT
        clearGenerationPresentation()
    }

    fun selectConversation(id: ConversationId) {
        userChoseSelection = true
        selectedConversationId.value = id
        destination.value = RootDestination.CHAT
        clearGenerationPresentation()
    }

    fun openConversationShortcut(id: ConversationId, onMissing: () -> Unit) {
        launchSafe {
            val conversation = repository.getConversation(id)
            if (conversation == null || conversation.deletedAt != null) onMissing() else selectConversation(id)
        }
    }

    fun openSpaceShortcut(id: SpaceId, onMissing: () -> Unit) {
        launchSafe {
            if (repository.spaces.first().none { it.id == id }) {
                onMissing()
            } else {
                selectSpace(id)
                startNewConversation()
            }
        }
    }

    fun selectSpace(id: SpaceId?) {
        selectedSpaceId.value = id
    }

    fun setConversationSearchQuery(query: String) {
        conversationSearchQuery.value = query
    }

    fun createSpace(name: String, onCreated: (SpaceId) -> Unit = {}) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        launchSafe {
            val id = repository.createSpace(normalized)
            selectedSpaceId.value = id
            onCreated(id)
        }
    }

    fun renameSpace(id: SpaceId, name: String) {
        launchSafe { repository.renameSpace(id, name) }
    }

    fun setSpacePinned(id: SpaceId, pinned: Boolean) {
        launchSafe { repository.setSpacePinned(id, pinned) }
    }

    fun deleteSpace(id: SpaceId) {
        if (selectedSpaceId.value == id) selectedSpaceId.value = null
        launchSafe { repository.deleteSpace(id) }
    }

    fun updateSpaceModelSettings(id: SpaceId, profileId: String?, overrides: SpaceModelOverrides) {
        launchSafe {
            if (profileId != null) {
                val profile = providerProfileStore.read().profiles.firstOrNull { it.id == profileId }
                if (profile == null || !profile.enabled || profile.modelId.isBlank() || credentialStore.read(profileId) == null) {
                    modelError.value = "项目模型不可用，请选择已启用且已配置密钥的 Provider"
                    return@launchSafe
                }
            }
            repository.setSpaceModelProfile(id, profileId)
            repository.updateSpaceModelSettings(id, overrides)
        }
    }

    fun moveSelectedConversationToSpace(spaceId: SpaceId?) {
        val conversationId = selectedConversationId.value ?: return
        moveConversationToSpace(conversationId, spaceId)
    }

    fun moveConversationToSpace(conversationId: ConversationId, spaceId: SpaceId?, onComplete: () -> Unit = {}) {
        launchSafe {
            repository.setConversationSpace(conversationId, spaceId)
            onComplete()
        }
    }

    fun updateSelectedConversationSettings(spaceId: SpaceId?, systemPrompt: String?) {
        val conversationId = selectedConversationId.value ?: return
        launchSafe {
            repository.setConversationSpace(conversationId, spaceId)
            repository.setConversationSystemPrompt(conversationId, systemPrompt)
        }
    }

    fun renameConversation(id: ConversationId, title: String) {
        launchSafe { repository.renameConversation(id, title) }
    }

    fun regenerateConversationTitle(id: ConversationId) {
        launchSafe {
            runCatching { chatGenerationService.generateConversationTitle(id) }
                .onFailure { error ->
                    modelError.value = (error as? ChatGenerationException)?.userMessage ?: "标题生成失败，请稍后重试"
                    canRetryGeneration.value = false
                }
        }
    }

    fun setConversationPinned(id: ConversationId, pinned: Boolean) {
        launchSafe { repository.setConversationPinned(id, pinned) }
    }

    fun trashConversation(id: ConversationId, onComplete: () -> Unit = {}) {
        if (selectedConversationId.value == id) startNewConversation()
        launchSafe {
            repository.trashConversation(id)
            onComplete()
        }
    }

    fun restoreConversation(id: ConversationId) {
        launchSafe { repository.restoreConversation(id) }
    }

    fun restoreAllConversations() {
        launchSafe { repository.restoreAllConversations() }
    }

    fun permanentlyDeleteConversation(id: ConversationId) {
        launchSafe { repository.permanentlyDeleteConversation(id) }
    }

    fun emptyTrash() {
        launchSafe { repository.emptyTrash() }
    }

    fun trashAllConversations(onComplete: () -> Unit = {}) {
        startNewConversation()
        launchSafe {
            repository.trashAllConversations()
            onComplete()
        }
    }

    fun sendUserMessage(text: String, attachments: List<MessageContentPart.Attachment> = emptyList()) {
        val normalized = text.trim()
        if ((normalized.isEmpty() && attachments.isEmpty()) || isGenerating.value || generationJob?.isActive == true) return
        val targetConversationId = selectedConversationId.value
        val targetSpaceId = selectedSpaceId.value
        followUpSuggestions.value = emptyList()
        val parts = buildList {
            if (normalized.isNotEmpty()) add(MessageContentPart.Text(MessagePartId(UUID.randomUUID().toString()), normalized))
            addAll(attachments)
        }
        generationJob = launchSafe {
            sendMutex.withLock {
                modelError.value = null
                canRetryGeneration.value = false
                retryRegenerationNodeId = null
                val currentId = targetConversationId
                val createdConversation = currentId == null
                val conversationId = if (currentId == null) {
                    userChoseSelection = true
                    repository.createWithFirstUserMessage(parts, targetSpaceId).also {
                        selectedConversationId.value = it
                    }
                } else {
                    repository.appendMessage(currentId, MessageRole.USER, parts)
                    currentId
                }
                if (generateReply(conversationId)) {
                    launchSafe { refreshFollowUpSuggestions(conversationId) }
                    if (createdConversation) launchSafe {
                        runCatching {
                            chatGenerationService.generateConversationTitle(conversationId, automatic = true)
                        }
                    }
                }
            }
        }
    }

    fun deleteDraftAttachment(attachment: MessageContentPart.Attachment) {
        launchSafe { attachmentStore.deleteDraft(attachment) }
    }

    fun discardDraftAttachments(attachments: List<MessageContentPart.Attachment>) {
        if (attachments.isEmpty()) return
        launchSafe { attachments.forEach { attachmentStore.deleteDraft(it) } }
    }

    fun saveProviderProfile(
        profileId: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        credential: String,
        protocol: ProviderProtocol,
        models: List<com.dwl.mutcube.core.ai.ProviderModel>,
        asrProtocol: com.dwl.mutcube.core.ai.AsrProtocol,
        ttsProtocol: com.dwl.mutcube.core.ai.TtsProtocol,
        ttsStreaming: Boolean,
    ) {
        launchSafe {
            runCatching {
                val configuration = providerProfileStore.read()
                val id = profileId ?: UUID.randomUUID().toString()
                val existing = configuration.profiles.firstOrNull { it.id == id }
                val profile = ProviderProfile(
                    id = id,
                    name = name,
                    baseUrl = baseUrl,
                    modelId = modelId,
                    protocol = protocol,
                    enabled = existing?.enabled ?: true,
                    models = models,
                    asrProtocol = asrProtocol,
                    ttsProtocol = ttsProtocol,
                    ttsStreaming = ttsStreaming,
                )
                providerProfileStore.write(
                    configuration.copy(
                        profiles = if (existing == null) configuration.profiles + profile else configuration.profiles.map { if (it.id == id) profile else it },
                        activeProfileId = configuration.activeProfileId,
                    ),
                )
                if (credential.isNotBlank()) credentialStore.write(id, credential)
            }.onSuccess {
                refreshCredentialState()
                modelError.value = null
                canRetryGeneration.value = false
            }.onFailure {
                modelError.value = "Provider 配置保存失败：${it.message ?: "请重试"}"
                canRetryGeneration.value = false
            }
        }
    }

    fun inspectProvider(
        profileId: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        credential: String,
        protocol: ProviderProtocol,
    ) {
        if (providerInspection.value.loading) return
        launchSafe {
            providerInspection.value = ProviderInspectionState(loading = true, message = "正在连接…")
            runCatching {
                chatGenerationService.inspectProvider(profileId, name, baseUrl, modelId, credential, protocol)
            }.onSuccess { models ->
                providerInspection.value = ProviderInspectionState(
                    message = if (models.isEmpty()) "连接成功，但服务未返回模型列表" else "连接成功，发现 ${models.size} 个模型",
                    models = models,
                )
            }.onFailure { error ->
                providerInspection.value = ProviderInspectionState(
                    message = (error as? ChatGenerationException)?.userMessage ?: "Provider 连接测试失败",
                )
            }
        }
    }

    fun clearProviderInspection() {
        providerInspection.value = ProviderInspectionState()
    }

    fun selectProviderProfile(id: String) {
        launchSafe {
            runCatching {
                val configuration = providerProfileStore.read()
                require(configuration.profiles.any { it.id == id })
                providerProfileStore.write(configuration.copy(activeProfileId = id))
            }.onFailure {
                modelError.value = "无法切换 Provider"
            }
        }
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        launchSafe {
            runCatching {
                val configuration = providerProfileStore.read()
                providerProfileStore.write(
                    configuration.copy(
                        profiles = configuration.profiles.map { profile ->
                            if (profile.id == id) profile.copy(enabled = enabled) else profile
                        },
                    ),
                )
            }.onFailure { modelError.value = it.message ?: "Provider 状态更新失败" }
        }
    }

    fun moveProvider(id: String, offset: Int) {
        launchSafe {
            val configuration = providerProfileStore.read()
            val from = configuration.profiles.indexOfFirst { it.id == id }
            val to = (from + offset).coerceIn(0, configuration.profiles.lastIndex)
            if (from < 0 || from == to) return@launchSafe
            val reordered = configuration.profiles.toMutableList().apply {
                add(to, removeAt(from))
            }
            providerProfileStore.write(configuration.copy(profiles = reordered))
        }
    }

    fun deleteProviderProfile(id: String) {
        launchSafe {
            runCatching {
                val configuration = providerProfileStore.read()
                require(configuration.profiles.size > 1) { "至少保留一个 Provider" }
                val remaining = configuration.profiles.filterNot { it.id == id }
                providerProfileStore.write(
                    ProviderConfiguration(
                        remaining,
                        configuration.activeProfileId.takeUnless { it == id } ?: remaining.first().id,
                    ),
                )
                repository.spaces.first().filter { it.modelProfileId == id }.forEach {
                    repository.setSpaceModelProfile(it.id, null)
                }
                credentialStore.delete(id)
            }.onFailure { modelError.value = it.message ?: "Provider 删除失败" }
        }
    }

    fun deleteProviderCredential(profileId: String) {
        launchSafe {
            runCatching { credentialStore.delete(profileId) }
                .onSuccess { refreshCredentialState() }
                .onFailure { modelError.value = "密钥删除失败，请重试" }
        }
    }

    fun exportProviderConfiguration(onReady: (String) -> Unit) {
        launchSafe { onReady(ProviderConfigurationTransfer.encode(providerProfileStore.read())) }
    }

    fun importProviderConfiguration(content: String, onResult: (Result<Int>) -> Unit) {
        launchSafe {
            onResult(runCatching {
                val imported = ProviderConfigurationTransfer.decode(content)
                val current = providerProfileStore.read()
                providerProfileStore.write(ProviderConfigurationTransfer.merge(current, imported))
                imported.profiles.size
            })
        }
    }

    fun saveModelSettings(settings: ModelSettings) {
        launchSafe {
            runCatching { modelSettingsStore.write(settings) }
                .onFailure {
                    modelError.value = "模型设置保存失败，请重试"
                    canRetryGeneration.value = false
                }
        }
    }

    fun saveDisplaySettings(settings: DisplaySettings) {
        displaySettingsStore.write(settings)
        if (!settings.followUpSuggestions) followUpSuggestions.value = emptyList()
    }

    fun saveUserProfile(nickname: String, avatarFile: String) {
        displaySettingsStore.write(displaySettingsStore.settings.value.copy(userNickname = nickname.trim(), userAvatarFile = avatarFile))
    }

    fun saveMcpServer(server: McpServer, bearerToken: String = "") {
        launchSafe {
            val current = mcpServerStore.read().toMutableList()
            val index = current.indexOfFirst { it.id == server.id }
            if (index >= 0) current[index] = server else current += server
            mcpServerStore.write(current)
            bearerToken.trim().takeIf(String::isNotEmpty)?.let { credentialStore.write("mcp.${server.id}", it) }
        }
    }

    fun deleteMcpServer(server: McpServer) {
        launchSafe {
            mcpClientService.disconnect(server.id)
            mcpServerStore.write(mcpServerStore.read().filterNot { it.id == server.id })
            credentialStore.delete("mcp.${server.id}")
            mcpOAuthManager.clear(server.id)
        }
    }

    fun inspectMcpServer(server: McpServer) {
        if (mcpBusyServerId.value != null) return
        launchSafe {
            mcpBusyServerId.value = server.id
            runCatching {
                val token = when (server.authType) {
                    McpAuthType.NONE -> null
                    McpAuthType.BEARER_TOKEN -> credentialStore.read("mcp.${server.id}")
                    McpAuthType.OAUTH2 -> mcpOAuthManager.freshAccessToken(server)
                }
                mcpClientService.inspect(server, token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty())
            }.onSuccess { tools -> saveMcpServer(server.copy(tools = tools)) }
                .onFailure { modelError.value = "MCP 连接失败：${it.message ?: "未知错误"}" }
            mcpBusyServerId.value = null
        }
    }

    fun updateMcpTool(server: McpServer, tool: McpTool) {
        saveMcpServer(server.copy(tools = server.tools.map { if (it.name == tool.name) tool else it }))
    }

    fun beginMcpOAuth(
        server: McpServer,
        onReady: (android.content.Intent) -> Unit,
        onError: () -> Unit,
    ) {
        launchSafe {
            runCatching { mcpOAuthManager.authorizationIntent(server) }
                .onSuccess(onReady)
                .onFailure {
                    modelError.value = "OAuth 启动失败：${it.message ?: "未知错误"}"
                    onError()
                }
        }
    }

    fun finishMcpOAuth(server: McpServer, data: android.content.Intent?, onFinished: (Boolean) -> Unit) {
        launchSafe {
            runCatching { mcpOAuthManager.finishAuthorization(server, data) }
                .onSuccess { onFinished(true) }
                .onFailure { modelError.value = "OAuth 授权失败：${it.message ?: "已取消"}"; onFinished(false) }
        }
    }

    fun clearExtensionAudit() {
        launchSafe { extensionAuditStore.clear() }
    }

    fun resolveToolApproval(requestId: String, decision: ToolApprovalDecision) {
        toolApprovalCoordinator.resolve(requestId, decision)
    }

    fun saveQuickPrompt(prompt: QuickPrompt) = contextLibraryStore.saveQuickPrompt(prompt)
    fun deleteQuickPrompt(id: String) = contextLibraryStore.deleteQuickPrompt(id)
    fun createSkill(name: String, description: String, instructions: String, onResult: (String?) -> Unit) {
        launchSafe(Dispatchers.IO) {
            val error = runCatching { skillStore.create(name, description, instructions) }.exceptionOrNull()?.message
            withContext(Dispatchers.Main) { onResult(error) }
        }
    }
    fun importSkill(uri: Uri, onResult: (String?) -> Unit) {
        launchSafe(Dispatchers.IO) {
            val error = runCatching { skillStore.importUri(uri) }.exceptionOrNull()?.message
            withContext(Dispatchers.Main) { onResult(error) }
        }
    }
    fun exportSkill(id: String, uri: Uri, onResult: (String?) -> Unit) {
        launchSafe(Dispatchers.IO) {
            val error = runCatching { skillStore.exportUri(id, uri) }.exceptionOrNull()?.message
            withContext(Dispatchers.Main) { onResult(error) }
        }
    }
    fun setSkillInvocation(id: String, mode: SkillInvocation) = skillStore.setInvocation(id, mode)
    fun setSkillProject(id: String, projectId: String?) = skillStore.setProject(id, projectId)
    fun uninstallSkill(id: String) = skillStore.uninstall(id)
    fun saveContextMode(mode: ContextMode) = contextLibraryStore.saveMode(mode)
    fun deleteContextMode(id: String) = contextLibraryStore.deleteMode(id)
    fun saveKnowledgeEntry(entry: KnowledgeEntry) = contextLibraryStore.saveKnowledgeEntry(entry)
    fun deleteKnowledgeEntry(id: String) = contextLibraryStore.deleteKnowledgeEntry(id)
    fun setRequestLoggingEnabled(enabled: Boolean) = requestLogStore.setEnabled(enabled)
    fun clearRequestLogs() = requestLogStore.clear()

    fun inspectStorage(onResult: (StorageSummary) -> Unit) {
        launchSafe { runCatching { attachmentStore.inspectStorage() }.onSuccess(onResult) }
    }

    fun cleanupUnusedAttachments(onResult: (Int, StorageSummary) -> Unit) {
        launchSafe {
            val removed = attachmentStore.cleanupUnreferenced(repository.getAllAttachmentUris())
            onResult(removed, attachmentStore.inspectStorage())
        }
    }

    fun clearAppCache(onResult: (Long, StorageSummary) -> Unit) {
        launchSafe {
            val removedBytes = attachmentStore.clearCache()
            onResult(removedBytes, attachmentStore.inspectStorage())
        }
    }

    fun createBackup(output: java.io.OutputStream, password: String?, onResult: (Result<BackupSummary>) -> Unit) {
        launchSafe { onResult(runCatching { backupService.create(output, password) }) }
    }

    fun restoreBackup(input: java.io.InputStream, password: String?, onResult: (Result<BackupSummary>) -> Unit) {
        launchSafe {
            generationJob?.cancel()
            generationJob?.join()
            clearGenerationPresentation()
            onResult(runCatching { backupService.restore(input, password) })
        }
    }

    fun inspectBackup(input: java.io.InputStream, password: String?, onResult: (Result<com.dwl.mutcube.storage.BackupPreview>) -> Unit) {
        launchSafe { onResult(runCatching { backupService.inspect(input, password) }) }
    }

    fun saveRemoteBackup(
        config: RemoteBackupConfig,
        primarySecret: String,
        secondarySecret: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        launchSafe { onResult(runCatching { remoteBackupService.save(config, primarySecret, secondarySecret) }) }
    }

    fun testRemoteBackup(onResult: (Result<Unit>) -> Unit) {
        launchSafe { onResult(runCatching { remoteBackupService.test() }) }
    }

    fun uploadRemoteBackup(password: String, onResult: (Result<BackupSummary>) -> Unit) {
        launchSafe { onResult(runCatching { remoteBackupService.upload(password) }) }
    }

    fun restoreRemoteBackup(password: String?, onResult: (Result<BackupSummary>) -> Unit) {
        launchSafe {
            generationJob?.cancel()
            generationJob?.join()
            clearGenerationPresentation()
            onResult(runCatching { remoteBackupService.restore(password) })
        }
    }

    fun inspectRemoteBackup(password: String?, onResult: (Result<com.dwl.mutcube.storage.BackupPreview>) -> Unit) {
        launchSafe { onResult(runCatching { remoteBackupService.inspect(password) }) }
    }

    fun setBackupReminderDays(days: Int) = backupReminderScheduler.update(days)

    fun saveSpeechServiceSettings(
        settings: SpeechServiceSettings,
        ttsKey: String,
        asrKey: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        launchSafe { onResult(runCatching { speechService.save(settings, ttsKey, asrKey) }) }
    }

    fun synthesizeSpeech(text: String, onResult: (Result<File>) -> Unit) {
        launchSafe { onResult(runCatching { speechService.synthesize(text) }) }
    }

    fun speakNetworkSpeech(text: String, onPreparing: () -> Unit, onPlaying: () -> Unit,
        onResult: (Result<Unit>) -> Unit): kotlinx.coroutines.Job = launchSafe {
        try {
            speechService.speak(text, { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { onPreparing() } },
                { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { onPlaying() } })
            onResult(Result.success(Unit))
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { onResult(Result.failure(error)) }
    }

    fun transcribeSpeech(file: File, language: String, onResult: (Result<String>) -> Unit) {
        launchSafe { onResult(runCatching { speechService.transcribe(file, language) }) }
    }

    fun deleteTtsCredential(onResult: (Result<Unit>) -> Unit) {
        launchSafe { onResult(runCatching { speechService.deleteTtsKey() }) }
    }

    fun deleteAsrCredential(onResult: (Result<Unit>) -> Unit) {
        launchSafe { onResult(runCatching { speechService.deleteAsrKey() }) }
    }

    fun deleteMemory(id: MemoryId) {
        launchSafe { repository.deleteMemory(id) }
    }

    fun addMemory(spaceId: SpaceId?, content: String) {
        launchSafe { repository.saveMemory(spaceId, content, null) }
    }

    fun updateMemory(id: MemoryId, content: String) {
        launchSafe { repository.updateMemory(id, content) }
    }

    fun exportConversation(
        id: ConversationId,
        format: ConversationExportFormat,
        onReady: (ConversationExport) -> Unit,
        onError: (String) -> Unit,
    ) {
        launchSafe {
            runCatching { conversationExportService.export(id, format) }
                .onSuccess(onReady)
                .onFailure { onError("对话导出失败") }
        }
    }

    fun refreshSecurityDiagnostics() {
        launchSafe {
            securityReport.value = runCatching { securityDiagnostics.inspect() }.getOrNull()
        }
    }

    fun stopGeneration() {
        generationJob?.cancel(CancellationException("Generation stopped by user"))
    }

    fun retryLastGeneration() {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value || generationJob?.isActive == true || !canRetryGeneration.value) return
        val regenerationNodeId = retryRegenerationNodeId
        generationJob = launchSafe {
            sendMutex.withLock {
                modelError.value = null
                canRetryGeneration.value = false
                if (generateReply(conversationId, regenerationNodeId)) {
                    launchSafe { refreshFollowUpSuggestions(conversationId) }
                }
            }
        }
    }

    fun regenerateLastResponse(nodeId: MessageNodeId) {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value || generationJob?.isActive == true) return
        followUpSuggestions.value = emptyList()
        generationJob = launchSafe {
            sendMutex.withLock {
                val latestMessage = repository.observeMessages(conversationId).first().lastOrNull()
                if (latestMessage?.nodeId != nodeId || latestMessage.role != MessageRole.AI) return@withLock
                modelError.value = null
                canRetryGeneration.value = false
                retryRegenerationNodeId = null
                if (generateReply(conversationId, nodeId)) {
                    launchSafe { refreshFollowUpSuggestions(conversationId) }
                }
            }
        }
    }

    fun selectMessageVariant(nodeId: MessageNodeId, variantId: MessageId) {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value) return
        launchSafe {
            repository.selectMessageVariant(conversationId, nodeId, variantId)
        }
    }

    fun editUserMessage(nodeId: MessageNodeId, text: String) {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value || generationJob?.isActive == true || text.isBlank()) return
        followUpSuggestions.value = emptyList()
        generationJob = launchSafe {
            sendMutex.withLock {
                repository.editMessage(conversationId, nodeId, text)
                modelError.value = null
                canRetryGeneration.value = false
                if (generateReply(conversationId)) {
                    launchSafe { refreshFollowUpSuggestions(conversationId) }
                }
            }
        }
    }

    fun deleteMessageBranch(nodeId: MessageNodeId) {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value) return
        launchSafe { repository.deleteMessageBranch(conversationId, nodeId) }
    }

    fun forkConversation(throughNodeId: MessageNodeId) {
        val conversationId = selectedConversationId.value ?: return
        if (isGenerating.value) return
        launchSafe {
            val forkId = repository.forkConversation(conversationId, throughNodeId)
            selectConversation(forkId)
        }
    }

    fun setMessageFavorite(messageId: MessageId, favorite: Boolean) {
        val conversationId = selectedConversationId.value ?: return
        launchSafe { repository.setMessageFavorite(conversationId, messageId, favorite) }
    }

    fun translateMessage(message: ChatMessage) {
        val conversationId = selectedConversationId.value ?: return
        if (message.translation != null) {
            launchSafe { repository.setMessageTranslation(conversationId, message.id, null) }
            return
        }
        launchSafe {
            runCatching {
                chatGenerationService.translateMessage(conversationId, message.id, message.text)
            }.onFailure { error ->
                modelError.value = (error as? ChatGenerationException)?.userMessage ?: "翻译失败，请稍后重试"
                canRetryGeneration.value = false
            }
        }
    }

    fun translateStandalone(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        onResult: (Result<String>) -> Unit,
    ) {
        launchSafe {
            onResult(runCatching { chatGenerationService.translateText(text, sourceLanguage, targetLanguage) })
        }
    }

    private fun refreshCredentialState() {
        launchSafe {
            val configuration = providerProfileStore.read()
            val configured = configuration.profiles.mapNotNull { profile ->
                profile.id.takeIf {
                    runCatching { credentialStore.read(profile.id) != null }.getOrDefault(false)
                }
            }.toSet()
            configuredProviderIds.value = configured
            val cleaned = configuration.withoutUnconfiguredMimoStarterModel(configured)
            if (cleaned != configuration) providerProfileStore.write(cleaned)
        }
    }

    private suspend fun generateReply(
        conversationId: ConversationId,
        regenerationNodeId: MessageNodeId? = null,
    ): Boolean {
        val attempt = UUID.randomUUID().toString()
        activeGenerationAttempt = attempt
        isGenerating.value = true
        regeneratingNodeId.value = regenerationNodeId
        streamingText.value = ""
        val responseBuilder = StringBuilder()
        liveToolTrace.value = emptyList()
        liveToolConversationId.value = conversationId
        var lastPresentationNanos = 0L
        try {
            chatGenerationService.generateReply(conversationId, regenerationNodeId,
                onToolTrace = { if (activeGenerationAttempt == attempt) liveToolTrace.value = it }) { delta ->
                responseBuilder.append(delta)
                val now = System.nanoTime()
                val shouldRefresh = lastPresentationNanos == 0L ||
                    now - lastPresentationNanos >= STREAMING_RENDER_INTERVAL_NANOS
                if (shouldRefresh && activeGenerationAttempt == attempt) {
                    streamingText.value = responseBuilder.toString()
                    lastPresentationNanos = now
                }
            }
            if (activeGenerationAttempt == attempt) {
                retryRegenerationNodeId = null
                streamingText.value = ""
            }
            return true
        } catch (error: CancellationException) {
            if (activeGenerationAttempt == attempt) streamingText.value = ""
            throw error
        } catch (error: ChatGenerationException) {
            if (activeGenerationAttempt == attempt) {
                modelError.value = error.userMessage
                canRetryGeneration.value = error.retryable
                retryRegenerationNodeId = regenerationNodeId.takeIf { canRetryGeneration.value }
            }
            return false
        } finally {
            if (activeGenerationAttempt == attempt) {
                activeGenerationAttempt = null
                liveToolTrace.value = emptyList()
                liveToolConversationId.value = null
                isGenerating.value = false
                regeneratingNodeId.value = null
            }
        }
    }

    private suspend fun refreshFollowUpSuggestions(conversationId: ConversationId) {
        if (!displaySettingsStore.settings.value.followUpSuggestions) return
        val suggestions = try {
            chatGenerationService.generateFollowUpSuggestions(conversationId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (selectedConversationId.value == conversationId && !isGenerating.value) {
            followUpSuggestions.value = suggestions
        }
    }

    private fun clearGenerationPresentation() {
        activeGenerationAttempt = null
        generationJob?.cancel()
        generationJob = null
        liveToolTrace.value = emptyList()
        liveToolConversationId.value = null
        isGenerating.value = false
        regeneratingNodeId.value = null
        streamingText.value = ""
        modelError.value = null
        canRetryGeneration.value = false
        retryRegenerationNodeId = null
        followUpSuggestions.value = emptyList()
    }

    class Factory(
        private val repository: ConversationRepository,
        private val chatGenerationService: ChatGenerationService,
        private val credentialStore: CredentialStore,
        private val modelSettingsStore: ModelSettingsStore,
        private val providerProfileStore: ProviderProfileStore,
        private val displaySettingsStore: DisplaySettingsStore,
        private val conversationExportService: ConversationExportService,
        private val securityDiagnostics: SecurityDiagnostics,
        private val attachmentStore: AttachmentStore,
        private val backupService: BackupService,
        private val remoteBackupConfigStore: RemoteBackupConfigStore,
        private val remoteBackupService: RemoteBackupService,
        private val backupReminderStore: BackupReminderStore,
        private val backupReminderScheduler: BackupReminderScheduler,
        private val speechServiceSettingsStore: SpeechServiceSettingsStore,
        private val speechService: SpeechService,
        private val mcpServerStore: McpServerStore,
        private val mcpClientService: McpClientService,
        private val mcpOAuthManager: McpOAuthManager,
        private val extensionAuditStore: ExtensionAuditStore,
        private val toolApprovalCoordinator: ToolApprovalCoordinator,
        private val contextLibraryStore: ContextLibraryStore,
        private val skillStore: SkillStore,
        private val requestLogStore: RequestLogStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MutCubeViewModel::class.java))
            return MutCubeViewModel(
                repository,
                chatGenerationService,
                credentialStore,
                modelSettingsStore,
                providerProfileStore,
                displaySettingsStore,
                conversationExportService,
                securityDiagnostics,
                attachmentStore,
                backupService,
                remoteBackupConfigStore,
                remoteBackupService,
                backupReminderStore,
                backupReminderScheduler,
                speechServiceSettingsStore,
                speechService,
                mcpServerStore,
                mcpClientService,
                mcpOAuthManager,
                extensionAuditStore,
                toolApprovalCoordinator,
                contextLibraryStore,
                skillStore,
                requestLogStore,
            ) as T
        }
    }
}
