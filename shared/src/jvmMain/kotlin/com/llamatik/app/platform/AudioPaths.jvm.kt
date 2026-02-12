package com.llamatik.app.platform

import java.io.File

actual object AudioPaths {
    actual fun tempWavPath(): String {
        val dir = System.getProperty("java.io.tmpdir") ?: "."
        return File(dir, "llamatik_recording_16k_mono.wav").absolutePath
    }
}
