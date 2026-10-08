package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class SettingEntry(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val onClick: () -> Unit,
)

@Composable
fun SettingsHomePage(
    providerSummary: String,
    onBack: () -> Unit,
    onModelSettings: () -> Unit,
    onDisplaySettings: () -> Unit,
    onMemoryManagement: () -> Unit,
    onDataManagement: () -> Unit,
    onTrash: () -> Unit,
    onExtensions: () -> Unit,
    onContextLibrary: () -> Unit,
    onSkills: () -> Unit,
    onDiagnosticsStats: () -> Unit,
    onSpeechSettings: () -> Unit,
    onNotificationSettings: () -> Unit,
    onDeveloperSettings: () -> Unit,
    onAbout: () -> Unit,
    onProfileSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = listOf(
        "个人" to listOf(SettingEntry(Icons.Rounded.Person, "个人信息", "设置头像与昵称", onProfileSettings)),
        "模型服务" to listOf(
            SettingEntry(Icons.Rounded.SmartToy, "模型与 Provider", providerSummary, onModelSettings),
        ),
        "扩展" to listOf(
            SettingEntry(Icons.Rounded.Extension, "MCP 与工具", "服务连接、工具启停与逐项授权", onExtensions),
            SettingEntry(Icons.Rounded.Bolt, "Skills", "安装、启用与项目范围", onSkills),
            SettingEntry(Icons.Rounded.Bolt, "提示词与上下文", "快捷提示词、对话模式与知识条目", onContextLibrary),
        ),
        "对话" to listOf(
            SettingEntry(Icons.Rounded.Memory, "记忆管理", "查看和删除全局或项目记忆", onMemoryManagement),
            SettingEntry(Icons.Rounded.Palette, "显示与聊天行为", "主题、字号、消息时间和自动滚动", onDisplaySettings),
            SettingEntry(Icons.Rounded.RecordVoiceOver, "语音", "TTS 朗读与 ASR 语音输入", onSpeechSettings),
            SettingEntry(Icons.Rounded.Notifications, "通知与反馈", "生成完成通知与振动反馈", onNotificationSettings),
        ),
        "数据" to listOf(
            SettingEntry(Icons.Rounded.QueryStats, "统计与调试", "对话 Token 统计与脱敏请求日志", onDiagnosticsStats),
            SettingEntry(Icons.Rounded.DeleteOutline, "回收站", "恢复或永久删除已移除的对话", onTrash),
            SettingEntry(Icons.Rounded.DataUsage, "数据与备份", "完整备份、恢复与安全诊断", onDataManagement),
        ),
        "应用" to listOf(
            SettingEntry(Icons.Rounded.DeveloperMode, "开发者选项", "脱敏日志与高级诊断开关", onDeveloperSettings),
            SettingEntry(Icons.Rounded.Info, "关于 MutCube", "版本、许可与更新信息", onAbout),
        ),
    )
    val filteredSections = sections.mapNotNull { (title, entries) ->
        val visible = entries.filter { entry ->
            query.isBlank() || entry.title.contains(query, true) || entry.summary.contains(query, true)
        }
        (title to visible).takeIf { visible.isNotEmpty() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsPageHeader("设置", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("搜索设置") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        )
        filteredSections.forEach { (sectionTitle, entries) ->
            SettingsSection(sectionTitle) {
                entries.forEachIndexed { index, entry ->
                    SettingsRow(entry.icon, entry.title, entry.summary, entry.onClick)
                    if (index != entries.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 60.dp))
                }
            }
        }
        if (filteredSections.isEmpty()) {
            Text(
                "没有匹配的设置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(30.dp),
            )
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
