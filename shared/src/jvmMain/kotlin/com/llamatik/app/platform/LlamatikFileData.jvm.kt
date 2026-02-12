package com.llamatik.app.platform

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
