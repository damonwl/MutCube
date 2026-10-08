package com.dwl.mutcube.template.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.dwl.mutcube.template.core.TemplateActionEngine
import com.dwl.mutcube.template.core.TemplateInstance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.dwl.mutcube.core.database.TemplateRepository
import com.dwl.mutcube.core.model.Space
import com.dwl.mutcube.core.model.TemplateAccessContext
import com.dwl.mutcube.template.core.TemplateManifest
import com.dwl.mutcube.template.core.ActionChannel
import com.dwl.mutcube.template.core.ActionMode
import com.dwl.mutcube.template.runtime.TemplateRuntime
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import android.webkit.MimeTypeMap
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private data class VersionConfirmation(
    val collection: String,
    val deletion: Boolean,
    val recordKey: String,
    val expectedRevision: Long,
    val json: String,
    val decision: CompletableDeferred<Boolean>,
)
private data class HostConfirmation(val title: String, val message: String, val decision: CompletableDeferred<Boolean>)

@Composable
fun TemplateRuntimePage(
    manifest: TemplateManifest,
    project: Space,
    repository: TemplateRepository,
    runtime: TemplateRuntime,
    actions: TemplateActionEngine,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    trustedGuiVersionSelection: Boolean = false,
    initialTarget: String? = null,
    installedResource: ((String) -> File?)? = null,
    hostPermissions: Set<String> = emptySet(),
    networkDomains: Set<String> = emptySet(),
    speechHost: TemplateSpeechHost? = null,
) {
    val scope = rememberCoroutineScope()
    val instance = remember(project.id, manifest.id) { TemplateInstance(project.id.value, manifest.id) }
    val context = remember(project.id, manifest.id) { TemplateAccessContext(project.id.value, manifest.id) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showRecords by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var versionConfirmation by remember { mutableStateOf<VersionConfirmation?>(null) }
    var hostConfirmation by remember { mutableStateOf<HostConfirmation?>(null) }
    var pendingFilePick by remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    var pendingMicrophonePermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingMicrophonePermission?.complete(granted)
        pendingMicrophonePermission = null
    }
    var speechTask by remember { mutableStateOf<Deferred<Unit>?>(null) }
    var speakingState by remember { mutableStateOf<String?>(null) }
    var recognitionTask by remember { mutableStateOf<Deferred<String>?>(null) }
    var recognitionState by remember { mutableStateOf<String?>(null) }
    var recognitionLevel by remember { mutableFloatStateOf(0f) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFilePick?.complete(uri)
        pendingFilePick = null
    }
    var leaving by remember { mutableStateOf(false) }
    var leaveRequestId by remember { mutableStateOf<String?>(null) }
    var showForceLeave by remember { mutableStateOf(false) }
    var selectedRun by remember { mutableStateOf<com.dwl.mutcube.core.database.TemplateRunEntity?>(null) }
    val runs by repository.runs(context).collectAsState(initial = emptyList())
    val progressByInstance by runtime.progressByInstance.collectAsState()
    val colors = MaterialTheme.colorScheme
    val theme = JSONObject().apply {
        fun color(name: String, value: androidx.compose.ui.graphics.Color) = put(name, "#%06X".format(value.toArgb() and 0xFFFFFF))
        color("bg", colors.background); color("fg", colors.onBackground); color("card", colors.surface)
        color("muted", colors.onSurfaceVariant); color("action", colors.primary); color("onaction", colors.onPrimary)
    }.toString()
    val latestTheme by rememberUpdatedState(theme)
    fun applyTheme(view: WebView) {
        view.evaluateJavascript("Object.entries($latestTheme).forEach(([k,v])=>document.documentElement.style.setProperty('--'+k,v))", null)
    }
    fun speechState(state: String, level: Float = 0f) {
        if (state == "preparing" || state == "playing") speakingState = state
        if (state == "finished" || state == "stopped") speakingState = null
        webView?.evaluateJavascript("window.dispatchEvent(new CustomEvent('mutcube:speech',{detail:{state:${JSONObject.quote(state)},level:$level}}))", null)
    }
    LaunchedEffect(theme, webView) { webView?.let(::applyTheme) }
    fun requestLeave() {
        if ("navigation.close" !in manifest.capabilities || webView == null) { onBack(); return }
        if (leaving) { showForceLeave = true; return }
        leaving = true
        val token = java.util.UUID.randomUUID().toString()
        leaveRequestId = token
        webView?.evaluateJavascript("""
            (async()=>{
              const accepted=typeof window.MutCubeBeforeLeave==='function' ? await window.MutCubeBeforeLeave() : true;
              MutCube.postMessage(JSON.stringify({protocolVersion:2,requestId:'${token}',capability:'navigation.close',input:{accepted}}));
            })().catch(()=>{});
        """.trimIndent(), null)
        scope.launch {
            delay(30_000)
            if (leaving && leaveRequestId == token) {
                leaving = false
                leaveRequestId = null
                showForceLeave = true
            }
        }
    }
    val guiActions = actions.withConfirmation { _, action, input ->
        // Host opt-in only for bundled, network-isolated GUI code. Chat never uses this engine.
        if (trustedGuiVersionSelection && action.mode == ActionMode.SELECT_VERSION) return@withConfirmation true
        check(versionConfirmation == null) { "Another confirmation is active" }
        val args = JSONObject(input)
        val key = args.getString("key")
        val record = requireNotNull(repository.read(context, action.collection, key))
        val pending = VersionConfirmation(action.collection, action.mode == ActionMode.DELETE, key,
            args.getLong("expectedRevision"), record.json, CompletableDeferred())
        versionConfirmation = pending
        try { pending.decision.await() } finally { if (versionConfirmation === pending) versionConfirmation = null }
    }
    BackHandler { requestLeave() }
    DisposableEffect(Unit) {
        onDispose {
            versionConfirmation?.decision?.complete(false)
            hostConfirmation?.decision?.complete(false)
            pendingFilePick?.complete(null)
            pendingMicrophonePermission?.complete(false)
            speechTask?.cancel()
            recognitionTask?.cancel()
            speechHost?.stopSpeaking()
            speechHost?.cancelRecognition()
            webView?.apply { stopLoading(); destroy() }; webView = null
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row {
            Row(Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = ::requestLeave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(manifest.name, style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = { showRecords = true }) { Text("运行记录") }
        }
        speakingState?.let { state -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(if (state == "preparing") "正在准备朗读…" else "正在朗读", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { speechHost?.stopSpeaking(); speechTask?.cancel(); speechState("stopped") }) { Text("停止") }
        } }
        progressByInstance["${project.id.value}/${manifest.id}"]?.let {
            Text(it.message, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { androidContext ->
                WebView(androidContext).apply {
                    webView = this
                    setBackgroundColor(colors.background.toArgb())
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.blockNetworkLoads = true
                    val host = if (installedResource == null) "appassets.androidplatform.net" else "template-" +
                        MessageDigest.getInstance("SHA-256").digest(manifest.id.toByteArray()).take(8)
                            .joinToString("") { "%02x".format(it.toInt() and 255) } + ".mutcube.invalid"
                    val origin = "https://$host"
                    val loader = if (installedResource == null) WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(androidContext)).build()
                        else WebViewAssetLoader.Builder().setDomain(host).addPathHandler("/bundle/", WebViewAssetLoader.PathHandler { path ->
                            val file = installedResource(path) ?: return@PathHandler blocked()
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                            WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf(
                                "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; form-action 'none'",
                                "X-Content-Type-Options" to "nosniff",
                            ), file.inputStream())
                        }).build()
                    val entryUrl = "$origin/${if (installedResource == null) "assets" else "bundle"}/${manifest.entry}"
                    val builtinResourcePrefix = entryUrl.substringBeforeLast('/') + "/"
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                            if (request.url.toString() == entryUrl || installedResource == null &&
                                request.url.toString().startsWith(builtinResourcePrefix) || installedResource != null &&
                                request.url.toString().startsWith("$origin/bundle/")) loader.shouldInterceptRequest(request.url)
                                ?: blocked() else blocked()
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        override fun onPageFinished(view: WebView, url: String) {
                            applyTheme(view)
                            initialTarget?.let { target ->
                                // JSON.parse with quoted input prevents script injection from record identifiers.
                                view.evaluateJavascript("window.MutCubeLaunchTarget=JSON.parse(${JSONObject.quote(target)});window.dispatchEvent(new Event('mutcube:open-record'));", null)
                            }
                        }
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        WebViewCompat.addWebMessageListener(this, "MutCube", setOf(origin)) { _, message, sourceOrigin, mainFrame, reply ->
                            if (mainFrame && sourceOrigin.toString().trimEnd('/') == origin) {
                                scope.launch {
                                    var requestId = ""
                                    var capabilityName = ""
                                    val response = JSONObject()
                                    try {
                                        runtime.initialize()
                                        val raw = requireNotNull(message.data)
                                        require(raw.length <= 110_000)
                                        val body = JSONObject(raw)
                                        require(body.keys().asSequence().all { it in setOf("protocolVersion", "requestId", "capability", "input") })
                                        require(body.get("protocolVersion") is Int && body.getInt("protocolVersion") == 2)
                                        requestId = body.getString("requestId")
                                        require(requestId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
                                        val capability = body.getString("capability")
                                        capabilityName = capability
                                        val lifecycleOnly = capability in setOf("navigation.close", "speech.stop", "speech.recognize.stop")
                                        suspend fun requireLiveBinding() {
                                            try { repository.requireBinding(context, manifest.version) }
                                            catch (_: IllegalArgumentException) { throw SecurityException("模板绑定或授权已失效") }
                                        }
                                        if (!lifecycleOnly) requireLiveBinding()
                                        val authorized = capability in manifest.capabilities || capability in hostPermissions ||
                                            capability == "speech.stop" && "speech.speak" in hostPermissions ||
                                            capability == "speech.recognize.stop" && "speech.recognize" in hostPermissions
                                        if (!authorized) throw SecurityException("模板未获此宿主能力授权")
                                        val result: Any = when (body.getString("capability")) {
                                            "action.run" -> {
                                                val input = body.getJSONObject("input")
                                                require(input.keys().asSequence().toSet() == setOf("actionId", "data"))
                                                val result = guiActions.execute(instance, manifest, input.getString("actionId"),
                                                    ActionChannel.GUI, requestId, input.getJSONObject("data").toString())
                                                JSONObject().put("key", result.key).put("data", JSONObject(result.data))
                                                    .put("pendingConfirmation", result.pendingConfirmation)
                                            }
                                            "navigation.openChat" -> { onOpenChat(); "ok" }
                                            "project.info" -> JSONObject().put("id", project.id.value).put("name", project.name)
                                            "host.permissions" -> JSONObject().put("granted", JSONArray(hostPermissions.sorted()))
                                                .put("available", JSONArray(hostPermissions.filter { permission ->
                                                    !permission.startsWith("speech.") || speechHost?.isConfigured(permission) == true
                                                }.sorted()))
                                                .put("networkDomains", JSONArray(networkDomains.sorted()))
                                            "ui.theme" -> JSONObject(latestTheme)
                                            "ui.notice" -> {
                                                val message = body.getJSONObject("input").getString("message")
                                                require(message.length in 1..500)
                                                notice = message
                                                "ok"
                                            }
                                            "ui.confirm" -> {
                                                require(hostConfirmation == null)
                                                val input = body.getJSONObject("input")
                                                val title = input.getString("title").take(80)
                                                val message = input.getString("message").take(2_000)
                                                val confirmation = HostConfirmation(title, message, CompletableDeferred())
                                                hostConfirmation = confirmation
                                                try { confirmation.decision.await() } finally { if (hostConfirmation === confirmation) hostConfirmation = null }
                                            }
                                            "clipboard.read" -> {
                                                require(hostConfirmation == null)
                                                val confirmation = HostConfirmation("读取剪贴板？", "${manifest.name} 请求读取剪贴板文字。", CompletableDeferred())
                                                hostConfirmation = confirmation
                                                val approved = try { confirmation.decision.await() }
                                                    finally { if (hostConfirmation === confirmation) hostConfirmation = null }
                                                check(approved) { "用户取消了剪贴板读取" }
                                                val clipboard = androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(androidContext)?.toString().orEmpty()
                                                require(text.length <= 10_000) { "剪贴板文字过长" }
                                                text
                                            }
                                            "network.fetch" -> {
                                                val input = body.getJSONObject("input")
                                                require(input.keys().asSequence().toSet() == setOf("url"))
                                                templateFetch(input.getString("url"), networkDomains)
                                            }
                                            "file.pick", "media.image.pick" -> {
                                                require(pendingFilePick == null) { "文件选择器已打开" }
                                                val decision = CompletableDeferred<Uri?>()
                                                pendingFilePick = decision
                                                try {
                                                    filePicker.launch(if (body.getString("capability") == "media.image.pick") "image/*" else "*/*")
                                                    val selected = requireNotNull(decision.await()) { "用户取消了文件选择" }
                                                    readTemplateSelectedFile(androidContext.contentResolver, selected,
                                                        body.getString("capability") == "media.image.pick")
                                                } finally { if (pendingFilePick === decision) pendingFilePick = null }
                                            }
                                            "speech.speak" -> supervisorScope {
                                                val input = body.getJSONObject("input")
                                                require(input.keys().asSequence().toSet() == setOf("text"))
                                                val utterance = input.getString("text")
                                                require(utterance.isNotBlank() && utterance.length <= 10_000)
                                                val host = checkNotNull(speechHost) { "语音服务不可用" }
                                                check(host.isConfigured("speech.speak")) { "请先在语音设置中配置可用的 TTS Provider 和模型" }
                                                check(speechTask == null) { "已有朗读正在进行" }
                                                val task = async { host.speak(utterance) { speechState(it) } }
                                                speechTask = task
                                                try { task.await(); JSONObject().put("state", "finished") }
                                                catch (_: CancellationException) { JSONObject().put("state", "stopped") }
                                                finally { if (speechTask === task) speechTask = null; speakingState = null }
                                            }
                                            "speech.stop" -> {
                                                require(body.getJSONObject("input").length() == 0)
                                                speechHost?.stopSpeaking()
                                                speechTask?.cancel()
                                                speechState("stopped")
                                                "ok"
                                            }
                                            "speech.recognize" -> supervisorScope {
                                                val input = body.getJSONObject("input")
                                                require(input.keys().asSequence().all { it == "language" })
                                                val language = if (input.has("language")) input.getString("language") else null
                                                require(language == null || language in setOf("auto", "en", "zh")) { "识别语言只支持 auto、en、zh" }
                                                val host = checkNotNull(speechHost) { "语音服务不可用" }
                                                check(host.isConfigured("speech.recognize")) { "请先在语音设置中配置可用的 ASR Provider 和模型" }
                                                check(recognitionTask == null && pendingMicrophonePermission == null && recognitionState == null && hostConfirmation == null) { "已有语音输入正在进行" }
                                                val requestedLanguage = when (language) { "en" -> "英语"; "zh" -> "中文"; "auto" -> "自动识别"; else -> "沿用全局设置" }
                                                val confirmation = HostConfirmation("开始语音输入？", "${manifest.name} 请求录音并转写为文字。本次语言：$requestedLanguage。录音由宿主管理，模板只会收到转写文字。", CompletableDeferred())
                                                hostConfirmation = confirmation
                                                val approved = try { confirmation.decision.await() }
                                                    finally { if (hostConfirmation === confirmation) hostConfirmation = null }
                                                if (!approved) JSONObject().put("cancelled", true) else {
                                                val allowed = if (androidContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) true
                                                    else {
                                                        val decision = CompletableDeferred<Boolean>()
                                                        pendingMicrophonePermission = decision
                                                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                                        try { decision.await() } finally { if (pendingMicrophonePermission === decision) pendingMicrophonePermission = null }
                                                    }
                                                check(allowed) { "未授予麦克风权限" }
                                                recognitionState = "recording"
                                                val task = async { host.recognize(language) { state, level ->
                                                    recognitionState = state
                                                    recognitionLevel = level
                                                    speechState(state, level)
                                                } }
                                                recognitionTask = task
                                                try { JSONObject().put("text", task.await()) }
                                                catch (_: CancellationException) { JSONObject().put("cancelled", true) }
                                                finally {
                                                    if (recognitionTask === task) recognitionTask = null
                                                    recognitionState = null
                                                    recognitionLevel = 0f
                                                }
                                                }
                                            }
                                            "speech.recognize.stop" -> {
                                                require(body.getJSONObject("input").length() == 0)
                                                speechHost?.stopRecognition()
                                                "ok"
                                            }
                                            "navigation.close" -> {
                                                require(leaving && requestId == leaveRequestId)
                                                val input = body.getJSONObject("input")
                                                require(input.keys().asSequence().toSet() == setOf("accepted") && input.get("accepted") is Boolean)
                                                leaving = false
                                                leaveRequestId = null
                                                if (input.getBoolean("accepted")) onBack()
                                                "ok"
                                            }
                                            else -> error("Unknown template capability")
                                        }
                                        if (!lifecycleOnly && capability != "navigation.openChat") requireLiveBinding()
                                        response.put("result", result)
                                    } catch (failure: Exception) {
                                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                                        val stale = failure is com.dwl.mutcube.core.database.TemplateRevisionConflictException
                                        val speechRequest = capabilityName.startsWith("speech.")
                                        val microphoneDenied = speechRequest && failure.message == "未授予麦克风权限"
                                        val speechBusy = speechRequest && failure.message?.startsWith("已有") == true
                                        val speechNotConfigured = speechRequest && failure.message?.startsWith("请先在语音设置中配置") == true
                                        response.put("errorCode", when {
                                            stale -> "VERSION_CONFLICT"
                                            microphoneDenied -> "PERMISSION_DENIED"
                                            failure is SecurityException -> "PERMISSION_DENIED"
                                            speechBusy -> "BUSY"
                                            speechNotConfigured -> "SERVICE_NOT_CONFIGURED"
                                            failure.message?.contains("Data access denied") == true -> "PERMISSION_DENIED"
                                            failure is IllegalArgumentException -> "INVALID_REQUEST"
                                            speechRequest -> "SPEECH_ERROR"
                                            else -> "HOST_ERROR"
                                        })
                                        response.put("error", when {
                                            stale -> "当前版本已变化，请刷新后重新生成候选。"
                                            microphoneDenied -> "未授予麦克风权限"
                                            failure is SecurityException -> "模板未获此宿主能力授权"
                                            speechBusy -> "已有语音任务正在进行"
                                            speechNotConfigured -> failure.message
                                            speechRequest -> failure.message?.let { message ->
                                                Regex("HTTP [0-9]{3}").find(message)?.value?.let { "语音服务返回 $it" }
                                                    ?: if (message.startsWith("系统语音") || message.startsWith("设备没有") || message.startsWith("没有录制")) message.take(150)
                                                    else "语音服务执行失败，请检查语音设置"
                                            } ?: "语音服务执行失败，请检查语音设置"
                                            else -> "操作失败：请检查授权、数据版本、模型配置或运行记录。已保存的数据不会回滚。"
                                        })
                                    }
                                    reply.postMessage(response.put("requestId", requestId).toString())
                                }
                            }
                        }
                        loadUrl(entryUrl)
                    } else {
                        loadData("<p>系统 WebView 版本不支持安全模板桥接，请更新系统 WebView。</p>", "text/html", "UTF-8")
                    }
                }
            },
            update = {
                it.setBackgroundColor(colors.background.toArgb())
                applyTheme(it)
            },
        )
        notice?.let { text ->
            Snackbar(action = { TextButton(onClick = { notice = null }) { Text("关闭") } }) { Text(text) }
        }
    }
    versionConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { pending.decision.complete(false) },
            title = { Text(if (pending.deletion) "确认删除此记录" else "确认启用此版本") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        Text("${manifest.name} · ${project.name}")
                        Text(if (pending.deletion) "删除后不可撤销，请确认内容。" else "确认后此版本成为当前版本，历史记录不会被覆盖。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pending.json)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pending.decision.complete(true) }) { Text(if (pending.deletion) "确认删除" else "确认启用") } },
            dismissButton = { TextButton(onClick = { pending.decision.complete(false) }) { Text("取消") } },
        )
    }
    hostConfirmation?.let { pending -> AlertDialog(
        onDismissRequest = { pending.decision.complete(false) },
        title = { Text(pending.title) }, text = { Text(pending.message) },
        confirmButton = { TextButton(onClick = { pending.decision.complete(true) }) { Text("确认") } },
        dismissButton = { TextButton(onClick = { pending.decision.complete(false) }) { Text("取消") } },
    ) }
    recognitionState?.let { state -> AlertDialog(
        onDismissRequest = { speechHost?.cancelRecognition(); recognitionTask?.cancel() },
        title = { Text(if (state == "transcribing") "正在转写" else "语音输入") },
        text = { Column {
            Text(if (state == "transcribing") "正在将录音转换成文字…" else "正在录音；说完后可点击完成")
            if (state == "recording") LinearProgressIndicator(progress = { recognitionLevel.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = state == "recording", onClick = { speechHost?.stopRecognition() }) { Text("完成") } },
        dismissButton = { TextButton(onClick = { speechHost?.cancelRecognition(); recognitionTask?.cancel() }) { Text("取消") } },
    ) }
    if (showForceLeave) AlertDialog(
        onDismissRequest = { showForceLeave = false },
        title = { Text("模板页面无响应") },
        text = { Text("页面尚未确认草稿是否保存。强制退出可能丢失未保存的内容。") },
        confirmButton = { TextButton(onClick = { showForceLeave = false; leaving = false; leaveRequestId = null; onBack() }) { Text("仍要退出") } },
        dismissButton = { TextButton(onClick = { showForceLeave = false }) { Text("继续等待") } },
    )
    if (showRecords) AlertDialog(
        onDismissRequest = { showRecords = false },
        title = { Text("运行记录") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(runs.size) { index ->
                    val run = runs[index]
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text("${run.status} · ${run.modelId}")
                        Text(run.error ?: run.outputJson ?: "正在处理", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { selectedRun = run }) { Text("查看详情与上下文") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showRecords = false }) { Text("关闭") } },
    )
    selectedRun?.let { run ->
        AlertDialog(
            onDismissRequest = { selectedRun = null },
            title = { Text("运行详情") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        Text("${run.actionId} · ${run.status}")
                        Text("${run.providerId} / ${run.modelId}")
                        Text("输入\n${run.inputJson}")
                        Text("结果\n${run.outputJson ?: run.error ?: "尚无结果"}")
                        Text("上下文快照\n${run.contextJson ?: "尚未保存上下文"}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedRun = null }) { Text("关闭") } },
        )
    }
}

private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))

private suspend fun readTemplateSelectedFile(resolver: ContentResolver, uri: Uri, imageOnly: Boolean): JSONObject = withContext(Dispatchers.IO) {
    val mime = resolver.getType(uri).orEmpty()
    if (imageOnly) require(mime.startsWith("image/")) { "请选择图片" }
    var name = "所选文件"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty().take(160)
    }
    val bytes = requireNotNull(resolver.openInputStream(uri)).use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 2 * 1024 * 1024) { "所选文件超过 2 MiB" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    JSONObject().put("name", name).put("mimeType", mime).put("size", bytes.size)
        .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP))
}
