package com.llamatik.app.platform

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class AudioRecorder actual constructor() {
    actual val isRecording: Boolean
        get() = TODO("Not yet implemented")

    actual suspend fun start(outputWavPath: String) {
    }

    actual suspend fun stop(): String {
        TODO("Not yet implemented")
    }
}