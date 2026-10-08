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
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.BackupSummary
import com.dwl.mutcube.storage.RemoteBackupConfig
import com.dwl.mutcube.storage.RemoteBackupType
import java.text.DateFormat
import java.util.Date

@Composable
fun RemoteBackupPage(
    config: RemoteBackupConfig,
    onBack: () -> Unit,
    onSave: (RemoteBackupConfig, String, String, (Result<Unit>) -> Unit) -> Unit,
    onTest: ((Result<Unit>) -> Unit) -> Unit,
    onUpload: (String, (Result<BackupSummary>) -> Unit) -> Unit,
    onRestore: (String) -> Unit,
    onNotice: (String) -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }
    var primarySecret by remember(config.type) { mutableStateOf("") }
    var secondarySecret by remember(config.type) { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun saveThen(action: (() -> Unit)? = null) {
        busy = true
        onSave(draft, primarySecret, secondarySecret) { result ->
            result.onSuccess {
                primarySecret = ""
                secondarySecret = ""
                if (action == null) {
                    busy = false
                    onNotice("远程备份配置已保存")
                } else action()
            }.onFailure {
                busy = false
                onNotice("保存失败：${it.message ?: "未知错误"}")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("远程备份", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("存储类型", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.padding(top = 10.dp)) {
                        RemoteBackupType.entries.forEachIndexed { index, type ->
                            FilterChip(
                                selected = draft.type == type,
                                onClick = { draft = draft.copy(type = type) },
                                label = { Text(if (type == RemoteBackupType.WEBDAV) "WebDAV" else "S3 兼容存储") },
                            )
                            if (index == 0) Spacer(Modifier.size(8.dp))
                        }
                    }
                    Text(
                        "仅允许 HTTPS；访问密钥使用 Android Keystore 加密，不会进入备份文件。新上传的远程备份必须加密，旧版未加密备份仍可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    OutlinedTextField(
                        value = draft.endpoint,
                        onValueChange = { draft = draft.copy(endpoint = it) },
                        label = { Text(if (draft.type == RemoteBackupType.WEBDAV) "WebDAV 根地址" else "S3 Endpoint") },
                        supportingText = {
                            Text(if (draft.type == RemoteBackupType.WEBDAV) "例如 https://dav.example.com/remote.php/dav/files/name" else "例如 https://s3.us-east-1.amazonaws.com")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (draft.type == RemoteBackupType.WEBDAV) {
                        OutlinedTextField(draft.username, { draft = draft.copy(username = it) }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(
                            primarySecret, { primarySecret = it }, label = { Text("密码") },
                            placeholder = { Text("留空表示不修改") }, visualTransformation = PasswordVisualTransformation(),
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    } else {
                        OutlinedTextField(draft.bucket, { draft = draft.copy(bucket = it) }, label = { Text("Bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(draft.region, { draft = draft.copy(region = it) }, label = { Text("Region") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        OutlinedTextField(
                            primarySecret, { primarySecret = it }, label = { Text("Access Key") },
                            placeholder = { Text("留空表示不修改") }, visualTransformation = PasswordVisualTransformation(),
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            secondarySecret, { secondarySecret = it }, label = { Text("Secret Key") },
                            placeholder = { Text("留空表示不修改") }, visualTransformation = PasswordVisualTransformation(),
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        draft.remotePath, { draft = draft.copy(remotePath = it) }, label = { Text("远程文件路径") },
                        supportingText = { Text("同一路径会保留最新的一份完整备份") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(enabled = !busy, onClick = { saveThen() }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text("保存配置")
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            saveThen {
                                onTest { result ->
                                    busy = false
                                    onNotice(result.fold({ "连接成功" }, { "连接失败：${it.message ?: "未知错误"}" }))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(Icons.Rounded.CloudDone, null)
                        Text("保存并测试连接", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Spacer(Modifier.size(14.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("立即执行", style = MaterialTheme.typography.titleMedium)
                    Text(
                        draft.lastBackupAt?.let { "上次备份：${DateFormat.getDateTimeInstance().format(Date(it))}" } ?: "尚未上传远程备份",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp, bottom = 12.dp),
                    )
                    OutlinedTextField(
                        backupPassword, { backupPassword = it }, label = { Text("备份加密密码") },
                        supportingText = { Text("至少 12 个字符；不会保存在设备或上传到服务商，丢失后无法恢复备份。") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        backupPasswordConfirm, { backupPasswordConfirm = it }, label = { Text("再次输入备份密码（上传时）") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                    )
                    Button(
                        enabled = !busy && backupPassword.length >= 12 && backupPassword == backupPasswordConfirm,
                        onClick = {
                            saveThen {
                                onUpload(backupPassword) { result ->
                                    busy = false
                                    onNotice(result.fold({ "远程备份完成：${it.entryCount} 个数据项" }, { "备份失败：${it.message ?: "未知错误"}" }))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudUpload, null)
                        Text("上传当前数据", modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(enabled = !busy, onClick = { onRestore(backupPassword) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Rounded.CloudDownload, null)
                        Text("从远程备份恢复", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
