package com.llamatik.app.platform

import android.annotation.SuppressLint
import android.os.Build
import android.os.FileUtils
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@RequiresApi(Build.VERSION_CODES.Q)
actual fun readResourceFile(fileName: String): String {
    val inputStream = FileUtils::class.java.classLoader!!
        .getResourceAsStream(fileName)
    return inputStream.bufferedReader().use { it.readText() }
}

@SuppressLint("DiscouragedApi")
@Composable
actual fun font(name: String, res: String, weight: FontWeight, style: FontStyle): Font {
    val context = LocalContext.current
    val id = context.resources.getIdentifier(res, "font", context.packageName)
    return Font(id, weight, style)
}