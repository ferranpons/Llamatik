@file:OptIn(ExperimentalWasmJsInterop::class)

package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val bytes = readRemaining().readBytes()
    bytes.writeToFile(fileName)
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    // Web/Wasm: no real filesystem. We persist into localStorage.
    putBytes(modelKey(fileName), this)
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    val k = modelKey(fileName)
    val existing = getBytes(k) ?: ByteArray(0)
    val out = ByteArray(existing.size + this.size)
    existing.copyInto(out, destinationOffset = 0)
    this.copyInto(out, destinationOffset = existing.size)
    putBytes(k, out)
}

actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    // No-op on Web/Wasm.
    return savedPath
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {
    private val safe = sanitizeTempPrefix(fileName)
    private val dataKey = "llamatik_tmp:$safe.bin"
    private val base64Key = "llamatik_tmp:$safe.b64"

    actual fun readBytes(): ByteArray = getBytes(dataKey) ?: ByteArray(0)

    actual fun appendBytes(bytes: ByteArray) {
        val existing = getBytes(dataKey) ?: ByteArray(0)
        val out = ByteArray(existing.size + bytes.size)
        existing.copyInto(out, 0)
        bytes.copyInto(out, existing.size)
        putBytes(dataKey, out)
    }

    actual fun getBase64String(): String {
        return encode(readBytes())
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
        // Incoming bytes are base64 TEXT bytes (chunked). We accumulate as string.
        val chunk = bytes.decodeToString()
        val current = localStorageGet(base64Key) ?: ""
        localStorageSet(base64Key, current + chunk)
    }

    actual fun close() {
        // Nothing to close in wasm implementation.
    }

    actual fun readBase64String(): String {
        return localStorageGet(base64Key) ?: ""
    }

    actual fun absolutePath(): String {
        // Logical identifier.
        return dataKey
    }

    actual fun delete(path: String): Boolean {
        return try {
            localStorageRemove(path)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

// ----------------- helpers -----------------

private fun modelKey(fileName: String): String {
    val safe = sanitizeTempPrefix(fileName)
    return "llamatik_models:$safe.gguf"
}

@OptIn(ExperimentalEncodingApi::class)
private fun encode(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decode(base64: String): ByteArray = Base64.decode(base64)

private fun putBytes(key: String, bytes: ByteArray) {
    localStorageSet(key, encode(bytes))
}

private fun getBytes(key: String): ByteArray? {
    val v = localStorageGet(key) ?: return null
    return runCatching { decode(v) }.getOrNull()
}

private fun sanitizeTempPrefix(input: String): String {
    val cleaned = input
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(64)
    return when {
        cleaned.length >= 3 -> cleaned
        cleaned.isBlank() -> "tmp"
        else -> "tmp_$cleaned"
    }
}

@JsFun(
    "(key) => { try { return globalThis.localStorage.getItem(key); } catch(e) { return null; } }"
)
private external fun localStorageGet(key: String): String?

@JsFun(
    "(key, value) => { try { globalThis.localStorage.setItem(key, value); } catch(e) {} }"
)
private external fun localStorageSet(key: String, value: String)

@JsFun(
    "(key) => { try { globalThis.localStorage.removeItem(key); } catch(e) {} }"
)
private external fun localStorageRemove(key: String)
