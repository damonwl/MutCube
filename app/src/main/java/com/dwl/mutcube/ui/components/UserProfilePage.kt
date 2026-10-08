package com.dwl.mutcube.ui.components

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.storage.DisplaySettings
import com.dwl.mutcube.storage.UserAvatarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable

@Composable
fun UserAvatar(fileName: String, size: Dp = 44.dp) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, fileName) {
        value = withContext(Dispatchers.IO) {
            runCatching { UserAvatarStore(context).file(fileName)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }.getOrNull()
        }
    }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            if (image != null) Image(image!!, "用户头像", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else MutCubeMark(size = size * 0.68f)
        }
    }
}

@Composable
fun UserProfilePage(settings: DisplaySettings, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { UserAvatarStore(context) }
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf(settings.userNickname) }
    var avatar by remember { mutableStateOf(settings.userAvatarFile) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val imported = remember { mutableSetOf<String>() }
    var savedAvatar by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { imported.filter { it != savedAvatar }.forEach(store::delete) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            var created: String? = null
            try {
                val name = withContext(Dispatchers.IO) { store.import(uri).also { created = it } }
                imported += name
                avatar = name
            } catch (cancelled: CancellationException) {
                withContext(Dispatchers.IO + NonCancellable) { created?.let(store::delete) }
                throw cancelled
            } catch (failure: Exception) { error = "头像选择失败，请换一张图片重试" }
            finally { busy = false }
        }
    }
    fun chooseAvatar() { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("个人信息", onBack)
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 24.dp).clickable(enabled = !busy) { chooseAvatar() }) { UserAvatar(avatar, 88.dp) }
            TextButton(onClick = ::chooseAvatar, enabled = !busy) { Text(if (busy) "正在处理图片…" else "选择头像") }
            if (avatar.isNotBlank()) TextButton(onClick = { avatar = "" }, enabled = !busy) { Text("恢复默认 Logo") }
            OutlinedTextField(nickname, { nickname = it }, Modifier.fillMaxWidth().padding(top = 20.dp),
                label = { Text("昵称") }, placeholder = { Text("用户") }, singleLine = true)
            Text("昵称留空显示“用户”，未设置头像时显示 App Logo。仅保存在本机。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
        Button(onClick = {
            onSave(nickname.trim(), avatar)
            savedAvatar = avatar
            if (settings.userAvatarFile != avatar) store.delete(settings.userAvatarFile)
            onBack()
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("保存") }
    }
}
