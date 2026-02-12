@file:OptIn(BetaInteropApi::class)

package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSDataBase64Encoding64CharacterLineLength
import platform.Foundation.NSDataBase64EncodingEndLineWithLineFeed
import platform.Foundation.NSDataBase64EncodingOptions
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.truncateFileAtOffset
import platform.Foundation.writeData
import platform.posix.memcpy

/**
 * iOS implementation optimized for large streaming downloads.
 *
 * Key points:
 * - All writing uses NSFileHandle (no repeated read+concat into a single NSData).
 * - Base64 helpers still use NSData, but only on-demand.
 * - Files are stored under NSTemporaryDirectory().
 */

// ---------- Name & path helpers ----------

/**
 * Normalize a model file name:
 * - If it already contains a dot (e.g. ".gguf"), keep it.
 * - Else, assume it's a GGUF model and append ".gguf".
 */
private fun normalizedModelName(fileName: String): String =
    if (fileName.contains('.')) fileName else "$fileName.gguf"

/**
 * Base temp directory for model files.
 * Example (simulator):
 * /Users/.../Containers/Data/Application/<UUID>/tmp/
 */
private fun tempBaseDir(): String {
    val baseDir = NSTemporaryDirectory() ?: "/tmp/"
    return if (baseDir.endsWith("/")) baseDir else "$baseDir/"
}

/**
 * Full path for a given model file name under the temp directory.
 */
private fun tempPathFor(fileName: String): String {
    val base = tempBaseDir()
    val normalized = normalizedModelName(fileName)
    return base + normalized
}

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

private fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

/**
 * Create an empty file at [path] if it does not already exist.
 */
private fun ensureFileExists(path: String) {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) {
        fm.createFileAtPath(path, null, null)
    }
}

// ---------- Low-level write helpers using NSFileHandle ----------

/**
 * Write [data] (NSData) to [path] using NSFileHandle.
 *
 * @param append If true, seek to end of file; otherwise truncate to 0 first.
 */
@OptIn(ExperimentalForeignApi::class)
private fun writeNSDataToFile(path: String, data: NSData, append: Boolean) {
    ensureFileExists(path)

    val handle: NSFileHandle? = NSFileHandle.fileHandleForWritingAtPath(path)
    if (handle == null) {
        println("🔴 [iOS] writeNSDataToFile: cannot open handle for $path")
        return
    }

    try {
        if (!append) {
            // Truncate to 0
            handle.truncateFileAtOffset(0uL)
        } else {
            // Seek to end
            handle.seekToEndOfFile()
        }
        handle.writeData(data)
    } finally {
        handle.closeFile()
    }
}

// ---------- actuals for extension functions ----------

/**
 * Stream download to file using a single NSFileHandle opened once.
 * This is the hot path used when downloading models.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val path = tempPathFor(fileName)
    println("🔵 [iOS] ByteReadChannel.writeToFile → $path")

    val fm = NSFileManager.defaultManager
    // Create an empty file (or overwrite if exists)
    fm.createFileAtPath(path, null, null)

    val handle: NSFileHandle? = NSFileHandle.fileHandleForWritingAtPath(path)
    if (handle == null) {
        println("🔴 [iOS] ByteReadChannel.writeToFile: cannot open handle for $path")
        return
    }

    try {
        // Ensure we start from offset 0
        handle.truncateFileAtOffset(0uL)

        val buffer = ByteArray(256 * 1024)

        while (true) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break

            val chunk = if (read == buffer.size) {
                buffer
            } else {
                buffer.copyOf(read)
            }

            val data = byteArrayToNSData(chunk)
            handle.writeData(data)
        }
    } finally {
        handle.closeFile()
    }

    val exists = fileExists(path)
    val size = NSData.create(contentsOfFile = path)?.length ?: -1
    println("✅ [iOS] Download finished. Exists=$exists, sizeBytes=$size")
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.writeToFile(fileName: String) {
    val path = tempPathFor(fileName)
    println("🔵 [iOS] ByteArray.writeToFile → $path")
    val data = byteArrayToNSData(this)
    writeNSDataToFile(path, data, append = false)
    val exists = fileExists(path)
    val size = NSData.create(contentsOfFile = path)?.length ?: -1
    println("✅ [iOS] ByteArray.writeToFile done. Exists=$exists, sizeBytes=$size")
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val path = tempPathFor(fileName)
    println("🔵 [iOS] ByteArray.addBytesToFile → $path")
    val data = byteArrayToNSData(this)
    writeNSDataToFile(path, data, append = true)
    val exists = fileExists(path)
    val size = NSData.create(contentsOfFile = path)?.length ?: -1
    println("✅ [iOS] ByteArray.addBytesToFile done. Exists=$exists, sizeBytes=$size")
}

// ---------- LlamatikTempFile implementation ----------

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val path: String = tempPathFor(fileName)

    init {
        val exists = fileExists(path)
        println("🟢 [iOS] LlamatikTempFile.init path=$path exists=$exists")
    }

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
        // println("🔵 [iOS] LlamatikTempFile.appendBytes → $path (len=${bytes.size})")
        val data = byteArrayToNSData(bytes)
        writeNSDataToFile(path, data, append = true)
    }

    actual fun readBytes(): ByteArray {
        // println("🔵 [iOS] LlamatikTempFile.readBytes ← $path")
        val data = NSData.create(contentsOfFile = path) ?: return ByteArray(0)
        return nsDataToByteArray(data)
    }

    actual fun getBase64String(): String = base64Encode(readBytes())

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun appendBytesBase64(bytes: ByteArray) {
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

    actual fun absolutePath(): String {
        val exists = fileExists(path)
        println("🔵 [iOS] LlamatikTempFile.absolutePath → $path (exists=$exists)")
        return path
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String): Boolean {
        val fileManager = NSFileManager.defaultManager
        return try {
            if (!fileManager.fileExistsAtPath(path)) {
                // Already gone, treat as success
                true
            } else {
                // removeItemAtPath returns true on success
                fileManager.removeItemAtPath(path, error = null)
            }
        } catch (_: Throwable) {
            false
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    if (savedPath.isBlank()) return savedPath

    val fm = NSFileManager.defaultManager

    // If it doesn't exist, nothing we can do (keep old path so caller can handle it)
    if (!fm.fileExistsAtPath(savedPath)) return savedPath

    val persistentDir = modelsDirIos().path ?: return savedPath

    // Already in persistent models dir
    if (savedPath.startsWith(persistentDir)) return savedPath

    val destPath = stableModelFileIos(modelNameOrFileName).path ?: return savedPath

    // Ensure parent dir exists
    ensureDirExistsIos(modelsDirIos())

    // If destination exists, replace it
    if (fm.fileExistsAtPath(destPath)) {
        runCatching { fm.removeItemAtPath(destPath, null) }
    }

    // Try move first (fast)
    val movedOk = fm.moveItemAtPath(savedPath, destPath, null)
    if (movedOk) {
        // cleanup leftover .part next to final file
        runCatching { fm.removeItemAtPath(destPath + ".part", null) }
        return destPath
    }

    // Fallback: copy + delete
    val copiedOk = fm.copyItemAtPath(savedPath, destPath, null)
    if (copiedOk) {
        runCatching { fm.removeItemAtPath(savedPath, null) }
        runCatching { fm.removeItemAtPath(destPath + ".part", null) }
        return destPath
    }

    return savedPath
}

@OptIn(ExperimentalForeignApi::class)
private fun modelsDirIos(): NSURL {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    ) ?: run {
        // very defensive fallback: use tmp-ish unique dir under Application Support if URLForDirectory fails
        val tmp = NSURL.fileURLWithPath("/tmp").URLByAppendingPathComponent("llamatik", true)!!
        ensureDirExistsIos(tmp)
        return tmp
    }

    // Application Support / Llamatik / models
    val app = base.URLByAppendingPathComponent("Llamatik", true)!!
    val models = app.URLByAppendingPathComponent("models", true)!!
    ensureDirExistsIos(models)
    return models
}

private fun stableModelFileIos(modelNameOrFileName: String): NSURL {
    val safeName = sanitizeFileName(modelNameOrFileName).ifBlank { "model_${NSUUID.UUID().UUIDString}" }
    return modelsDirIos().URLByAppendingPathComponent("$safeName.gguf", false)!!
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureDirExistsIos(dir: NSURL) {
    val fm = NSFileManager.defaultManager
    runCatching {
        fm.createDirectoryAtURL(
            url = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }
}

private fun sanitizeFileName(input: String): String {
    // close enough to your Android sanitize: keep alnum . _ - ; replace others with _
    var out = input
    val invalid = Regex("[^A-Za-z0-9._-]")
    out = out.replace(invalid, "_")
    out = out.take(64)
    return out
}
