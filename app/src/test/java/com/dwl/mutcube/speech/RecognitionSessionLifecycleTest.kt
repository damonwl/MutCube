package com.dwl.mutcube.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecognitionSessionLifecycleTest {
    @Test fun successfulSessionsDoNotCancelServiceAndInvalidateOldCallbacks() {
        var cancellations = 0
        val lifecycle = RecognitionSessionLifecycle { cancellations += 1 }
        repeat(3) {
            lifecycle.begin()
            val token = lifecycle.session
            lifecycle.complete()
            assertFalse(lifecycle.active)
            assertNotEquals(token, lifecycle.session)
        }
        assertEquals(0, cancellations)
    }

    @Test fun explicitCancellationOnlyCancelsAnActiveSessionOnce() {
        var cancellations = 0
        val lifecycle = RecognitionSessionLifecycle { cancellations += 1 }
        lifecycle.begin()
        lifecycle.cancel()
        lifecycle.cancel()
        lifecycle.begin()
        lifecycle.begin()
        assertEquals(2, cancellations)
    }
}
