package com.llamatik.sdk.assistant

interface RagStorage {
    suspend fun write(relativePath: String, bytes: ByteArray)
    suspend fun read(relativePath: String): ByteArray?
    fun delete(relativePath: String): Boolean
}
