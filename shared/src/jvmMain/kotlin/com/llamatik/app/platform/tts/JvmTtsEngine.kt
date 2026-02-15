package com.llamatik.app.platform.tts

/**
 * Desktop/JVM TTS is intentionally a no-op for now to avoid adding heavyweight dependencies.
 *
 * If you want real desktop TTS later, consider:
 * - macOS native target: use AVSpeechSynthesizer (like iOS)
 * - Windows/Linux JVM: bridge to OS tools (eSpeak, SAPI) or a JVM TTS library
 */
class JvmTtsEngine : TtsEngine {
    override val isAvailable: Boolean = false

    override suspend fun speak(text: String, interrupt: Boolean) {
        // no-op
    }

    override fun stop() {
        // no-op
    }
}
