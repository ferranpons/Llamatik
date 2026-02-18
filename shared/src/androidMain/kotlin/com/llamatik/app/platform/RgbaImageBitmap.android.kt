package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap

actual fun rgbaToImageBitmap(width: Int, height: Int, rgba: ByteArray): ImageBitmap? {
    return try {
        val expected = width * height * 4
        if (rgba.size < expected) return null

        val pixels = IntArray(width * height)
        var i = 0
        var p = 0
        while (p < pixels.size) {
            val r = rgba[i++].toInt() and 0xFF
            val g = rgba[i++].toInt() and 0xFF
            val b = rgba[i++].toInt() and 0xFF
            val a = rgba[i++].toInt() and 0xFF
            pixels[p++] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val bmp = createBitmap(width, height)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
