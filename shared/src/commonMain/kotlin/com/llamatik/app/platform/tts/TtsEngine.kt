package com.llamatik.app.platform.tts

/**
 * Thin, platform-backed TTS abstraction.
 *
 * Llamatik uses native OS voices per platform (Android TextToSpeech, iOS AVSpeechSynthesizer, etc.).
 * Desktop/JVM can provide a real implementation later; for now it may be a no-op.
 */
interface TtsEngine {
    /** True if this platform/engine can actually speak. */
    val isAvailable: Boolean

    /**
     * Speak [text].
     *
     * If [interrupt] is true, any previous speech should be stopped/overridden.
     */
    suspend fun speak(text: String, interrupt: Boolean = true)

    /** Stop any current speech immediately. */
    fun stop()
}
