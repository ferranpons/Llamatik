package com.llamatik.app.platform

import android.os.Build
import android.os.FileUtils
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
actual fun readResourceFile(fileName: String): String {
    val inputStream = FileUtils::class.java.classLoader!!
        .getResourceAsStream(fileName)
    return inputStream.bufferedReader().use { it.readText() }
}
