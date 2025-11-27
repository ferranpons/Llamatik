package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSDataBase64Encoding64CharacterLineLength
import platform.Foundation.NSDataBase64EncodingEndLineWithLineFeed
import platform.Foundation.NSDataBase64EncodingOptions
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.memcpy
import platform.posix.open
import platform.posix.write

/**
 * iOS implementation optimized for large streaming downloads.
 *
 * Key points:
 * - All *writing* uses POSIX open/write/close (no repeated read+concat).
 * - Base64 helpers still use NSData, but only on-demand.
 */

// ---------- Small helpers ----------

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun byteArrayToNSData(bytes: ByteArray): NSData =
    bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }

@OptIn(ExperimentalForeignApi::class)
private fun nsDataToByteArray(data: NSData): ByteArray {
    val length = data.length.toInt()
    val result = ByteArray(length)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length.convert())
    }
    return result
}

/**
 * Low-level streaming write using POSIX APIs.
 * This does NOT read the existing file – it just appends or overwrites.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun writeBytesToFile(path: String, bytes: ByteArray, append: Boolean) {
    val flags = if (append) {
        O_WRONLY or O_CREAT or O_APPEND
    } else {
        O_WRONLY or O_CREAT or O_TRUNC
    }

    val fd = open(path, flags, 0x644)
    if (fd < 0) {
        // Could log errno here if you want
        return
    }

    try {
        bytes.usePinned { pinned ->
            var remaining = bytes.size
            var offset = 0
            while (remaining > 0) {
                val written = write(fd, pinned.addressOf(offset), remaining.convert())
                if (written <= 0) {
                    // error or interrupted; you might check errno if needed
                    break
                }
                val w = written.toInt()
                remaining -= w
                offset += w
            }
        }
    } finally {
        close(fd)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun readBytesFromFile(path: String): ByteArray {
    val data = NSData.create(contentsOfFile = path) ?: return ByteArray(0)
    return nsDataToByteArray(data)
}

private fun tempPathFor(fileName: String): String {
    val baseDir = NSTemporaryDirectory()
    return if (baseDir.endsWith("/")) baseDir + fileName else "$baseDir/$fileName"
}

// ---------- actuals for extension functions ----------

/**
 * Stream download to file using a single open() + write(...) loop.
 * This is the hot path used when downloading models.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val path = tempPathFor(fileName)

    val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x644)
    if (fd < 0) {
        // Could log: "Failed to open $path, errno=$errno"
        return
    }

    try {
        // Larger buffer for better throughput on iOS
        val buffer = ByteArray(256 * 1024)

        while (true) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break

            buffer.usePinned { pinned ->
                var remaining = read
                var offset = 0
                while (remaining > 0) {
                    val written =
                        write(fd, pinned.addressOf(offset), remaining.convert())
                    if (written <= 0) {
                        // error or interrupted
                        remaining = 0
                        break
                    }
                    val w = written.toInt()
                    remaining -= w
                    offset += w
                }
            }
        }
    } finally {
        close(fd)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.writeToFile(fileName: String) {
    val path = tempPathFor(fileName)
    writeBytesToFile(path, this, append = false)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val path = tempPathFor(fileName)
    writeBytesToFile(path, this, append = true)
}

// ---------- LlamatikTempFile implementation ----------

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val path: String = tempPathFor(fileName)

    @OptIn(ExperimentalForeignApi::class)
    private fun base64Encode(bytes: ByteArray): String {
        val data = byteArrayToNSData(bytes)
        val options: NSDataBase64EncodingOptions =
            NSDataBase64Encoding64CharacterLineLength or NSDataBase64EncodingEndLineWithLineFeed
        return data.base64EncodedStringWithOptions(options)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun base64Decode(base64: String): ByteArray {
        val data = NSData.create(
            base64EncodedString = base64,
            options = NSDataBase64DecodingIgnoreUnknownCharacters
        ) ?: return ByteArray(0)
        return nsDataToByteArray(data)
    }

    actual fun appendBytes(bytes: ByteArray) {
        writeBytesToFile(path, bytes, append = true)
    }

    actual fun readBytes(): ByteArray = readBytesFromFile(path)

    actual fun getBase64String(): String = base64Encode(readBytes())

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun appendBytesBase64(bytes: ByteArray) {
        // Interpret incoming bytes as UTF-8 base64 text, decode, and append to the binary file
        val nsString = NSString.create(
            data = byteArrayToNSData(bytes),
            encoding = NSUTF8StringEncoding
        ) ?: return
        val decoded = base64Decode(nsString as String)
        if (decoded.isNotEmpty()) {
            appendBytes(decoded)
        }
    }

    actual fun close() {
        // No persistent handles – everything is opened/closed per call.
    }

    actual fun readBase64String(): String = getBase64String()

    actual fun absolutePath(): String = path
}