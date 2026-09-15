package com.llamatik.sdk.framework

interface MemoryStorage {
    suspend fun write(key: String, bytes: ByteArray)
    suspend fun read(key: String): ByteArray?
    suspend fun delete(key: String)
}
