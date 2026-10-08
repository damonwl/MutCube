package com.dwl.mutcube.speech

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NetworkSpeechPlaybackTest {
    @Test fun temporaryAudioIsDeletedAfterPlaybackFailureAndCancellation() = runBlocking {
        for (cancel in listOf(false, true)) {
            val files = java.util.Collections.synchronizedList(mutableListOf<File>())
            val playing = CompletableDeferred<Unit>()
            val job = launch {
                runCatching {
                    playSpeechSegments(listOf("one", "two", "three"), {
                        File.createTempFile("mutcube-tts-", ".wav").also { file -> files.add(file) }
                    }, {}, {
                        playing.complete(Unit)
                        if (cancel) awaitCancellation() else error("Playback failed")
                    })
                }
            }
            withTimeout(2_000) {
                playing.await()
                if (cancel) job.cancelAndJoin() else job.join()
            }
            assertTrue(files.isNotEmpty())
            assertTrue("TTS temporary files leaked", files.all { !it.exists() })
        }
    }

    @Test fun segmentsPlayInOrderWithPrefetch() = runBlocking {
        val playing = CompletableDeferred<Unit>()
        val nextGenerated = CompletableDeferred<Unit>()
        val played = mutableListOf<String>()
        playSpeechSegments(listOf("one", "two"), { text ->
            if (text == "two") { playing.await(); nextGenerated.complete(Unit) }
            File(text)
        }, {}, { file ->
            played += file.name
            if (file.name == "one") { playing.complete(Unit); withTimeout(2_000) { nextGenerated.await() } }
        })
        assertEquals(listOf("one", "two"), played)
    }

    @Test fun cancellationStopsPlaybackAndPrefetch() = runBlocking {
        val playing = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val job = launch {
            playSpeechSegments(listOf("one", "two", "three"), { File(it) }, {}, {
                try { playing.complete(Unit); awaitCancellation() } finally { stopped.complete(Unit) }
            })
        }
        withTimeout(2_000) { playing.await(); job.cancelAndJoin(); stopped.await() }
        assertTrue(job.isCancelled)
    }
}
