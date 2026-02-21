package com.llamatik.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun appDataRoot(): File {
    val home = System.getProperty("user.home") ?: "."
    return File(home, ".llamatik/app_data").apply { mkdirs() }
}

private fun resolvePath(relativePath: String): File {
    val safe = relativePath.trimStart('/').replace("..", "_")
    val f = File(appDataRoot(), safe)
    f.parentFile?.mkdirs()
    return f
}

actual object AppStorage {
    actual suspend fun writeBytes(relativePath: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val f = resolvePath(relativePath)
            f.writeBytes(bytes)
        }
    }

    actual suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = resolvePath(relativePath)
        if (!f.exists()) null else f.readBytes()
    }

    actual fun exists(relativePath: String): Boolean = resolvePath(relativePath).exists()

    actual fun delete(relativePath: String): Boolean = try {
        val f = resolvePath(relativePath)
        if (f.exists()) f.delete() else true
    } catch (_: Throwable) {
        false
    }
}
