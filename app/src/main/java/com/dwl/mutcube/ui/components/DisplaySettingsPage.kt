package com.dwl.mutcube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.DisplaySettings
import com.dwl.mutcube.storage.ThemeMode
import com.dwl.mutcube.storage.StartupMode
import com.dwl.mutcube.storage.ChatBackground
import com.dwl.mutcube.storage.BubbleStyle

@Composable
fun DisplaySettingsPage(
    settings: DisplaySettings,
    onBack: () -> Unit,
    onSave: (DisplaySettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsPageHeader("显示与聊天行为", onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text("主题", modifier = Modifier.padding(top = 8.dp))
            listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色",
            ).forEach { (mode, label) ->
                SettingChoice(label, draft.themeMode == mode) { draft = draft.copy(themeMode = mode) }
            }
            Text("文字大小", modifier = Modifier.padding(top = 18.dp))
            listOf(0.9f to "紧凑", 1f to "标准", 1.15f to "较大").forEach { (scale, label) ->
                SettingChoice(label, draft.textScale == scale) { draft = draft.copy(textScale = scale) }
            }
            Text("聊天背景", modifier = Modifier.padding(top = 18.dp))
            listOf(
                ChatBackground.DEFAULT to "默认",
                ChatBackground.WARM to "暖灰",
                ChatBackground.COOL to "冷灰",
            ).forEach { (background, label) ->
                SettingChoice(label, draft.chatBackground == background) { draft = draft.copy(chatBackground = background) }
            }
            Text("消息气泡", modifier = Modifier.padding(top = 18.dp))
            listOf(
                BubbleStyle.STANDARD to "标准",
                BubbleStyle.TONAL to "柔和填充",
                BubbleStyle.MINIMAL to "极简",
            ).forEach { (style, label) ->
                SettingChoice(label, draft.bubbleStyle == style) { draft = draft.copy(bubbleStyle = style) }
            }
            SettingSwitch("显示消息头像", draft.showMessageAvatars) {
                draft = draft.copy(showMessageAvatars = it)
            }
            SettingSwitch("显示回复模型标识", draft.showModelLabel) {
                draft = draft.copy(showModelLabel = it)
            }
            SettingSwitch("显示消息时间", draft.showMessageTime) {
                draft = draft.copy(showMessageTime = it)
            }
            SettingSwitch("新消息自动滚动到底部", draft.autoScroll) {
                draft = draft.copy(autoScroll = it)
            }
            Text("启动方式", modifier = Modifier.padding(top = 22.dp))
            SettingChoice("打开最近一次对话", draft.startupMode == StartupMode.LAST_CONVERSATION) {
                draft = draft.copy(startupMode = StartupMode.LAST_CONVERSATION)
            }
            SettingChoice("进入空白新对话", draft.startupMode == StartupMode.NEW_CONVERSATION) {
                draft = draft.copy(startupMode = StartupMode.NEW_CONVERSATION)
            }
            Text("输入与生成", modifier = Modifier.padding(top = 18.dp))
            SettingSwitch("回车直接发送", draft.enterToSend) { draft = draft.copy(enterToSend = it) }
            SettingSwitch("AI 生成后续问题建议", draft.followUpSuggestions) {
                draft = draft.copy(followUpSuggestions = it)
            }
        }
        Button(
            onClick = { onSave(draft); onBack() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) { Text("保存") }
    }
}

@Composable
private fun SettingChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
