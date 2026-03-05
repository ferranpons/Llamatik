@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDataReadingMappedIfSafe
import platform.Foundation.NSDataReadingOptions
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

private fun appDataRootUrl(): NSURL {
    val fm = NSFileManager.defaultManager
    val urls = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
    val documents = urls.lastOrNull() as NSURL
    val root = documents.URLByAppendingPathComponent("llamatik_app_data", true)!!
    fm.createDirectoryAtURL(root, withIntermediateDirectories = true, attributes = null, error = null)
    return root
}

private fun resolveUrl(relativePath: String): NSURL {
    val safe = relativePath.trimStart('/').replace("..", "_")
    val parts = safe.split('/').filter { it.isNotBlank() }
    var current = appDataRootUrl()
    val fm = NSFileManager.defaultManager
    for (i in 0 until parts.size - 1) {
        current = current.URLByAppendingPathComponent(parts[i], true)!!
        fm.createDirectoryAtURL(current, withIntermediateDirectories = true, attributes = null, error = null)
    }
    return current.URLByAppendingPathComponent(parts.lastOrNull() ?: "data.bin", false)!!
}

actual object AppStorage {

    actual suspend fun writeBytes(relativePath: String, bytes: ByteArray) {
        withContext(Dispatchers.Default) {
            val url = resolveUrl(relativePath)

            bytes.usePinned { pinned ->
                val data = NSData.dataWithBytes(
                    bytes = pinned.addressOf(0),
                    length = bytes.size.toULong()
                )
                data.writeToURL(url, atomically = true)
            }
        }
    }

    actual suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.Default) {
        val url = resolveUrl(relativePath)
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(url.path!!)) return@withContext null

        val options: NSDataReadingOptions = NSDataReadingMappedIfSafe
        val data = NSData.dataWithContentsOfURL(url, options = options, error = null) ?: return@withContext null

        val size = data.length.toInt()
        if (size <= 0) return@withContext ByteArray(0)

        val out = ByteArray(size)
        out.usePinned { outPinned ->
            memcpy(outPinned.addressOf(0), data.bytes, data.length)
        }
        out
    }

    actual fun exists(relativePath: String): Boolean {
        val url = resolveUrl(relativePath)
        return NSFileManager.defaultManager.fileExistsAtPath(url.path!!)
    }

    actual fun delete(relativePath: String): Boolean = try {
        val url = resolveUrl(relativePath)
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(url.path!!)) true else fm.removeItemAtURL(url, error = null)
    } catch (_: Throwable) {
        false
    }
}
