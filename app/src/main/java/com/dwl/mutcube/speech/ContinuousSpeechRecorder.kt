package com.dwl.mutcube.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/** Owns the microphone for the entire utterance; recognition never interrupts capture. */
class ContinuousSpeechRecorder private constructor(
    private val recorder: AudioRecord,
    private val file: File,
) {
    private val recording = AtomicBoolean(true)
    @Volatile var decibels: Float = -90f
        private set
    @Volatile private var failure: Throwable? = null
    private val worker = Thread({
        try {
            RandomAccessFile(file, "rw").use { output ->
                output.write(wavHeader(0))
                val samples = ShortArray(1_024)
                var dataBytes = 0
                while (recording.get()) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count < 0) {
                        if (!recording.get()) break
                        error("麦克风读取失败（代码 $count）")
                    }
                    if (count == 0) continue
                    val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
                    var energy = 0.0
                    repeat(count) { index ->
                        bytes.putShort(samples[index])
                        val normalized = samples[index] / 32768.0
                        energy += normalized * normalized
                    }
                    decibels = (20 * log10(sqrt(energy / count).coerceAtLeast(0.00003))).toFloat()
                    output.write(bytes.array())
                    dataBytes += count * 2
                }
                output.seek(0)
                output.write(wavHeader(dataBytes))
            }
        } catch (error: Throwable) {
            failure = error
            recording.set(false)
        }
    }, "MutCubeVoiceCapture").apply { start() }

    val amplitude: Float get() = ((decibels + 65f) / 55f).coerceIn(0f, 1f)
    fun checkHealthy() { failure?.let { throw IllegalStateException("录音失败：${it.message}", it) } }

    fun stop(): File {
        recording.set(false)
        runCatching { recorder.stop() }
        worker.join(1_000)
        recorder.release()
        try {
            check(!worker.isAlive) { "录音文件尚未保存完成" }
            checkHealthy()
            require(file.length() > 44) { "没有录制到音频" }
        } catch (error: Exception) {
            if (!worker.isAlive) file.delete()
            throw error
        }
        return file
    }

    fun discard() {
        runCatching { stop() }
        if (!worker.isAlive) file.delete()
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun start(context: Context): ContinuousSpeechRecorder {
            val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0) { "设备不支持当前录音格式" }
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8_192))
            val file = File.createTempFile("mutcube-recording-", ".wav", context.cacheDir)
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麦克风初始化失败" }
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "麦克风未开始录音" }
                return ContinuousSpeechRecorder(recorder, file)
            } catch (error: Exception) {
                recorder.release()
                file.delete()
                throw error
            }
        }
    }
}

internal fun wavHeader(dataBytes: Int, sampleRate: Int = 16_000): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
    put("RIFF".toByteArray(Charsets.US_ASCII)); putInt(36 + dataBytes)
    put("WAVEfmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
    putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
    put("data".toByteArray(Charsets.US_ASCII)); putInt(dataBytes)
}.array()

/** Fixed dBFS speech threshold; non-speech timeout avoids leaving a silent microphone open. */
internal class SpeechPauseDetector(private val silenceMillis: Long) {
    private var startedAt: Long? = null
    private var lastSpeechAt: Long? = null
    val hasSpeech: Boolean get() = lastSpeechAt != null
    fun shouldFinish(now: Long, decibels: Float): Boolean {
        if (startedAt == null) startedAt = now
        if (decibels > -42f) lastSpeechAt = now
        return lastSpeechAt?.let { now - it >= silenceMillis }
            ?: (now - checkNotNull(startedAt) >= 15_000)
    }
}
