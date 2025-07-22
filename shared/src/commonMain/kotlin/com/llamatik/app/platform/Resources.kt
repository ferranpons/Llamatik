package com.llamatik.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
expect fun font(name: String, res: String, weight: FontWeight, style: FontStyle): Font

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class AudioPlayer() {
    fun loadSound(resource: String): Int?

    @Composable
    fun loadMusic(path: String)
    fun playMusic()
    fun playSound(position: Int)
    fun pause()
    fun resume()
    fun release()
}

expect fun readResourceFile(fileName: String): String
