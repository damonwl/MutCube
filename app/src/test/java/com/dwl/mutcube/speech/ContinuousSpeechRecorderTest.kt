package com.dwl.mutcube.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ContinuousSpeechRecorderTest {
    @Test fun streamingPcmUses24KhzWavHeader() {
        val header = ByteBuffer.wrap(wavHeader(4_800, 24_000)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(24_000, header.getInt(24))
        assertEquals(48_000, header.getInt(28))
        assertEquals(4_800, header.getInt(40))
    }
    @Test fun pauseAllowsContinuationAndThenFinishes() {
        val detector = SpeechPauseDetector(5_000)
        assertFalse(detector.shouldFinish(0, -20f))
        assertFalse(detector.shouldFinish(3_000, -80f))
        assertFalse(detector.shouldFinish(4_000, -25f))
        assertFalse(detector.shouldFinish(8_000, -80f))
        assertTrue(detector.shouldFinish(9_000, -80f))
    }
    @Test fun noSpeechFinishesAfterFifteenSeconds() {
        val detector = SpeechPauseDetector(5_000)
        assertFalse(detector.shouldFinish(0, -80f))
        assertFalse(detector.shouldFinish(14_900, -80f))
        assertTrue(detector.shouldFinish(15_000, -80f))
    }
    @Test fun wavHeaderMatchesCapturedPcm() {
        val header = wavHeader(3_200)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, header.size)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals(3_236, buffer.getInt(4))
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(3_200, buffer.getInt(40))
    }
}
