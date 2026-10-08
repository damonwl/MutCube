package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.SecurityDiagnosticReport

@Composable
fun SecurityDiagnosticsPage(
    report: SecurityDiagnosticReport?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsPageHeader("数据与安全诊断", onBack)
        if (report == null) {
            CircularProgressIndicator(modifier = Modifier.padding(22.dp))
        } else {
            Column(modifier = Modifier.weight(1f).padding(horizontal = 22.dp)) {
                DiagnosticRow("系统云备份", if (report.backupDisabled) "已禁用" else "需要关闭")
                DiagnosticRow("会话数据库", if (report.databasePresent) formatBytes(report.databaseBytes) else "尚未创建")
                DiagnosticRow("Keystore 主密钥", if (report.keystoreKeyPresent) "存在" else "尚未创建")
                DiagnosticRow("加密凭据", "${report.encryptedCredentialPairs} 组")
                DiagnosticRow("附件文件", "${report.attachmentFiles} 个 · ${formatBytes(report.attachmentBytes)}")
                DiagnosticRow(
                    "孤立附件",
                    if (report.orphanAttachmentFiles == 0) "未发现" else "${report.orphanAttachmentFiles} 个待清理",
                )
                Text(
                    "诊断只读取元数据，不读取、解密或显示 API Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
        Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text("重新检查") }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
