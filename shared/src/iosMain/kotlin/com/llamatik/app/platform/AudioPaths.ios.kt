package com.llamatik.app.platform

import platform.Foundation.NSTemporaryDirectory

actual object AudioPaths {
    actual fun tempWavPath(): String {
        val dir = NSTemporaryDirectory()
        return "$dir/llamatik_recording_16k_mono.wav"
    }
}
