package com.llamatik.app.platform

import com.llamatik.sdk.platform.LlamatikFileAccess

class LlamatikFileAccessAdapter : LlamatikFileAccess {

    override val isWasm: Boolean get() = PlatformInfo.isWasm

    override fun createTempFile(fileName: String): LlamatikFileAccess.TempFileHandle {
        val tempFile = LlamatikTempFile(fileName)
        return TempFileHandleAdapter(tempFile)
    }

    override suspend fun writeBytes(fileName: String, bytes: ByteArray) {
        bytes.writeToFile(fileName)
    }

    override suspend fun appendBytes(fileName: String, bytes: ByteArray) {
        bytes.addBytesToFile(fileName)
    }

    private class TempFileHandleAdapter(
        private val delegate: LlamatikTempFile,
    ) : LlamatikFileAccess.TempFileHandle {
        override fun appendBytes(bytes: ByteArray) = delegate.appendBytes(bytes)
        override fun close() = delegate.close()
        override fun delete() { delegate.delete(delegate.absolutePath()) }
        override fun absolutePath(): String = delegate.absolutePath()
        override fun readBytes(): ByteArray = delegate.readBytes()
    }
}
