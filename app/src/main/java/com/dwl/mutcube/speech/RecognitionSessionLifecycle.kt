package com.dwl.mutcube.speech

/** Completing a result invalidates callbacks without cancelling the reusable system service. */
internal class RecognitionSessionLifecycle(private val cancelService: () -> Unit) {
    var session = 0
        private set
    var active = false
        private set

    fun begin() {
        if (active) cancel()
        session += 1
        active = true
    }

    fun complete() {
        session += 1
        active = false
    }

    fun cancel() {
        val wasActive = active
        complete()
        if (wasActive) cancelService()
    }
}
