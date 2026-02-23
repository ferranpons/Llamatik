package com.llamatik.app.platform.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

class AppleTtsEngine : TtsEngine {

    private val synthesizer = AVSpeechSynthesizer()

    override val isAvailable: Boolean = true

    override suspend fun speak(text: String, interrupt: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        // AVSpeechSynthesizer is safest to use on the main thread.
        withContext(Dispatchers.Main) {
            if (interrupt) {
                runCatching { synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate) }
            }

            val utterance = AVSpeechUtterance(string = trimmed)

            // Best-effort: pick a voice that matches current locale language if available.
            runCatching {
                val lang = NSLocale.currentLocale.languageCode
                if (lang != null) {
                    utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(lang)
                }
            }

            synthesizer.speakUtterance(utterance)
        }
    }

    override fun stop() {
        runCatching { synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate) }
    }
}
