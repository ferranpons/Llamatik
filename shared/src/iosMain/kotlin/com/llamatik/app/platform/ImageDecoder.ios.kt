package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

actual fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String?): ImageBitmap? {
    try {
        if (bytes.size < 8) {
            saveTemp(bytes, suggestedFileName, ".bin")
            return null
        }
        val header = bytes.sliceArray(0 until minOf(12, bytes.size))
        val pngSig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (header.startsWith(pngSig)) {
            return try { Image.makeFromEncoded(bytes).asImageBitmap() } catch (_: Throwable) { null }
        }
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
            return try { Image.makeFromEncoded(bytes).asImageBitmap() } catch (_: Throwable) { null }
        }
        val ascii = header.toString(Charsets.US_ASCII)
        if (ascii.startsWith("P6")) {
            val png = convertPpmToPng(bytes) ?: run { saveTemp(bytes, suggestedFileName, ".ppm"); return null }
            return try { Image.makeFromEncoded(png).asImageBitmap() } catch (_: Throwable) { null }
        }

        saveTemp(bytes, suggestedFileName, ".bin")
    } catch (_: Throwable) {
        saveTemp(bytes, suggestedFileName, ".err")
    }
    return null
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (this.size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

private fun saveTemp(bytes: ByteArray, suggestedName: String?, ext: String) {
    try {
        val name = (suggestedName ?: "image") + ext
        val path = NSTemporaryDirectory().plus("/").plus(name)
        val data = NSData.create(bytes = bytes.toCValues().getPointer(), length = bytes.size.toULong()) // pseudocode - use correct bridging
        data.writeToFile(path, true)
    } catch (_: Throwable) { }
}

/** Very small PPM -> PNG converter implemented with Skia if available. */
private fun convertPpmToPng(bytes: ByteArray): ByteArray? {
    try {
        val s = String(bytes, 0, minOf(bytes.size, 1024), Charsets.US_ASCII)
        val headerEnd = s.indexOf('\n', s.indexOf('\n', s.indexOf('\n') + 1) + 1)
        if (headerEnd < 0) return null
        val headerFull = String(bytes, 0, headerEnd, Charsets.US_ASCII)
        val parts = headerFull.replace("\r", "").split(Regex("\\s+")).filter { it.isNotBlank() }
        val width = parts[1].toInt()
        val height = parts[2].toInt()
        val maxval = parts[3].toInt()
        val pixelStart = headerFull.toByteArray(Charsets.US_ASCII).size
        val expected = width * height * 3
        if (bytes.size < pixelStart + expected) return null
        val pixels = IntArray(width * height)
        var idx = pixelStart
        var pIndex = 0
        while (pIndex < pixels.size) {
            val r = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
            val g = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
            val b = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
            pixels[pIndex++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = org.jetbrains.skia.Bitmap().apply {
            allocN32Pixels(width, height)
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return bitmap.encodeToData(org.jetbrains.skia.ImageInfoFormat.PNG)?.bytes
    } catch (_: Throwable) {
        return null
    }
}
