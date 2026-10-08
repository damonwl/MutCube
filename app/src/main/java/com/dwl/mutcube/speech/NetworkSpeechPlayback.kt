package com.dwl.mutcube.speech

import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One prefetch slot bounds work while the current segment plays. Cancellation releases playback. */
internal suspend fun playSpeechSegments(
    segments: List<String>,
    synthesize: suspend (String) -> File,
    onPlaying: suspend () -> Unit,
    playback: suspend (File) -> Unit = ::playFile,
) = coroutineScope {
    val queue = Channel<File>(1, onUndeliveredElement = { it.delete() })
    val producer = launch(Dispatchers.IO) {
        try { segments.forEach { queue.send(synthesize(it)) } }
        finally { queue.close() }
    }
    try {
        for (file in queue) {
            try {
                onPlaying()
                playback(file)
            } finally {
                file.delete()
            }
        }
        producer.join()
    } finally { producer.cancel(); queue.cancel() }
}

private suspend fun playFile(file: File) = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val player = MediaPlayer()
        continuation.invokeOnCancellation { player.release() }
        try {
            player.setDataSource(file.path)
            player.setOnPreparedListener { if (continuation.isActive) it.start() }
            player.setOnCompletionListener {
                if (continuation.isActive) { it.release(); continuation.resume(Unit) }
            }
            player.setOnErrorListener { _, _, _ ->
                if (continuation.isActive) { player.release(); continuation.resumeWithException(IllegalStateException("音频播放失败")) }
                true
            }
            player.prepareAsync()
        } catch (error: Exception) {
            player.release()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
