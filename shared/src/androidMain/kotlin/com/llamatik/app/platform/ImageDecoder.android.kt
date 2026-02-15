package com.llamatik.app.platform

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

private const val TAG = "ImageDecoder"

actual fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String?): ImageBitmap? {
    try {
        Log.d(TAG, "decodeImage: size=${bytes.size}")
        if (bytes.size < 8) {
            Log.d(TAG, "decodeImage: too small")
            saveTempForInspection(bytes, suggestedFileName, ".bin")
            return null
        }

        val header = bytes.sliceArray(0 until minOf(12, bytes.size))

        // PNG
        val pngSig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        if (header.startsWith(pngSig)) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it.asImageBitmap() }
            Log.d(TAG, "PNG decode failed")
        }

        // JPEG
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it.asImageBitmap() }
            Log.d(TAG, "JPEG decode failed")
        }

        // WebP (RIFF....WEBP)
        val riff = "RIFF".toByteArray(Charset.forName("US-ASCII"))
        val webp = "WEBP".toByteArray(Charset.forName("US-ASCII"))
        if (header.startsWith(riff) && bytes.size >= 12 && bytes.sliceArray(8 until 12).contentEquals(webp)) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it.asImageBitmap() }
            Log.d(TAG, "WebP decode failed")
        }

        // ASCII PPM "P6\n"
        val asciiHeader = String(header, Charset.forName("US-ASCII"))
        if (asciiHeader.startsWith("P6")) {
            // Convert PPM -> Bitmap manually
            val bmp = decodePpmToBitmapAndroid(bytes)
            if (bmp != null) return bmp.asImageBitmap()
            Log.d(TAG, "PPM decode failed")
        }

        // Last resort: save file for inspection
        saveTempForInspection(bytes, suggestedFileName, ".bin")
    } catch (t: Throwable) {
        Log.w(TAG, "decodeImage exception", t)
        saveTempForInspection(bytes, suggestedFileName, ".err")
    }
    return null
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (this.size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

private fun saveTempForInspection(bytes: ByteArray, suggestedName: String?, ext: String) {
    try {
        val name = (suggestedName ?: "image") + ext
        val f = File("/tmp", name)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { it.write(bytes) }
        Log.d(TAG, "Saved temp image to: ${f.absolutePath}")
    } catch (t: Throwable) {
        Log.d(TAG, "saveTempForInspection failed: ${t.message}")
    }
}

/** Very small PPM (P6) parser -> android Bitmap. Assumes 8-bit channels. */
private fun decodePpmToBitmapAndroid(bytes: ByteArray): android.graphics.Bitmap? {
    try {
        val s = String(bytes, 0, minOf(bytes.size, 1024), Charset.forName("US-ASCII"))
        val headerEndIdx = s.indexOf('\n', s.indexOf('\n', s.indexOf('\n') + 1) + 1)
        if (headerEndIdx < 0) return null
        // Fallback: naive parse
        val headerFull = String(bytes, 0, headerEndIdx, Charset.forName("US-ASCII"))
        val parts = headerFull.replace("\r", "").split(Regex("\\s+")).filter { it.isNotBlank() }
        // P6, width, height, maxval
        if (parts.size < 4) return null
        val width = parts[1].toInt()
        val height = parts[2].toInt()
        val maxval = parts[3].toInt()
        val pixelStart = headerFull.toByteArray(Charset.forName("US-ASCII")).size
        val expected = width * height * 3
        if (bytes.size < pixelStart + expected) return null
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        var idx = pixelStart
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
                val g = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
                val b = (bytes[idx++].toInt() and 0xFF) * 255 / maxval
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                bmp.setPixel(x, y, color)
            }
        }
        return bmp
    } catch (_: Throwable) {
        return null
    }
}
