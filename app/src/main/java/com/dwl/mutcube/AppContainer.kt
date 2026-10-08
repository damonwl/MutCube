package com.dwl.mutcube

import android.app.Application
import android.content.Context
import com.dwl.mutcube.core.database.ConversationRepository
import com.dwl.mutcube.core.database.MutCubeDatabase
import com.dwl.mutcube.core.database.RoomConversationRepository
import com.dwl.mutcube.core.security.AndroidKeystoreCredentialStore
import com.dwl.mutcube.core.security.CredentialStore
import com.dwl.mutcube.feature.chat.ChatGenerationService
import com.dwl.mutcube.feature.chat.ConversationExportService
import com.dwl.mutcube.storage.AndroidModelSettingsStore
import com.dwl.mutcube.storage.AndroidProviderProfileStore
import com.dwl.mutcube.storage.DisplaySettingsStore
import com.dwl.mutcube.storage.SecurityDiagnostics
import com.dwl.mutcube.storage.AttachmentStore
import com.dwl.mutcube.storage.BackupService
import com.dwl.mutcube.storage.AndroidMcpServerStore
import com.dwl.mutcube.storage.ExternalMcpToolService
import com.dwl.mutcube.storage.McpOAuthManager
import com.dwl.mutcube.storage.AndroidExtensionAuditStore
import com.dwl.mutcube.storage.ToolApprovalCoordinator
import com.dwl.mutcube.storage.ContextLibraryStore
import com.dwl.mutcube.storage.SkillStore
import com.dwl.mutcube.storage.RequestLogStore
import com.dwl.mutcube.storage.LoggingModelGateway
import com.dwl.mutcube.storage.RemoteBackupConfigStore
import com.dwl.mutcube.storage.RemoteBackupService
import com.dwl.mutcube.storage.BackupReminderScheduler
import com.dwl.mutcube.storage.BackupReminderStore
import com.dwl.mutcube.storage.SpeechService
import com.dwl.mutcube.storage.InstalledTemplateStore
import com.dwl.mutcube.storage.SpeechServiceSettingsStore
import com.dwl.mutcube.core.ai.OpenAiCompatibleModelGateway
import com.dwl.mutcube.core.extensions.McpClientService

/** Application composition root; feature implementations depend on contracts, not this container. */
class AppContainer(context: Context) {
    private val database = MutCubeDatabase.create(context)

    val conversationRepository: ConversationRepository = RoomConversationRepository(database)
    val templateRepository = com.dwl.mutcube.core.database.TemplateRepository(database)
    val builtinTemplates = listOf(
        com.dwl.mutcube.template.builtin.FitnessTemplate.manifest(),
    )
    val installedTemplateStore = InstalledTemplateStore(context, templateRepository, builtinTemplates.map { it.id }.toSet())
    val availableTemplates get() = builtinTemplates + installedTemplateStore.templates.value.map { it.definition.manifest }
    val credentialStore: CredentialStore = AndroidKeystoreCredentialStore(context)
    val modelSettingsStore = AndroidModelSettingsStore(context)
    val providerProfileStore = AndroidProviderProfileStore(context)
    val displaySettingsStore = DisplaySettingsStore(context)
    val attachmentStore = AttachmentStore(context)
    val backupService = BackupService(context, database)
    val remoteBackupConfigStore = RemoteBackupConfigStore(context)
    val remoteBackupService = RemoteBackupService(context, backupService, credentialStore, remoteBackupConfigStore)
    val backupReminderStore = BackupReminderStore(context)
    val backupReminderScheduler = BackupReminderScheduler(context, backupReminderStore)
    val speechServiceSettingsStore = SpeechServiceSettingsStore(context)
    val speechService = SpeechService(context, speechServiceSettingsStore, credentialStore, providerProfileStore)
    val mcpServerStore = AndroidMcpServerStore(context)
    val mcpClientService = McpClientService()
    val mcpOAuthManager = McpOAuthManager(context, credentialStore)
    val extensionAuditStore = AndroidExtensionAuditStore(context)
    val toolApprovalCoordinator = ToolApprovalCoordinator()
    val contextLibraryStore = ContextLibraryStore(context)
    val skillStore = SkillStore(context)
    val requestLogStore = RequestLogStore(context)
    val externalMcpToolService = ExternalMcpToolService(
        mcpServerStore, mcpClientService, credentialStore, mcpOAuthManager, extensionAuditStore,
        toolApprovalCoordinator,
    )
    private val modelGateway = OpenAiCompatibleModelGateway()
    val templateRuntime = com.dwl.mutcube.template.runtime.TemplateRuntime(
        templateRepository, conversationRepository, providerProfileStore, modelSettingsStore, credentialStore, modelGateway,
    )
    val templateActions = com.dwl.mutcube.template.runtime.TemplateActions(templateRepository, templateRuntime, conversationRepository)
    val chatGenerationService = ChatGenerationService(
        repository = conversationRepository,
        modelGateway = LoggingModelGateway(modelGateway, modelGateway, requestLogStore),
        credentialStore = credentialStore,
        modelSettingsStore = modelSettingsStore,
        providerProfileStore = providerProfileStore,
        externalToolService = externalMcpToolService,
        contextInstructions = contextLibraryStore::buildContextInstructions,
        skillService = skillStore,
        scopedToolService = com.dwl.mutcube.storage.TemplateDataToolService(templateRepository, templateRuntime, conversationRepository) { availableTemplates },
    )
    val conversationExportService = ConversationExportService(conversationRepository)
    val securityDiagnostics = SecurityDiagnostics(context, conversationRepository)
}
