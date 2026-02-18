package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

actual fun rgbaToImageBitmap(width: Int, height: Int, rgba: ByteArray): ImageBitmap? {
    return try {
        val expected = width * height * 4
        if (rgba.size < expected) return null

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = rgba[i++].toInt() and 0xFF
                val g = rgba[i++].toInt() and 0xFF
                val b = rgba[i++].toInt() and 0xFF
                val a = rgba[i++].toInt() and 0xFF
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                img.setRGB(x, y, argb)
            }
        }

        img.toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
