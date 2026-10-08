package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.DisplaySettings
import com.dwl.mutcube.storage.ReleaseInfo

@Composable
fun NotificationSettingsPage(
    settings: DisplaySettings,
    onBack: () -> Unit,
    onSave: (DisplaySettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    SettingsForm("通知与反馈", onBack, onSave = { onSave(draft) }) {
        SettingSwitch("生成完成时振动", draft.completionVibration) {
            draft = draft.copy(completionVibration = it)
        }
        SettingSwitch("允许后台持续生成并通知", draft.completionNotification) {
            draft = draft.copy(completionNotification = it)
        }
        Text(
            "启用后，生成期间由低打扰的前台任务保护；完成后发送通知。Android 13 及以上会请求通知权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
fun DeveloperSettingsPage(
    settings: DisplaySettings,
    requestLoggingEnabled: Boolean,
    onBack: () -> Unit,
    onSave: (DisplaySettings, Boolean) -> Unit,
    templateResetTargets: List<TemplateResetTarget> = emptyList(),
    onPreviewTemplateReset: suspend (TemplateResetTarget) -> com.dwl.mutcube.core.database.TemplateResetPreview,
    onResetTemplate: suspend (TemplateResetTarget, String) -> com.dwl.mutcube.core.database.TemplateResetPreview,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var logging by remember(requestLoggingEnabled) { mutableStateOf(requestLoggingEnabled) }
    SettingsForm("开发者选项", onBack, onSave = { onSave(draft, logging) }) {
        SettingSwitch("启用开发者模式", draft.developerMode) {
            draft = draft.copy(developerMode = it)
            if (!it) logging = false
        }
        if (draft.developerMode) SettingSwitch("记录脱敏请求日志", logging) { logging = it }
        if (draft.developerMode) TemplateDeveloperReset(
            enabled = settings.developerMode,
            targets = templateResetTargets,
            onPreview = onPreviewTemplateReset,
            onReset = onResetTemplate,
        )
        Text(
            if (draft.developerMode) {
                "请求日志只保存端点、协议、模型、Token、耗时和脱敏错误，不保存提示词、回复正文或 API Key。"
            } else {
                "开启后可记录和查看请求诊断；关闭开发者模式会同时停止日志采集。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
fun AboutPage(
    versionName: String,
    onBack: () -> Unit,
    onCheckUpdate: ((Result<ReleaseInfo>) -> Unit) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onOpenLicenses: () -> Unit,
) {
    var update by remember { mutableStateOf<Result<ReleaseInfo>?>(null) }
    var checking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("关于 MutCube", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("MutCube", style = MaterialTheme.typography.headlineSmall)
                    Text("版本 $versionName", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "一个面向多种 AI 服务形态、项目与模板的本地优先客户端。",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Text("开源许可", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            LicenseCard("MutCube", "MIT License")
            Button(onClick = onOpenLicenses, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("查看完整的第三方依赖与许可")
            }
            Text("更新", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        when {
                            checking -> "正在检查…"
                            update == null -> "通过 Gitee Releases 检查新版本"
                            update?.isSuccess == true -> update?.getOrNull()?.message.orEmpty()
                            else -> "检查失败：${update?.exceptionOrNull()?.message ?: "网络不可用"}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            onCheckUpdate { result -> update = result; checking = false }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) { Text("检查更新") }
                    update?.getOrNull()?.let { release ->
                        androidx.compose.material3.TextButton(
                            onClick = { onOpenReleasePage(release.pageUrl) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("打开发布页面") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(name: String, license: String) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsForm(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) { content() }
        Button(
            onClick = { onSave(); onBack() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text("保存") }
    }
}
