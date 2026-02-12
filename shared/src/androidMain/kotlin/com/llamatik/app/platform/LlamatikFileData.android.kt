package com.llamatik.app.platform

import android.content.Context
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Base64

private const val MODEL_DIR_NAME = "models"

/**
 * Persistent location for model files:
 *   <filesDir>/models/<safePrefix>.gguf
 * During download we write:
 *   <filesDir>/models/<safePrefix>.gguf.part
 */
private fun modelsDir(context: Context): File =
    File(context.filesDir, MODEL_DIR_NAME).apply { mkdirs() }

private fun stableModelFile(context: Context, fileName: String): File {
    val safePrefix = sanitizeTempPrefix(fileName)
    return File(modelsDir(context), "$safePrefix.gguf")
}

private fun partialModelFile(context: Context, fileName: String): File {
    val safePrefix = sanitizeTempPrefix(fileName)
    return File(modelsDir(context), "$safePrefix.gguf.part")
}

private fun ensureParent(file: File) {
    file.parentFile?.mkdirs()
}

/**
 * Optional: migrate a previously-saved path in cacheDir into filesDir/models.
 *
 * - If `savedPath` points to a cache file that exists, we move it to the stable
 *   destination (based on `modelNameOrFileName`), and return the NEW path.
 * - If it already points to a filesDir model, we just return it.
 * - If the file doesn't exist, returns the original `savedPath` (caller can handle).
 */
actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    val context = AndroidContextHolder.appContext
    if (savedPath.isBlank()) return savedPath

    val saved = File(savedPath)
    if (!saved.exists()) return savedPath

    // Already in persistent models dir
    val persistentDir = modelsDir(context).absolutePath
    if (saved.absolutePath.startsWith(persistentDir)) return savedPath

    // Move into persistent stable path
    val dest = stableModelFile(context, modelNameOrFileName)
    ensureParent(dest)
    if (dest.exists()) dest.delete()

    val moved = saved.renameTo(dest)
    if (moved) {
        // also cleanup any leftover .part in same "model name" bucket
        File(dest.absolutePath + ".part").delete()
        return dest.absolutePath
    }

    // If rename fails (cross-device / weird FS), fallback to copy+delete
    return try {
        FileInputStream(saved).use { input ->
            FileOutputStream(dest, false).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
        saved.delete()
        dest.absolutePath
    } catch (_: Throwable) {
        // If migration fails, keep old path
        savedPath
    }
}

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val ctx = AndroidContextHolder.appContext
    val part = partialModelFile(ctx, fileName)
    val final = stableModelFile(ctx, fileName)

    ensureParent(part)
    if (part.exists()) part.delete()

    FileOutputStream(part, false).use { fos ->
        val buffer = ByteArray(256 * 1024) // 256KB
        while (!isClosedForRead) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break
            fos.write(buffer, 0, read)
        }
        fos.flush()
        fos.fd.sync()
    }

    if (final.exists()) final.delete()
    if (!part.renameTo(final)) {
        throw IllegalStateException("Failed to finalize model file. Could not rename ${part.absolutePath} -> ${final.absolutePath}")
    }
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    val ctx = AndroidContextHolder.appContext
    val part = partialModelFile(ctx, fileName)
    val final = stableModelFile(ctx, fileName)

    ensureParent(part)
    if (part.exists()) part.delete()

    FileOutputStream(part, false).use { fos ->
        fos.write(this)
        fos.flush()
        fos.fd.sync()
    }

    if (final.exists()) final.delete()
    if (!part.renameTo(final)) {
        throw IllegalStateException("Failed to finalize model file. Could not rename ${part.absolutePath} -> ${final.absolutePath}")
    }
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val ctx = AndroidContextHolder.appContext
    val part = partialModelFile(ctx, fileName)

    ensureParent(part)
    FileOutputStream(part, /* append = */ true).use { fos ->
        fos.write(this)
        fos.flush()
        fos.fd.sync()
    }
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val app = AndroidContextHolder.appContext
    private val safePrefix = sanitizeTempPrefix(fileName)

    private val finalFile: File = stableModelFile(app, fileName)
    private val partFile: File = partialModelFile(app, fileName)

    private val base64file: File = File(modelsDir(app), "$safePrefix.txt").apply { ensureParent(this) }

    private val base64fileStream = FileOutputStream(base64file, /* append = */ true)
    private val base64Encoder = Base64.getEncoder().wrap(base64fileStream)

    actual fun readBytes(): ByteArray {
        val f = if (finalFile.exists()) finalFile else partFile
        RandomAccessFile(f.path, "r").use { raf ->
            val channel: FileChannel = raf.channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)
            return byteArray
        }
    }

    actual fun appendBytes(bytes: ByteArray) {
        ensureParent(partFile)
        FileOutputStream(partFile, /* append = */ true).use { out ->
            out.write(bytes)
            out.flush()
            out.fd.sync()
        }
    }

    actual fun getBase64String(): String {
        val outputStream = ByteArrayOutputStream()
        encodeFileToBase64(outputStream)
        return outputStream.toString("UTF-8")
    }

    private fun encodeFileToBase64(output: OutputStream) {
        val buffer = ByteArray(4096)
        val localBase64Encoder = Base64.getEncoder().wrap(output)

        FileInputStream(base64file.path).use { inputStream ->
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
        runCatching { base64Encoder.close() }

        if (partFile.exists()) {
            if (finalFile.exists()) finalFile.delete()
            val ok = partFile.renameTo(finalFile)
            if (!ok) {
                throw IllegalStateException("Failed to finalize model file. Could not rename ${partFile.absolutePath} -> ${finalFile.absolutePath}")
            }
        }
    }

    actual fun readBase64String(): String {
        val sb = StringBuilder()
        base64file.bufferedReader().use { reader ->
            reader.forEachLine { line -> sb.append(line) }
        }
        return sb.toString()
    }

    actual fun absolutePath(): String =
        if (finalFile.exists()) finalFile.absolutePath else partFile.absolutePath

    actual fun delete(path: String): Boolean {
        return try {
            val f = File(path)
            val ok1 = if (f.exists()) f.delete() else true
            val ok2 = File("$path.part").let { if (it.exists()) it.delete() else true }
            ok1 && ok2
        } catch (_: Throwable) {
            false
        }
    }
}

private fun sanitizeTempPrefix(input: String): String {
    val cleaned = input
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(64) // allow a bit more uniqueness, still safe
    return when {
        cleaned.length >= 3 -> cleaned
        cleaned.isBlank() -> "tmp"
        else -> "tmp_$cleaned"
    }
}
