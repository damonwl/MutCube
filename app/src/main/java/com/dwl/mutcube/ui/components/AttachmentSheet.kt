package com.dwl.mutcube.ui.components

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composeunstyled.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onRecentImage: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun canReadImages(): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED)
    var hasPermission by remember { mutableStateOf(canReadImages()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = canReadImages() }
    val recentImages by produceState<List<Uri>>(emptyList(), hasPermission) {
        value = if (hasPermission) withContext(Dispatchers.IO) { queryRecentImages(context.contentResolver) } else emptyList()
    }
    val state = rememberModalBottomSheetState(initialDetent = SheetDetent.FullyExpanded)
    UnstyledModalBottomSheet(state = state, onDismiss = onDismiss, overlay = { Scrim(scrimColor = Color.Black.copy(alpha = 0.35f)) }) {
        Sheet(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 18.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentAction("拍照", Icons.Rounded.CameraAlt, onCamera, Modifier.weight(1f))
                    AttachmentAction("相册", Icons.Rounded.PhotoLibrary, onGallery, Modifier.weight(1f))
                    AttachmentAction("本地文件", Icons.Rounded.Folder, onFiles, Modifier.weight(1f))
                }
                Text("最近图片", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
                when {
                    !hasPermission -> Surface(onClick = { permissionLauncher.launch(permission) }, color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.PhotoLibrary, null)
                            Text("允许访问照片后显示最近图片", modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    recentImages.isEmpty() -> Text("相册中暂无图片", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally))
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentImages.take(3).forEach { uri -> RecentImage(uri, { onRecentImage(uri) }, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentImage(uri: Uri, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) resolver.loadThumbnail(uri, Size(360, 360), null)
            else resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.Options().run { inSampleSize = 4; BitmapFactory.decodeStream(stream, null, this) }
            }
        }
    }
    Box(modifier.aspectRatio(1.1f).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

private fun queryRecentImages(resolver: android.content.ContentResolver): List<Uri> {
    val result = mutableListOf<Uri>()
    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        while (cursor.moveToNext() && result.size < 6) result += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn))
    }
    return result
}

@Composable
private fun AttachmentAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) { Icon(icon, null, Modifier.padding(11.dp).size(20.dp)) }
            Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}
