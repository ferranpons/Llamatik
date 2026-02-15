package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.logging.Logger
import javax.imageio.ImageIO

private val log = Logger.getLogger("ImageDecoderJvm")

actual fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String?): ImageBitmap? {
    try {
        log.info("decodeImage: size=${bytes.size}")
        if (bytes.size < 8) {
            saveTemp(bytes, suggestedFileName, ".bin")
            return null
        }

        val header = bytes.sliceArray(0 until minOf(12, bytes.size))

        // Quick format detection
        val pngSig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val isPng = header.startsWith(pngSig)
        val isJpeg = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()
        val isWebp = header.startsWith("RIFF".toByteArray(Charset.forName("US-ASCII"))) &&
                bytes.size >= 12 &&
                bytes.sliceArray(8 until 12).contentEquals("WEBP".toByteArray(Charset.forName("US-ASCII")))

        val asciiHeader = String(header, Charset.forName("US-ASCII"))
        val isPpm = asciiHeader.startsWith("P6") || asciiHeader.startsWith("P3")

        // ✅ Standard encoded image (PNG/JPEG/WebP if ImageIO supports it)
        if (isPng || isJpeg || isWebp) {
            val img = ImageIO.read(ByteArrayInputStream(bytes))
            if (img != null) return img.toComposeImageBitmap()
            log.info("ImageIO failed to read encoded image")
            saveTemp(bytes, suggestedFileName, if (isPng) ".png" else if (isJpeg) ".jpg" else ".webp")
            return null
        }

        // ✅ PPM (P6/P3)
        if (isPpm) {
            val img = decodePpmToBufferedImage(bytes)
            if (img != null) return img.toComposeImageBitmap()
            log.info("PPM decode failed")
            saveTemp(bytes, suggestedFileName, ".ppm")
            return null
        }

        // Unknown format: save for inspection
        saveTemp(bytes, suggestedFileName, ".bin")
        return null
    } catch (t: Throwable) {
        log.warning("decodeImage exception: ${t.message}")
        saveTemp(bytes, suggestedFileName, ".err")
        return null
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (this.size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}

private fun saveTemp(bytes: ByteArray, suggestedName: String?, ext: String) {
    try {
        val name = (suggestedName ?: "image") + ext
        val f = File(System.getProperty("java.io.tmpdir"), name)
        f.parentFile?.mkdirs()
        FileOutputStream(f).use { it.write(bytes) }
        log.info("Saved temp image to: ${f.absolutePath}")
    } catch (t: Throwable) {
        log.warning("saveTemp failed: ${t.message}")
    }
}

/**
 * Minimal PPM parser supporting P6 (binary) and P3 (ascii).
 * Returns a BufferedImage (ARGB).
 */
private fun decodePpmToBufferedImage(bytes: ByteArray): BufferedImage? {
    return try {
        val (magic, width, height, maxVal, dataOffset) = parsePpmHeader(bytes) ?: return null
        if (width <= 0 || height <= 0 || maxVal <= 0) return null

        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        when (magic) {
            "P6" -> {
                val needed = width * height * 3
                if (bytes.size < dataOffset + needed) return null

                var idx = dataOffset
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val r = (bytes[idx++].toInt() and 0xFF) * 255 / maxVal
                        val g = (bytes[idx++].toInt() and 0xFF) * 255 / maxVal
                        val b = (bytes[idx++].toInt() and 0xFF) * 255 / maxVal
                        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        img.setRGB(x, y, argb)
                    }
                }
            }

            "P3" -> {
                // ASCII pixel data: r g b r g b ...
                val text = String(bytes, dataOffset, bytes.size - dataOffset, Charsets.US_ASCII)
                val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
                val needed = width * height * 3
                if (tokens.size < needed) return null

                var t = 0
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val r = tokens[t++].toInt().coerceIn(0, maxVal) * 255 / maxVal
                        val g = tokens[t++].toInt().coerceIn(0, maxVal) * 255 / maxVal
                        val b = tokens[t++].toInt().coerceIn(0, maxVal) * 255 / maxVal
                        val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        img.setRGB(x, y, argb)
                    }
                }
            }

            else -> return null
        }

        img
    } catch (_: Throwable) {
        null
    }
}

/**
 * Parses PPM header (supports comments).
 * Returns: (magic, width, height, maxVal, dataOffset)
 */
private fun parsePpmHeader(bytes: ByteArray): PpmHeader? {
    // Tokenize header while skipping comments (# ...)
    val tokens = ArrayList<String>(4)
    var i = 0

    fun skipWhitespace() {
        while (i < bytes.size) {
            val c = bytes[i].toInt().toChar()
            if (!c.isWhitespace()) break
            i++
        }
    }

    fun skipCommentIfAny() {
        if (i < bytes.size && bytes[i].toInt().toChar() == '#') {
            while (i < bytes.size && bytes[i].toInt().toChar() != '\n') i++
        }
    }

    fun readToken(): String? {
        skipWhitespace()
        skipCommentIfAny()
        skipWhitespace()
        if (i >= bytes.size) return null
        val start = i
        while (i < bytes.size) {
            val c = bytes[i].toInt().toChar()
            if (c.isWhitespace() || c == '#') break
            i++
        }
        return String(bytes, start, i - start, Charsets.US_ASCII)
    }

    while (tokens.size < 4) {
        val tok = readToken() ?: return null
        if (tok.startsWith("#")) continue
        tokens.add(tok)
        skipCommentIfAny()
    }

    val magic = tokens[0]
    val width = tokens[1].toIntOrNull() ?: return null
    val height = tokens[2].toIntOrNull() ?: return null
    val maxVal = tokens[3].toIntOrNull() ?: return null

    // The remaining byte after one whitespace is pixel data start
    // Ensure we move to next non-whitespace byte
    skipWhitespace()
    val dataOffset = i

    return PpmHeader(magic, width, height, maxVal, dataOffset)
}

private data class PpmHeader(
    val magic: String,
    val width: Int,
    val height: Int,
    val maxVal: Int,
    val dataOffset: Int
)
