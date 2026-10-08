package com.dwl.mutcube.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID

/** Own a small image copy rather than keeping temporary picker URI permissions. */
class UserAvatarStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "user_profile")

    fun file(name: String): File? {
        if (!isUserAvatarFileName(name)) return null
        val candidate = File(directory, name)
        return candidate.takeIf { it.isFile && it.canonicalFile.parentFile == directory.canonicalFile }
    }

    fun delete(name: String) { file(name)?.delete() }

    fun import(uri: Uri): String {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val scale = minOf(1f, 512f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("无法读取图片")
        }
        require(directory.isDirectory || directory.mkdirs()) { "无法保存头像" }
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            target.outputStream().use { require(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "头像保存失败" } }
            return target.name
        } catch (error: Exception) { target.delete(); throw error }
        finally { bitmap.recycle() }
    }
}

internal fun isUserAvatarFileName(name: String): Boolean =
    name.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.jpg"))
