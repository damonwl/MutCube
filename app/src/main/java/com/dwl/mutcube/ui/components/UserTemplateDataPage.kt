package com.dwl.mutcube.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.core.database.TemplatePermissionEntity
import com.dwl.mutcube.core.database.TemplateRecordEntity
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.TemplateAccessContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun UserTemplateDataPage(repository: TemplateRepository, projects: List<Space>, onBack: () -> Unit) {
    val records by repository.userRecords.collectAsState(initial = emptyList())
    val audits by repository.audits.collectAsState(initial = emptyList())
    val collections by repository.userCollections.collectAsState(initial = emptyList())
    val bindings by repository.userBindings.collectAsState(initial = emptyList())
    val permissions by repository.userPermissions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    var notice by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<TemplateRecordEntity?>(null) }
    var exportPayload by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri)).bufferedWriter().use { it.write(exportPayload) }
                }
                notice = "数据已导出"
            } catch (failure: Exception) { notice = "导出失败，请检查文件位置" }
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        SettingsPageHeader("模板数据与授权", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            item {
                Text("数据归你所有，解绑或卸载模板后仍可查看和导出。只读授权仅作用于已启用服务中声明的查询操作，不授予修改权限。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    exportPayload = JSONObject().put("schemaVersion", 2).put("contracts", JSONArray().apply {
                        collections.forEach { contract ->
                            put(JSONObject().put("templateId", contract.templateId).put("collection", contract.collection)
                                .put("schemaVersion", contract.schemaVersion).put("mutability", contract.mutability)
                                .put("schema", JSONObject(contract.schemaJson)))
                        }
                    }).put("records", JSONArray().apply {
                        records.forEach { row ->
                            put(JSONObject().put("projectId", row.projectId).put("templateId", row.templateId)
                                .put("collection", row.collection).put("key", row.recordKey).put("revision", row.revision)
                                .put("schemaVersion", row.schemaVersion).put("source", row.source)
                                .put("createdAt", row.createdAt).put("updatedAt", row.updatedAt).put("data", JSONObject(row.json)))
                        }
                    }).toString(2)
                    export.launch("mutcube-template-data.json")
                }, enabled = records.isNotEmpty()) { Text("导出全部模板数据") }
                if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (records.isEmpty()) Text("暂无模板数据", modifier = Modifier.padding(vertical = 24.dp))
            }
            records.groupBy { it.projectId to it.templateId }.forEach { (identity, rows) ->
                item(key = "${identity.first}/${identity.second}") {
                    var visibleCount by remember(identity) { mutableIntStateOf(20) }
                    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            val project = projects.firstOrNull { it.id.value == identity.first }
                            Text("${project?.name ?: "已删除项目的数据"} · ${identity.second}", style = MaterialTheme.typography.titleSmall)
                            Text("${rows.size} 条记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val access = TemplateAccessContext(identity.first, identity.second)
                            val binding = bindings.firstOrNull { it.projectId == identity.first && it.templateId == identity.second }
                            val readable = permissions.filter { it.projectId == identity.first && it.templateId == identity.second && it.operation == "LIST" }.map { it.collection }
                            Text(if (readable.isEmpty()) "项目聊天读取：未授权" else "已授权读取：${readable.joinToString()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (binding?.enabled == true) TextButton(onClick = {
                                scope.launch {
                                    try {
                                        repository.bind(binding.copy(enabled = false))
                                        notice = "模板已停用，授权已撤销，数据仍保留"
                                    } catch (failure: Exception) { notice = "停用失败，项目可能已被删除" }
                                }
                            }) { Text("停用模板并保留数据") }
                            Row {
                                TextButton(enabled = project != null, onClick = {
                                    scope.launch {
                                        try {
                                            rows.map { it.collection }.distinct().forEach { collection ->
                                                listOf("READ", "LIST").forEach { operation ->
                                                    repository.grant(TemplatePermissionEntity(identity.first, identity.second, collection, operation, false))
                                                }
                                            }
                                            notice = "已授予只读权限；仅已启用服务中声明的查询操作可以调用"
                                        } catch (failure: Exception) { notice = "授权失败，项目可能已被删除" }
                                    }
                                }) { Text("授予只读权限") }
                                TextButton(onClick = {
                                    scope.launch {
                                        repository.revoke(access)
                                        notice = "已撤销该模板的全部数据授权"
                                    }
                                }) { Text("撤销授权") }
                            }
                            rows.take(visibleCount).forEach { row ->
                                TextButton(onClick = { selected = row }) { Text("${row.collection} · ${row.recordKey.take(16)}") }
                            }
                            if (rows.size > visibleCount) {
                                TextButton(onClick = { visibleCount += 20 }) {
                                    Text("显示更多记录（剩余 ${rows.size - visibleCount} 条）")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("近期访问审计", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
                Text("不记录数据正文、密钥或请求头。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(audits.size) { index ->
                val audit = audits[index]
                Text("${audit.templateId}/${audit.collection} · ${audit.operation} · ${audit.outcome}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
    selected?.let { row -> AlertDialog(
        onDismissRequest = { selected = null },
        title = { Text("${row.collection} · 版本 ${row.revision}") },
        text = { LazyColumn { item { Text(JSONObject(row.json).toString(2)) } } },
        confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
    ) }
}
