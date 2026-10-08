package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val translationLanguages = listOf("自动检测", "简体中文", "繁体中文", "英语", "日语", "韩语", "法语", "德语", "西班牙语")

@Composable
fun TranslationPage(
    onBack: () -> Unit,
    onTranslate: (String, String, String, (Result<String>) -> Unit) -> Unit,
    onCopy: (String) -> Unit,
) {
    var source by remember { mutableStateOf("自动检测") }
    var target by remember { mutableStateOf("简体中文") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("翻译", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguagePicker(source, translationLanguages, { source = it }, Modifier.weight(1f))
                IconButton(onClick = {
                    if (source != "自动检测") {
                        val previous = source; source = target; target = previous
                        val previousText = input; input = output; output = previousText
                    }
                }) { Icon(Icons.Rounded.SwapHoriz, "交换语言") }
                LanguagePicker(target, translationLanguages.drop(1), { target = it }, Modifier.weight(1f))
            }
            OutlinedTextField(
                input, { input = it.take(20_000) }, label = { Text("原文") }, minLines = 7,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            if (output.isNotBlank()) {
                OutlinedTextField(
                    output, {}, readOnly = true, label = { Text("译文") }, minLines = 7,
                    trailingIcon = { IconButton(onClick = { onCopy(output) }) { Icon(Icons.Rounded.ContentCopy, "复制译文") } },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(20.dp))
        }
        Button(
            enabled = input.isNotBlank() && !loading,
            onClick = {
                loading = true; error = null
                onTranslate(input, source, target) { result ->
                    loading = false
                    result.onSuccess { output = it }.onFailure { error = it.message ?: "翻译失败" }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(18.dp),
        ) { Text(if (loading) "正在翻译…" else "翻译") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    value: String,
    values: List<String>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value, {}, readOnly = true, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            values.forEach { language ->
                DropdownMenuItem({ Text(language) }, { onChange(language); expanded = false })
            }
        }
    }
}
