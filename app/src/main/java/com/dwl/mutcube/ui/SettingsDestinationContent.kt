package com.dwl.mutcube.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.dwl.mutcube.core.model.SpaceId
import com.dwl.mutcube.ui.components.UserProfilePage
import com.dwl.mutcube.ui.components.ModelSettingsPage
import com.dwl.mutcube.ui.components.MemoryManagementPage
import com.dwl.mutcube.ui.components.McpSettingsPage
import com.dwl.mutcube.ui.components.DisplaySettingsPage
import com.dwl.mutcube.ui.components.DataManagementPage
import com.dwl.mutcube.ui.components.RemoteBackupPage
import com.dwl.mutcube.ui.components.SecurityDiagnosticsPage
import com.dwl.mutcube.ui.components.SettingsHomePage
import com.dwl.mutcube.ui.components.TrashPage
import com.dwl.mutcube.ui.components.ContextLibraryPage
import com.dwl.mutcube.ui.components.SkillManagementPage
import com.dwl.mutcube.ui.components.DiagnosticsStatsPage
import com.dwl.mutcube.ui.components.SpeechSettingsPage
import com.dwl.mutcube.ui.components.NotificationSettingsPage
import com.dwl.mutcube.ui.components.DeveloperSettingsPage
import com.dwl.mutcube.ui.components.AboutPage
import com.dwl.mutcube.ui.components.ThirdPartyLicensesPage
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.storage.UpdateChecker
import kotlinx.coroutines.launch

internal enum class SettingsDestination {
    HOME, MODEL, DISPLAY, MEMORY, TRASH, DATA, SECURITY, EXTENSIONS, CONTEXT, SKILLS, STATS, SPEECH,
    NOTIFICATIONS, DEVELOPER, ABOUT, REMOTE_BACKUP, LICENSES, PROFILE,
}

internal data class SettingsHostActions(
    val onResetMemoryScope: () -> Unit,
    val onImportProviders: () -> Unit,
    val onExportProviders: () -> Unit,
    val onTemplateData: () -> Unit,
    val onCreateBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onNotificationPermission: () -> Unit,
    val onRemoteRestore: (String?) -> Unit,
    val onAuthorize: (McpServer) -> Unit,
)

/** Settings routing consumes screen state; platform launchers stay in the application host. */
@Composable
internal fun SettingsDestinationContent(
    destination: SettingsDestination,
    uiState: MutCubeUiState,
    viewModel: MutCubeViewModel,
    onBack: () -> Unit,
    onNavigate: (SettingsDestination) -> Unit,
    actions: SettingsHostActions,
    settingsStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    memoryInitialProjectId: SpaceId?,
    installedSkills: List<com.dwl.mutcube.storage.InstalledSkill>,
    skillAudit: List<com.dwl.mutcube.storage.SkillAuditEvent>,
    engines: List<Pair<String, String>>,
    voices: List<Pair<String, String>>,
    templateBindings: List<com.dwl.mutcube.core.database.TemplateBindingEntity>,
    templateDefinitions: List<com.dwl.mutcube.template.core.TemplateManifest>,
    templateRepository: com.dwl.mutcube.core.database.TemplateRepository,
    onNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when (destination) {
        SettingsDestination.HOME -> settingsStateHolder.SaveableStateProvider("settings-home") {
            SettingsHomePage(
            providerSummary = uiState.providerConfiguration.activeProfile.let { "${it.name} · ${it.modelId}" },
            onBack = onBack,
            onModelSettings = {
                viewModel.clearProviderInspection()
                onNavigate(SettingsDestination.MODEL)
            },
            onDisplaySettings = { onNavigate(SettingsDestination.DISPLAY) },
            onMemoryManagement = {
                actions.onResetMemoryScope()
                onNavigate(SettingsDestination.MEMORY)
            },
            onTrash = { onNavigate(SettingsDestination.TRASH) },
            onDataManagement = { onNavigate(SettingsDestination.DATA) },
            onExtensions = { onNavigate(SettingsDestination.EXTENSIONS) },
            onContextLibrary = { onNavigate(SettingsDestination.CONTEXT) },
            onSkills = { onNavigate(SettingsDestination.SKILLS) },
            onDiagnosticsStats = { onNavigate(SettingsDestination.STATS) },
            onSpeechSettings = { onNavigate(SettingsDestination.SPEECH) },
            onNotificationSettings = { onNavigate(SettingsDestination.NOTIFICATIONS) },
            onDeveloperSettings = { onNavigate(SettingsDestination.DEVELOPER) },
            onAbout = { onNavigate(SettingsDestination.ABOUT) },
            onProfileSettings = { onNavigate(SettingsDestination.PROFILE) },
            )
        }
        SettingsDestination.MODEL -> ModelSettingsPage(
            effectiveProviderId = uiState.effectiveProviderId,
            configuredProviderIds = uiState.configuredProviderIds,
            settings = uiState.modelSettings,
            providers = uiState.providerConfiguration,
            inspectionLoading = uiState.providerInspectionLoading,
            inspectionMessage = uiState.providerInspectionMessage,
            discoveredModels = uiState.discoveredModels,
            onBack = onBack,
            onSaveProvider = viewModel::saveProviderProfile,
            onInspectProvider = viewModel::inspectProvider,
            onSelectProvider = viewModel::selectProviderProfile,
            onProviderEnabledChange = viewModel::setProviderEnabled,
            onMoveProvider = viewModel::moveProvider,
            onDeleteProvider = viewModel::deleteProviderProfile,
            onDeleteCredential = viewModel::deleteProviderCredential,
            onSaveSettings = viewModel::saveModelSettings,
            onImport = actions.onImportProviders,
            onExport = actions.onExportProviders,
            onShare = {
                viewModel.exportProviderConfiguration { content ->
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_TEXT, content)
                            },
                            "分享 Provider 配置（不含 API Key）",
                        ),
                    )
                }
            },
        )
        SettingsDestination.PROFILE -> UserProfilePage(
            settings = uiState.displaySettings,
            onBack = onBack,
            onSave = viewModel::saveUserProfile,
        )
        SettingsDestination.DISPLAY -> DisplaySettingsPage(
            settings = uiState.displaySettings,
            onBack = onBack,
            onSave = viewModel::saveDisplaySettings,
        )
        SettingsDestination.MEMORY -> MemoryManagementPage(
            memories = uiState.memories,
            spaces = uiState.spaces,
            onDelete = viewModel::deleteMemory,
            onAdd = viewModel::addMemory,
            onUpdate = viewModel::updateMemory,
            onBack = onBack,
            initialSpaceId = memoryInitialProjectId,
        )
        SettingsDestination.TRASH -> TrashPage(
            conversations = uiState.trashedConversations,
            onBack = onBack,
            onRestore = viewModel::restoreConversation,
            onRestoreAll = viewModel::restoreAllConversations,
            onDeletePermanently = viewModel::permanentlyDeleteConversation,
            onEmpty = viewModel::emptyTrash,
        )
        SettingsDestination.DATA -> DataManagementPage(
            onTemplateData = actions.onTemplateData,
            onBack = onBack,
            onCreateBackup = actions.onCreateBackup,
            onRestoreBackup = actions.onRestoreBackup,
            onRemoteBackup = { onNavigate(SettingsDestination.REMOTE_BACKUP) },
            backupReminderDays = uiState.backupReminderDays,
            onBackupReminderDaysChange = { days ->
                viewModel.setBackupReminderDays(days)
                if (days > 0 && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    actions.onNotificationPermission()
                }
                onNotice(if (days == 0) "已关闭备份提醒" else "已设为每 $days 天提醒")
            },
            onSecurityDiagnostics = {
                viewModel.refreshSecurityDiagnostics()
                onNavigate(SettingsDestination.SECURITY)
            },
            onInspectStorage = viewModel::inspectStorage,
            onCleanupUnusedAttachments = viewModel::cleanupUnusedAttachments,
            onClearCache = viewModel::clearAppCache,
            onNotice = { notice -> onNotice(notice) },
        )
        SettingsDestination.SECURITY -> SecurityDiagnosticsPage(
            report = uiState.securityReport,
            onRefresh = viewModel::refreshSecurityDiagnostics,
            onBack = onBack,
        )
        SettingsDestination.REMOTE_BACKUP -> RemoteBackupPage(
            config = uiState.remoteBackupConfig,
            onBack = onBack,
            onSave = viewModel::saveRemoteBackup,
            onTest = viewModel::testRemoteBackup,
            onUpload = viewModel::uploadRemoteBackup,
            onRestore = actions.onRemoteRestore,
            onNotice = { notice -> onNotice(notice) },
        )
        SettingsDestination.EXTENSIONS -> McpSettingsPage(
            servers = uiState.mcpServers,
            busyServerId = uiState.mcpBusyServerId,
            onBack = onBack,
            onSaveServer = viewModel::saveMcpServer,
            onDeleteServer = viewModel::deleteMcpServer,
            onInspect = viewModel::inspectMcpServer,
            onUpdateTool = viewModel::updateMcpTool,
            onAuthorize = actions.onAuthorize,
            auditEvents = uiState.extensionAuditEvents,
            onClearAudit = viewModel::clearExtensionAudit,
        )
        SettingsDestination.CONTEXT -> ContextLibraryPage(
            state = uiState.contextLibrary,
            onBack = onBack,
            onSavePrompt = viewModel::saveQuickPrompt,
            onDeletePrompt = viewModel::deleteQuickPrompt,
            onSaveMode = viewModel::saveContextMode,
            onDeleteMode = viewModel::deleteContextMode,
            onSaveKnowledge = viewModel::saveKnowledgeEntry,
            onDeleteKnowledge = viewModel::deleteKnowledgeEntry,
        )
        SettingsDestination.SKILLS -> SkillManagementPage(
            installed = installedSkills,
            audit = skillAudit,
            spaces = uiState.spaces,
            onBack = onBack,
            onImport = viewModel::importSkill,
            onExport = viewModel::exportSkill,
            onCreate = viewModel::createSkill,
            onInvocation = viewModel::setSkillInvocation,
            onProject = viewModel::setSkillProject,
            onUninstall = viewModel::uninstallSkill,
        )
        SettingsDestination.STATS -> DiagnosticsStatsPage(
            messages = uiState.messages,
            logs = uiState.requestLogs,
            developerMode = uiState.displaySettings.developerMode,
            onBack = onBack,
            onLoggingEnabled = viewModel::setRequestLoggingEnabled,
            onClearLogs = viewModel::clearRequestLogs,
        )
        SettingsDestination.SPEECH -> SpeechSettingsPage(
            settings = uiState.displaySettings,
            serviceSettings = uiState.speechServiceSettings,
            providers = uiState.providerConfiguration,
            engines = engines,
            voices = voices,
            onBack = onBack,
            onSave = { display, service, ttsKey, asrKey, callback ->
                viewModel.saveDisplaySettings(display)
                viewModel.saveSpeechServiceSettings(service, ttsKey, asrKey, callback)
            },
            onDeleteTtsKey = viewModel::deleteTtsCredential,
            onDeleteAsrKey = viewModel::deleteAsrCredential,
            onNotice = { notice -> onNotice(notice) },
        )
        SettingsDestination.NOTIFICATIONS -> NotificationSettingsPage(
            settings = uiState.displaySettings,
            onBack = onBack,
            onSave = { settings ->
                viewModel.saveDisplaySettings(settings)
                if (settings.completionNotification && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    actions.onNotificationPermission()
                }
            },
        )
        SettingsDestination.DEVELOPER -> DeveloperSettingsPage(
            settings = uiState.displaySettings,
            requestLoggingEnabled = uiState.requestLogs.enabled,
            templateResetTargets = templateBindings.filter { it.enabled }.mapNotNull { binding ->
                uiState.spaces.firstOrNull { it.id.value == binding.projectId }?.let { project ->
                    com.dwl.mutcube.ui.components.TemplateResetTarget(binding.projectId, binding.templateId,
                        project.name, templateDefinitions.firstOrNull { it.id == binding.templateId }?.name ?: binding.templateId)
                }
            },
            onPreviewTemplateReset = { target ->
                check(uiState.displaySettings.developerMode) { "请先启用并保存开发者模式" }
                templateRepository.previewDeveloperReset(
                    com.dwl.mutcube.core.model.TemplateAccessContext(target.projectId, target.templateId))
            },
            onResetTemplate = { target, projectName ->
                check(uiState.displaySettings.developerMode) { "请先启用并保存开发者模式" }
                templateRepository.resetForDeveloper(
                    com.dwl.mutcube.core.model.TemplateAccessContext(target.projectId, target.templateId), projectName)
            },
            onBack = onBack,
            onSave = { settings, logging ->
                viewModel.saveDisplaySettings(settings)
                viewModel.setRequestLoggingEnabled(settings.developerMode && logging)
            },
        )
        SettingsDestination.ABOUT -> AboutPage(
            versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知",
            onBack = onBack,
            onCheckUpdate = { callback ->
                scope.launch { callback(runCatching { UpdateChecker.check() }) }
            },
            onOpenReleasePage = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            },
            onOpenLicenses = { onNavigate(SettingsDestination.LICENSES) },
        )
        SettingsDestination.LICENSES -> ThirdPartyLicensesPage(
            onBack = onBack,
            onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },
        )
    }
}
