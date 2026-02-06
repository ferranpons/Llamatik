package com.llamatik.app.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class AudioRecorder() {
    val isRecording: Boolean
    suspend fun start(outputWavPath: String)
    suspend fun stop(): String // returns wav path
}