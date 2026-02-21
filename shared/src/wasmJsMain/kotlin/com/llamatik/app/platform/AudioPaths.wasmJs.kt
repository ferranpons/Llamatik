package com.llamatik.app.platform

actual object AudioPaths {
    actual fun tempWavPath(): String {
        // In the browser we don't have a real filesystem path.
        // We use a logical key that other wasm implementations (AudioRecorder/AppStorage)
        // can interpret.
        return "tmp/recording.wav"
    }
}