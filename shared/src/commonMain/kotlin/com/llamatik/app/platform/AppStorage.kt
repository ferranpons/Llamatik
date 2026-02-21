package com.llamatik.app.platform

/**
 * Small cross-platform persistent file helper for app-owned data.
 *
 * This is intentionally separate from model storage (which lives under /models).
 * We use it to persist on-device RAG artifacts (vector stores) across restarts.
 */
expect object AppStorage {
    /** Write bytes to an app-private persistent location under [relativePath]. */
    suspend fun writeBytes(relativePath: String, bytes: ByteArray)

    /** Read bytes previously written under [relativePath], or null if not found. */
    suspend fun readBytes(relativePath: String): ByteArray?

    /** Whether the file exists. */
    fun exists(relativePath: String): Boolean

    /** Delete the file if it exists. */
    fun delete(relativePath: String): Boolean
}
