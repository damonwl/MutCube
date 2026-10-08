package com.dwl.mutcube.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.InstalledTemplate
import com.dwl.mutcube.storage.InstalledTemplateStore
import com.dwl.mutcube.storage.TemplatePackageInspection
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.template.core.TemplateCapabilityTier
import com.dwl.mutcube.template.core.TemplateHostCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TemplatePackageManagerPage(store: InstalledTemplateStore, repository: TemplateRepository, projects: List<Space>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by store.templates.collectAsState()
    val bindings by repository.userBindings.collectAsState(initial = emptyList())
    val records by repository.userRecords.collectAsState(initial = emptyList())
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pending by remember { mutableStateOf<TemplatePackageInspection?>(null) }
    var removing by remember { mutableStateOf<InstalledTemplate?>(null) }
    var deleteData by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; message = null
            try {
                val input = requireNotNull(context.contentResolver.openInputStream(uri))
                pending = input.use { store.inspect(it) }
                pendingUri = uri
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { messageIsError = true; message = "模板包校验失败：${failure.message ?: "文件不可用"}" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("返回模板") }
        Text("模板管理", style = MaterialTheme.typography.headlineMedium)
        Text("第三方模板只在绑定项目并授权后访问数据。安装本身不会授权；来源未验证的模板请谨慎使用。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
            Text("从本地导入模板包")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (installed.isEmpty()) Text("尚未安装第三方模板", color = MaterialTheme.colorScheme.onSurfaceVariant)
        installed.forEach { item ->
            val pkg = item.definition
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pkg.manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text("${pkg.developer.name} · v${pkg.manifest.version} · 未验证来源")
                    pkg.developer.license?.let { Text("声明许可：$it", style = MaterialTheme.typography.bodySmall) }
                    Text("模板 ID：${pkg.manifest.id}", style = MaterialTheme.typography.bodySmall)
                    Text("来源：本地导入 · 大小：${item.directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024} KiB",
                        style = MaterialTheme.typography.bodySmall)
                    val activeProjects = bindings.filter { it.templateId == pkg.manifest.id && it.enabled }
                        .map { binding -> projects.firstOrNull { it.id.value == binding.projectId }?.name ?: binding.projectId }
                    Text("已绑定项目：${activeProjects.ifEmpty { listOf("无") }.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                    Text("权限：${pkg.permissions.sorted().joinToString("、", transform = ::permissionLabel)}", style = MaterialTheme.typography.bodySmall)
                    Text("集合：${pkg.manifest.collections.joinToString("、") { it.id }}", style = MaterialTheme.typography.bodySmall)
                    if (pkg.networkDomains.isNotEmpty()) Text("允许请求：${pkg.networkDomains.joinToString("、")}")
                    OutlinedButton(onClick = { removing = item; deleteData = false; confirmation = "" }, enabled = !busy) { Text("卸载") }
                }
            }
        }
    }
    pending?.let { inspection ->
        val pkg = inspection.definition
        AlertDialog(
            onDismissRequest = { if (!busy) { pending = null; pendingUri = null } },
            title = { Text("安装 ${pkg.manifest.name}？") },
            text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("开发者：${pkg.developer.name}（本地包，来源未验证）")
                pkg.developer.license?.let { Text("声明许可：$it（未经宿主核实）") }
                Text("版本：${pkg.manifest.version} · 需要 MutCube ${pkg.minHostVersion}+")
                Text("资源总量：${inspection.totalBytes / 1024} KiB · 本地包 Hash 已核对")
                val retained = records.count { it.templateId == pkg.manifest.id }
                if (retained > 0) Text("此 ID 已有 $retained 条保留的用户记录。安装并授权后，新包可能读取这些记录；请核对开发者与来源。",
                    color = MaterialTheme.colorScheme.error)
                installed.firstOrNull { it.definition.manifest.id == pkg.manifest.id }?.let { current ->
                    Text("当前已安装 v${current.definition.manifest.version}；升级后所有项目需重新授权。")
                }
                Text("数据集合：${pkg.manifest.collections.joinToString("、") { it.id }}")
                Text("需要的宿主能力：")
                pkg.permissions.sorted().forEach { permission ->
                    val tier = TemplateHostCapabilities.supported.getValue(permission)
                    Text("${if (tier == TemplateCapabilityTier.SENSITIVE) "敏感" else "基础"} · ${permissionLabel(permission)}（$permission）")
                }
                if (pkg.networkDomains.isNotEmpty()) Text("网络域名：${pkg.networkDomains.joinToString("、")}")
                Text("安装后仍需选择项目并授权。模板不能直接读取 API Key、其他项目或任意本地文件。")
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
                val uri = pendingUri ?: return@launch
                busy = true
                try {
                    val input = requireNotNull(context.contentResolver.openInputStream(uri))
                    val result = input.use { store.install(it, inspection.definition) }
                    pending = null; pendingUri = null
                    messageIsError = false
                    message = "已安装 ${result.definition.manifest.name}。绑定项目后即可使用。"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { messageIsError = true; message = "安装失败：${failure.message ?: "请重试"}" }
                finally { busy = false }
            } }) { Text("确认安装") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null; pendingUri = null }) { Text("取消") } },
        )
    }
    removing?.let { item -> AlertDialog(
        onDismissRequest = { if (!busy) removing = null },
        title = { Text("卸载 ${item.definition.manifest.name}？") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("卸载会解除该模板与项目的绑定。默认保留所有项目中的模板数据，以便重新安装后恢复使用。")
            Row { Checkbox(checked = deleteData, onCheckedChange = { deleteData = it; confirmation = "" }); Text("同时永久删除此模板在所有项目的数据") }
            if (deleteData) {
                Text("此操作不可撤销。输入完整模板 ID 确认：${item.definition.manifest.id}")
                OutlinedTextField(confirmation, { confirmation = it }, singleLine = true, label = { Text("模板 ID") })
            }
        } },
        confirmButton = { TextButton(enabled = !busy && (!deleteData || confirmation == item.definition.manifest.id), onClick = { scope.launch {
            busy = true
            try {
                store.uninstall(item.definition.manifest.id, deleteData, confirmation.takeIf { deleteData })
                removing = null
                messageIsError = false
                message = if (deleteData) "模板与其数据已删除" else "模板已卸载，数据已保留"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { messageIsError = true; message = "卸载失败：${failure.message ?: "请重试"}" }
            finally { busy = false }
        } }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("确认卸载") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { removing = null }) { Text("取消") } },
    ) }
}

internal fun permissionLabel(value: String) = when (value) {
    "host.permissions" -> "查看授权状态"
    "data.records" -> "模板记录"
    "ai.generate" -> "AI 生成"
    "project.info" -> "当前项目信息"
    "ui.theme" -> "界面主题"
    "ui.notice" -> "应用内提示"
    "ui.confirm" -> "原生确认框"
    "navigation.openChat" -> "打开项目聊天"
    "file.pick" -> "选择文件"
    "media.image.pick" -> "选择图片"
    "clipboard.read" -> "读取剪贴板"
    "network.fetch" -> "访问授权域名"
    "speech.speak" -> "朗读模板文字（可能发送至语音服务）"
    "speech.recognize" -> "录音并转写（录音可能发送至语音服务）"
    else -> value
}
