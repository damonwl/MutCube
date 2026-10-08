package com.dwl.mutcube

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableStateOf
import com.dwl.mutcube.ui.MutCubeApp
import com.dwl.mutcube.ui.MutCubeViewModel
import com.dwl.mutcube.ui.theme.MutCubeTheme

data class IncomingShare(
    val id: Long,
    val text: String,
    val uris: List<Uri>,
)

data class IncomingShortcut(val id: Long, val kind: String, val targetId: String)

class MainActivity : ComponentActivity() {
    private val incomingShare = mutableStateOf<IncomingShare?>(null)
    private val incomingShortcut = mutableStateOf<IncomingShortcut?>(null)

    private val viewModel: MutCubeViewModel by viewModels {
        val container = (application as MutCubeApplication).container
        MutCubeViewModel.Factory(
            repository = container.conversationRepository,
            chatGenerationService = container.chatGenerationService,
            credentialStore = container.credentialStore,
            modelSettingsStore = container.modelSettingsStore,
            providerProfileStore = container.providerProfileStore,
            displaySettingsStore = container.displaySettingsStore,
            conversationExportService = container.conversationExportService,
            securityDiagnostics = container.securityDiagnostics,
            attachmentStore = container.attachmentStore,
            backupService = container.backupService,
            remoteBackupConfigStore = container.remoteBackupConfigStore,
            remoteBackupService = container.remoteBackupService,
            backupReminderStore = container.backupReminderStore,
            backupReminderScheduler = container.backupReminderScheduler,
            speechServiceSettingsStore = container.speechServiceSettingsStore,
            speechService = container.speechService,
            mcpServerStore = container.mcpServerStore,
            mcpClientService = container.mcpClientService,
            mcpOAuthManager = container.mcpOAuthManager,
            extensionAuditStore = container.extensionAuditStore,
            toolApprovalCoordinator = container.toolApprovalCoordinator,
            contextLibraryStore = container.contextLibraryStore,
            skillStore = container.skillStore,
            requestLogStore = container.requestLogStore,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingShare.value = intent.toIncomingShareOrNull()
        incomingShortcut.value = intent.toIncomingShortcutOrNull()
        setContent {
            val settings by (application as MutCubeApplication).container.displaySettingsStore.settings
                .collectAsStateWithLifecycle()
            MutCubeTheme(themeMode = settings.themeMode, textScale = settings.textScale) {
                val background = MaterialTheme.colorScheme.background
                val darkTheme = background.luminance() < 0.5f
                SideEffect {
                    val style = SystemBarStyle.auto(
                        lightScrim = background.toArgb(),
                        darkScrim = background.toArgb(),
                        detectDarkMode = { darkTheme },
                    )
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                MutCubeApp(
                    viewModel = viewModel,
                    incomingShare = incomingShare.value,
                    onIncomingShareConsumed = { incomingShare.value = null },
                    incomingShortcut = incomingShortcut.value,
                    onIncomingShortcutConsumed = { incomingShortcut.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShare.value = intent.toIncomingShareOrNull()
        incomingShortcut.value = intent.toIncomingShortcutOrNull()
    }
}

private fun Intent.toIncomingShortcutOrNull(): IncomingShortcut? {
    val kind = getStringExtra(EXTRA_SHORTCUT_KIND) ?: return null
    val targetId = getStringExtra(EXTRA_SHORTCUT_TARGET)?.takeIf(String::isNotBlank) ?: return null
    return IncomingShortcut(System.nanoTime(), kind, targetId)
}

const val EXTRA_SHORTCUT_KIND = "com.dwl.mutcube.extra.SHORTCUT_KIND"
const val EXTRA_SHORTCUT_TARGET = "com.dwl.mutcube.extra.SHORTCUT_TARGET"

@Suppress("DEPRECATION")
private fun Intent.toIncomingShareOrNull(): IncomingShare? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val sharedText = getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
    val streamUris = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }
    val clipUris = buildList {
        val clips = clipData ?: return@buildList
        repeat(clips.itemCount) { index -> clips.getItemAt(index).uri?.let(::add) }
    }
    val uris = (streamUris + clipUris).distinct()
    if (sharedText.isEmpty() && uris.isEmpty()) return null
    return IncomingShare(System.nanoTime(), sharedText, uris)
}
