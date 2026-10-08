package com.dwl.mutcube.template.ui

/** The host owns playback, microphone capture, provider credentials and audio files. */
interface TemplateSpeechHost {
    /** Installation permission is separate from a usable, user-configured speech service. */
    suspend fun isConfigured(capability: String): Boolean
    suspend fun speak(text: String, onState: (String) -> Unit)
    fun stopSpeaking()
    suspend fun recognize(language: String?, onState: (String, Float) -> Unit): String
    fun stopRecognition()
    fun cancelRecognition()
}
