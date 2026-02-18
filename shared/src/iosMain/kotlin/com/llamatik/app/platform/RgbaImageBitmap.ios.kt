package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun rgbaToImageBitmap(width: Int, height: Int, rgba: ByteArray): ImageBitmap? {
    return try {
        val expected = width * height * 4
        if (rgba.size < expected) return null

        val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val data = Data.makeFromBytes(rgba.copyOf(expected))
        Image.makeRaster(info, data, width * 4).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
