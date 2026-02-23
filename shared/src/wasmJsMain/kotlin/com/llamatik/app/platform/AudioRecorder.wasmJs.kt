package com.llamatik.app.platform

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class AudioRecorder actual constructor() {
    private var recording = false
    private var lastPath: String = AudioPaths.tempWavPath()

    actual val isRecording: Boolean
        get() = recording

    actual suspend fun start(outputWavPath: String) {
        // Proper microphone capture (MediaRecorder/WebAudio) requires a JS glue layer.
        // For now we provide a no-crash stub so the app compiles/runs on wasm.
        // When wired, this should write a valid WAV to AppStorage under outputWavPath.
        lastPath = outputWavPath
        recording = true
    }

    actual suspend fun stop(): String {
        recording = false
        return lastPath
    }
}
