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

// ------------------------------
// Paths
// ------------------------------

@OptIn(ExperimentalForeignApi::class)
private fun modelsDirIos(): NSURL {
    val fm = NSFileManager.defaultManager
    val base = fm.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    ) ?: error("Cannot resolve Application Support directory")

    val app = base.URLByAppendingPathComponent("Llamatik", true)!!
    val models = app.URLByAppendingPathComponent("models", true)!!
    ensureDirExistsIos(models)
    return models
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureDirExistsIos(dir: NSURL) {
    val fm = NSFileManager.defaultManager
    fm.createDirectoryAtURL(
        url = dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
}

private fun sanitizeFileName(input: String): String {
    val invalid = Regex("[^A-Za-z0-9._-]")
    return input.replace(invalid, "_").take(128).ifBlank { "model_${NSUUID.UUID().UUIDString}" }
}

/** Preserve extension exactly as provided. */
private fun finalModelPath(fileName: String): String {
    val safe = sanitizeFileName(fileName)
    return modelsDirIos().URLByAppendingPathComponent(safe, false)!!.path!!
}

private fun partModelPath(fileName: String): String =
    finalModelPath(fileName) + ".part"

// ------------------------------
// NSData helpers
// ------------------------------

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

private fun ensureFileExists(path: String) {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) {
        fm.createFileAtPath(path, null, null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeNSDataToFile(path: String, data: NSData, append: Boolean) {
    ensureFileExists(path)

    val handle = NSFileHandle.fileHandleForWritingAtPath(path)
    if (handle == null) {
        println("🔴 [iOS] writeNSDataToFile: cannot open handle for $path")
        return
    }

    try {
        if (!append) {
            handle.truncateFileAtOffset(0uL)
        } else {
            handle.seekToEndOfFile()
        }
        handle.writeData(data)
    } finally {
        handle.closeFile()
    }
}

// ------------------------------
// actuals
// ------------------------------

/**
 * Stream download to a persistent .part file and then atomically rename to final.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val part = partModelPath(fileName)
    val final = finalModelPath(fileName)

    println("🔵 [iOS] ByteReadChannel.writeToFile → part=$part")

    val fm = NSFileManager.defaultManager

    // remove old part
    if (fm.fileExistsAtPath(part)) runCatching { fm.removeItemAtPath(part, null) }
    // create empty part
    fm.createFileAtPath(part, null, null)

    val handle = NSFileHandle.fileHandleForWritingAtPath(part)
    if (handle == null) {
        println("🔴 [iOS] writeToFile: cannot open handle for $part")
        return
    }

    try {
        handle.truncateFileAtOffset(0uL)

        val buffer = ByteArray(256 * 1024)

        while (true) {
            val read = readAvailable(buffer, 0, buffer.size)
            if (read <= 0) break

            val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
            handle.writeData(byteArrayToNSData(chunk))
        }
    } finally {
        handle.closeFile()
    }

    // finalize: remove old final and rename part -> final (atomic on same volume)
    if (fm.fileExistsAtPath(final)) runCatching { fm.removeItemAtPath(final, null) }

    val moved = fm.moveItemAtPath(part, final, null)
    if (!moved) {
        println("🔴 [iOS] finalize failed: could not rename $part -> $final")
        return
    }

    val size = NSData.create(contentsOfFile = final)?.length ?: -1
    println("✅ [iOS] Download finalized: $final sizeBytes=$size")
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.writeToFile(fileName: String) {
    val part = partModelPath(fileName)
    val final = finalModelPath(fileName)

    println("🔵 [iOS] ByteArray.writeToFile → part=$part")
    writeNSDataToFile(part, byteArrayToNSData(this), append = false)

    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(final)) runCatching { fm.removeItemAtPath(final, null) }

    val moved = fm.moveItemAtPath(part, final, null)
    if (!moved) {
        println("🔴 [iOS] finalize failed: could not rename $part -> $final")
        return
    }

    val size = NSData.create(contentsOfFile = final)?.length ?: -1
    println("✅ [iOS] ByteArray.writeToFile finalized: $final sizeBytes=$size")
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val part = partModelPath(fileName)
    println("🔵 [iOS] ByteArray.addBytesToFile → $part")
    writeNSDataToFile(part, byteArrayToNSData(this), append = true)
}

// ------------------------------
// LlamatikTempFile
// ------------------------------

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamatikTempFile actual constructor(fileName: String) {

    private val finalPath: String = finalModelPath(fileName)
    private val partPath: String = partModelPath(fileName)

    init {
        println("🟢 [iOS] LlamatikTempFile.init final=$finalPath exists=${fileExists(finalPath)} part=$partPath exists=${fileExists(partPath)}")
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
        val data = byteArrayToNSData(bytes)
        writeNSDataToFile(partPath, data, append = true)
    }

    actual fun readBytes(): ByteArray {
        val path = if (fileExists(finalPath)) finalPath else partPath
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
        if (decoded.isNotEmpty()) appendBytes(decoded)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun close() {
        // finalize part -> final if present
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(partPath)) {
            if (fm.fileExistsAtPath(finalPath)) runCatching { fm.removeItemAtPath(finalPath, null) }
            fm.moveItemAtPath(partPath, finalPath, null)
        }
    }

    actual fun readBase64String(): String = getBase64String()

    actual fun absolutePath(): String {
        val path = if (fileExists(finalPath)) finalPath else partPath
        println("🔵 [iOS] LlamatikTempFile.absolutePath → $path (exists=${fileExists(path)})")
        return path
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String): Boolean {
        val fm = NSFileManager.defaultManager
        return try {
            if (fm.fileExistsAtPath(path)) fm.removeItemAtPath(path, null) else true
        } catch (_: Throwable) {
            false
        }
    }
}

// ------------------------------
// Migration (keep extension)
// ------------------------------

@OptIn(ExperimentalForeignApi::class)
actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    if (savedPath.isBlank()) return savedPath

    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(savedPath)) return savedPath

    val persistentDir = modelsDirIos().path ?: return savedPath
    if (savedPath.startsWith(persistentDir)) return savedPath

    val destPath = finalModelPath(modelNameOrFileName)

    // Ensure parent dir exists (already)
    if (fm.fileExistsAtPath(destPath)) runCatching { fm.removeItemAtPath(destPath, null) }

    val movedOk = fm.moveItemAtPath(savedPath, destPath, null)
    if (movedOk) {
        runCatching { fm.removeItemAtPath("$destPath.part", null) }
        return destPath
    }

    val copiedOk = fm.copyItemAtPath(savedPath, destPath, null)
    if (copiedOk) {
        runCatching { fm.removeItemAtPath(savedPath, null) }
        runCatching { fm.removeItemAtPath("$destPath.part", null) }
        return destPath
    }

    return savedPath
}
