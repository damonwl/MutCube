package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.StorageSummary

@Composable
fun DataManagementPage(
    onBack: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onRemoteBackup: () -> Unit,
    backupReminderDays: Int,
    onBackupReminderDaysChange: (Int) -> Unit,
    onSecurityDiagnostics: () -> Unit,
    onInspectStorage: ((StorageSummary) -> Unit) -> Unit,
    onCleanupUnusedAttachments: ((Int, StorageSummary) -> Unit) -> Unit,
    onClearCache: ((Long, StorageSummary) -> Unit) -> Unit,
    onNotice: (String) -> Unit,
    onTemplateData: () -> Unit,
) {
    var storage by remember { mutableStateOf<StorageSummary?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onInspectStorage { storage = it } }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("数据与备份", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            OutlinedButton(onClick = onTemplateData, modifier = Modifier.fillMaxWidth()) { Text("模板数据与授权") }
            Spacer(Modifier.size(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("完整本地备份", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "备份会话、项目、模板数据、设置、附件、头像和 Skills。API Key 不会写入备份。可设置备份密码；留空生成未加密 ZIP。恢复前会预览并校验内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    Button(onClick = onCreateBackup, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FileUpload, null)
                        Text("创建备份", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Rounded.FileDownload, null)
                        Text("从备份恢复", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(onClick = onRemoteBackup, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Rounded.CloudSync, null)
                    Text("远程备份", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text("WebDAV 与 S3 兼容存储，支持连接测试、上传和恢复", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("备份提醒", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "由系统在约定周期附近提醒，不会在后台自动上传数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                    Row(Modifier.padding(top = 10.dp)) {
                        listOf(0 to "关闭", 7 to "7 天", 14 to "14 天", 30 to "30 天").forEachIndexed { index, option ->
                            FilterChip(
                                selected = backupReminderDays == option.first,
                                onClick = { onBackupReminderDaysChange(option.first) },
                                label = { Text(option.second) },
                            )
                            if (index < 3) Spacer(Modifier.size(8.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Rounded.Folder, null)
                    Text("文件存储", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        storage?.let {
                            "${it.attachmentCount} 个附件 · ${it.attachmentBytes.readableBytes()}，缓存 ${it.cacheBytes.readableBytes()}"
                        } ?: "正在统计…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            onCleanupUnusedAttachments { count, summary ->
                                storage = summary
                                busy = false
                                onNotice("已清理 $count 个未引用附件")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null)
                        Text("清理未引用附件", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            onClearCache { bytes, summary ->
                                storage = summary
                                busy = false
                                onNotice("已清理 ${bytes.readableBytes()} 缓存")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("清理应用缓存") }
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(onClick = onSecurityDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Rounded.Security, null)
                    Text("数据与安全诊断", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text("检查数据库、加密凭据和附件一致性", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun Long.readableBytes(): String = when {
    this >= 1024L * 1024 * 1024 -> "%.1f GB".format(this / (1024.0 * 1024 * 1024))
    this >= 1024L * 1024 -> "%.1f MB".format(this / (1024.0 * 1024))
    this >= 1024L -> "%.1f KB".format(this / 1024.0)
    else -> "$this B"
}
