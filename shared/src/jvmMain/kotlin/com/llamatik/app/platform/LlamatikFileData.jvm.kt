package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
    // Creates a temporary .gguf file in the default temp directory
    val file = createTempFile(
        prefix = fileName,
        suffix = ".gguf"
    )
    val bytes = this.readRemaining().readBytes()
    file.writeBytes(bytes)
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    val file = createTempFile(
        prefix = fileName,
        suffix = ".gguf"
    )
    file.writeBytes(this)
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val file = createTempFile(
        prefix = fileName,
        suffix = ".gguf"
    )
    file.writeBytes(this)
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val file = createTempFile(
        prefix = fileName,
        suffix = ".gguf"
    )

    private val base64file = createTempFile(
        prefix = fileName,
        suffix = ".txt"
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
        return outputStream.toString(Charsets.UTF_8.name())
    }

    private fun encodeFileToBase64(output: OutputStream) {
        val buffer = ByteArray(4096) // 4 KB buffer
        val encoder = Base64.getEncoder().wrap(output)

        FileInputStream(base64file.pathString).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                encoder.write(buffer, 0, bytesRead)
            }
        }
        encoder.close() // Ensure proper completion of Base64 encoding
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
        base64Encoder.write(bytes)
    }

    actual fun close() {
        base64Encoder.close()
    }

    actual fun readBase64String(): String {
        val sb = StringBuilder()
        base64file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                sb.append(line)
            }
        }
        return sb.toString()
    }

    actual fun absolutePath(): String = file.toString()

    actual fun delete(path: String): Boolean {
        return try {
            val file = java.io.File(path)
            if (file.exists()) {
                file.delete()
            } else {
                true // treat "already gone" as success
            }
        } catch (_: Throwable) {
            false
        }
    }
}

actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    if (savedPath.isBlank()) return savedPath

    val saved = File(savedPath)
    if (!saved.exists()) return savedPath

    val persistentDir = modelsDirJvm().absolutePath

    // Already in persistent models dir
    if (saved.absolutePath.startsWith(persistentDir)) return savedPath

    val dest = stableModelFileJvm(modelNameOrFileName)
    dest.parentFile?.mkdirs()

    if (dest.exists()) dest.delete()

    // Try move first
    val moved = saved.renameTo(dest)
    if (moved) {
        File(dest.absolutePath + ".part").delete()
        return dest.absolutePath
    }

    // Fallback: copy + delete
    return try {
        FileInputStream(saved).use { input ->
            FileOutputStream(dest, false).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        saved.delete()
        File(dest.absolutePath + ".part").delete()
        dest.absolutePath
    } catch (_: Throwable) {
        savedPath
    }
}

private fun modelsDirJvm(): File {
    // Stable per-user location
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".llamatik/models").apply { mkdirs() }
}

private fun stableModelFileJvm(modelNameOrFileName: String): File {
    val safe = sanitizeFileName(modelNameOrFileName).ifBlank { "model" }
    return File(modelsDirJvm(), "$safe.gguf")
}

private fun sanitizeFileName(input: String): String {
    val cleaned = input
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(64)
    return when {
        cleaned.length >= 3 -> cleaned
        cleaned.isBlank() -> "tmp"
        else -> "tmp_$cleaned"
    }
}
