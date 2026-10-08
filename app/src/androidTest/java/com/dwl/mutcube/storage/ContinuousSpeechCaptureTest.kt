package com.dwl.mutcube.storage

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.dwl.mutcube.speech.ContinuousSpeechRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ContinuousSpeechCaptureTest {
    @Test fun capturesCompleteWavAndReleasesMicrophone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        repeat(2) {
            val recorder = ContinuousSpeechRecorder.start(context)
            try {
                Thread.sleep(1_200)
                recorder.checkHealthy()
                assertTrue(recorder.amplitude in 0f..1f)
                val file = recorder.stop()
                try {
                    val bytes = file.readBytes()
                    assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
                    assertTrue(bytes.size > 16_000)
                    val dataBytes = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(40)
                    assertEquals(bytes.size - 44, dataBytes)
                } finally { file.delete() }
            } catch (error: Throwable) {
                recorder.discard()
                throw error
            }
        }
    }
}
