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

@androidx.compose.runtime.Composable
actual fun font(
    name: String,
    res: String,
    weight: androidx.compose.ui.text.font.FontWeight,
    style: androidx.compose.ui.text.font.FontStyle
): androidx.compose.ui.text.font.Font {

}