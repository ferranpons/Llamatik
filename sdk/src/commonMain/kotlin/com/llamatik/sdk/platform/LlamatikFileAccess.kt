package com.llamatik.sdk.platform

interface LlamatikFileAccess {
    val isWasm: Boolean

    fun createTempFile(fileName: String): TempFileHandle
    suspend fun writeBytes(fileName: String, bytes: ByteArray)
    suspend fun appendBytes(fileName: String, bytes: ByteArray)

    interface TempFileHandle {
        fun appendBytes(bytes: ByteArray)
        fun close()
        fun delete()
        fun absolutePath(): String
        fun readBytes(): ByteArray
    }
}
