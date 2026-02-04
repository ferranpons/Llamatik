package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Base64
import kotlin.io.path.appendBytes
import kotlin.io.path.bufferedReader
import kotlin.io.path.createTempFile
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val safePrefix = sanitizeTempPrefix(fileName)
    val file = createTempFile(prefix = safePrefix, suffix = ".gguf")

    RandomAccessFile(file.pathString, "rw").use { raf ->
        val buffer = ByteArray(256 * 1024) // 256KB
        while (!isClosedForRead) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            raf.write(buffer, 0, read)
        }
    }
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    val safePrefix = sanitizeTempPrefix(fileName)
    val file = createTempFile(prefix = safePrefix, suffix = ".gguf")
    file.writeBytes(this)
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val safePrefix = sanitizeTempPrefix(fileName)
    val file = createTempFile(prefix = safePrefix, suffix = ".gguf")
    file.writeBytes(this)
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val safePrefix = sanitizeTempPrefix(fileName)

    private val file = createTempFile(
        prefix = safePrefix, suffix = ".gguf"
    )

    private val base64file = createTempFile(
        prefix = safePrefix, suffix = ".txt"
    )

    private val base64fileStream = base64file.outputStream()
    private val base64Encoder = Base64.getEncoder().wrap(base64fileStream)

    actual fun readBytes(): ByteArray {
        RandomAccessFile(file.pathString, "r").use { raf ->
            val channel: FileChannel = raf.channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)
            return byteArray
        }
    }

    actual fun appendBytes(bytes: ByteArray) {
        file.appendBytes(bytes)
    }

    actual fun getBase64String(): String {
        val outputStream = ByteArrayOutputStream()
        encodeFileToBase64(outputStream)
        return outputStream.toString("UTF-8")
    }

    private fun encodeFileToBase64(output: OutputStream) {
        val buffer = ByteArray(4096) // 4 KB buffer
        val localBase64Encoder = Base64.getEncoder().wrap(output)

        FileInputStream(base64file.pathString).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                localBase64Encoder.write(buffer, 0, bytesRead)
            }
        }
        localBase64Encoder.close()
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
        base64Encoder.write(bytes)
    }

    actual fun close() {
        base64Encoder.close()
    }

    actual fun readBase64String(): String {
        val stringBuilder = StringBuilder()
        base64file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                stringBuilder.append(line)
            }
        }
        return stringBuilder.toString()
    }

    actual fun absolutePath(): String = file.toString()

    actual fun delete(path: String): Boolean {
        return try {
            val f = java.io.File(path)
            if (f.exists()) f.delete() else true
        } catch (_: Throwable) {
            false
        }
    }
}

private fun sanitizeTempPrefix(input: String): String {
    val cleaned = input
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(32)
    return when {
        cleaned.length >= 3 -> cleaned
        cleaned.isBlank() -> "tmp"
        else -> "tmp_$cleaned"
    }
}
