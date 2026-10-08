package com.dwl.mutcube.ui

import androidx.compose.material.icons.rounded.ViewInAr
import android.graphics.drawable.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class TemplatePreview(val id: String, val name: String, val description: String, val icon: ImageVector)

@Composable
internal fun TemplateSurface(
    templates: List<com.dwl.mutcube.template.core.TemplateManifest>,
    onManage: () -> Unit,
    projectCount: Int,
    boundProjectNames: Map<String, List<String>> = emptyMap(),
    onBindTemplate: (TemplatePreview) -> Unit = {},
    query: String,
    showSearch: Boolean,
    onQueryChange: (String) -> Unit,
    onTemplateClick: (TemplatePreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previews = templates.map { manifest ->
        TemplatePreview(manifest.id, manifest.name,
            "独立服务界面、项目数据与 AI 协作",
            if (manifest.id == "mutcube.fitness") Icons.Rounded.FitnessCenter else Icons.Rounded.ViewInAr)
    }
    val filtered = previews.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true)
    }
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 14.dp)) {
        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索模板") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
        }
        Text(
            "模板",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = if (showSearch) 22.dp else 8.dp),
        )
        Text(
            "让 AI 以更合适的服务界面出现",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        TextButton(onClick = onManage, modifier = Modifier.padding(bottom = 12.dp)) { Text("已安装模板与本地导入") }
        filtered.forEach { item ->
            val projects = boundProjectNames[item.id].orEmpty()
            TemplateCard(item, projects, onClick = { onTemplateClick(item) },
                onBind = if (projects.isNotEmpty() && projectCount > 1) ({ onBindTemplate(item) }) else null)
        }
    }
}

@Composable
private fun TemplateCard(item: TemplatePreview, projects: List<String>, onClick: () -> Unit, onBind: (() -> Unit)? = null) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(item.icon, contentDescription = null, modifier = Modifier.padding(14.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(item.name, fontWeight = FontWeight.Medium)
                Text(
                    item.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (projects.isNotEmpty()) Text(
                    "已在 ${projects.joinToString("、")} 中启用",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            onBind?.let { bind ->
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "${item.name}的更多操作")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("用于其他项目") }, onClick = {
                            menuExpanded = false
                            bind()
                        })
                    }
                }
            }
        }
    }
}
