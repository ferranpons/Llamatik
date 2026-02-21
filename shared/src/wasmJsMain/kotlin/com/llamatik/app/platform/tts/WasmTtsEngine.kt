package com.llamatik.app.platform.tts

class WasmTtsEngine : TtsEngine {

    private sealed class Backend {
        data class Mac(val cmd: String = "say") : Backend()
        data class Windows(val cmd: String = "powershell") : Backend()
        data class Linux(val cmd: String) : Backend()
    }

    private val backend: Backend? = detectBackend()

    override val isAvailable: Boolean
        get() = backend != null

    override suspend fun speak(text: String, interrupt: Boolean) {

    }

    override fun stop() {
    }

    private fun detectBackend(): Backend? {
        return null
    }

    private fun commandExists(cmd: String): Boolean {
        return false
    }
}
