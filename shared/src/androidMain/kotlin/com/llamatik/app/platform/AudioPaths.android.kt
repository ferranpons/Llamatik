package com.llamatik.app.platform

import android.content.Context
import java.io.File

object AndroidContextHolder {
    lateinit var appContext: Context
}

actual object AudioPaths {
    actual fun tempWavPath(): String {
        val f = File(AndroidContextHolder.appContext.cacheDir, "llamatik_recording_16k_mono.wav")
        return f.absolutePath
    }
}