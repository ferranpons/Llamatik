@file:OptIn(BetaInteropApi::class)

package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

@OptIn(ExperimentalForeignApi::class)
private fun writeBytesToFile(path: String, bytes: ByteArray, append: Boolean) {
    val newData = byteArrayToNSData(bytes)
    val dataToWrite: NSData = if (!append) {
        newData
    } else {
        val existing = NSData.create(contentsOfFile = path)
        if (existing == null) {
            newData
        } else {
            // concatenate existing + new
            val existingBytes = nsDataToByteArray(existing)
            val combined = ByteArray(existingBytes.size + bytes.size)
            combined.usePinned { pinned ->
                // copy existing
                existingBytes.usePinned { src ->
                    memcpy(pinned.addressOf(0), src.addressOf(0), existingBytes.size.convert())
                }
                // copy new
                bytes.usePinned { src ->
                    memcpy(
                        pinned.addressOf(existingBytes.size),
                        src.addressOf(0),
                        bytes.size.convert()
                    )
                }
            }
            byteArrayToNSData(combined)
        }
    }
    dataToWrite.writeToFile(path, true)
}

@OptIn(ExperimentalForeignApi::class)
private fun readBytesFromFile(path: String): ByteArray {
    val data = NSData.create(contentsOfFile = path) ?: return ByteArray(0)
    return nsDataToByteArray(data)
}

private fun tempPathFor(fileName: String): String {
    val baseDir = NSTemporaryDirectory()
    return if (baseDir.endsWith("/")) baseDir + fileName else "$baseDir/$fileName"
}

// ---------- actuals for extension functions ----------

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val path = tempPathFor(fileName)
    val buffer = ByteArray(8 * 1024)
    var first = true
    while (true) {
        val read = readAvailable(buffer, 0, buffer.size)
        if (read <= 0) break
        val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
        writeBytesToFile(path, chunk, append = !first)
        first = false
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

    @OptIn(ExperimentalEncodingApi::class)
    private fun base64Encode(bytes: ByteArray): String {
        // Use Kotlin's Base64 instead of deprecated NSData APIs
        return Base64.encode(bytes)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun base64Decode(base64: String): ByteArray {
        return try {
            Base64.decode(base64)
        } catch (_: IllegalArgumentException) {
            ByteArray(0)
        }
    }

    actual fun appendBytes(bytes: ByteArray) {
        writeBytesToFile(path, bytes, append = true)
    }

    actual fun readBytes(): ByteArray = readBytesFromFile(path)

    actual fun getBase64String(): String = base64Encode(readBytes())

    @OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun appendBytesBase64(bytes: ByteArray) {
        // Interpret incoming bytes as UTF-8 base64 text, decode, and append to the binary file
        val base64 = bytes.decodeToString()
        val decoded = base64Decode(base64)
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