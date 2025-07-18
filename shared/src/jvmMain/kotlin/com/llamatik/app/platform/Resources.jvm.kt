package com.llamatik.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes

private val cache: MutableMap<String, Font> = mutableMapOf()

@OptIn(InternalResourceApi::class)
@Composable
actual fun font(name: String, res: String, weight: FontWeight, style: FontStyle): Font {
    return cache.getOrPut(res) {
        val byteArray = runBlocking {
            readResourceBytes("font/$res.ttf")
        }
        androidx.compose.ui.text.platform.Font(res, byteArray, weight, style)
    }
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AudioPlayer {
    actual fun loadSound(resource: String): Int? {
        return null
    }

    @Composable
    actual fun loadMusic(path: String) {
    }

    actual fun playMusic() {
    }

    actual fun pause() {
    }

    actual fun resume() {
    }

    actual fun release() {
    }

    actual fun playSound(position: Int) {
    }
}

actual fun readResourceFile(fileName: String): String {
    return ""
}
