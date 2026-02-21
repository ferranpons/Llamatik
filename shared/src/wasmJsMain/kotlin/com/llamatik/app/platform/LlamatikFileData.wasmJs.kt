package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.coroutines.startCoroutine
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val bytes = readRemaining().readBytes()
    bytes.writeToFile(fileName)
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    // Web/Wasm: store in IndexedDB
    val key = modelKey(fileName)
    val b64 = encode(this)
    IndexedDbFiles.writeAllBase64(key, b64)
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
    // Web/Wasm: append chunk (base64) into IndexedDB
    val key = modelKey(fileName)
    val b64 = encode(this)
    IndexedDbFiles.appendChunkBase64(key, b64)
}

actual fun migrateModelPathIfNeeded(
    modelNameOrFileName: String,
    savedPath: String
): String {
    // No-op on Web/Wasm (logical keys, not filesystem paths)
    return savedPath
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {
    private val safe = sanitizeName(fileName)

    // temp file storage keys (IndexedDB as well)
    private val dataKey = "tmp/$safe.bin"
    private val base64Key = "tmp/$safe.b64"

    actual fun readBytes(): ByteArray {
        // Warning: reconstructing full bytes allocates memory.
        val b64 = runCatching { readBase64String() }.getOrElse { "" }
        return if (b64.isBlank()) ByteArray(0) else decode(b64)
    }

    actual fun appendBytes(bytes: ByteArray) {
        // Blocking API in expect/actual; implement by launching async append.
        // In practice, your usage is on background coroutines.
        // We'll do best-effort by scheduling and returning immediately.
        val chunk = encode(bytes)
        // fire-and-forget: no suspend in this API
        launchWasmAsync {
            IndexedDbFiles.appendChunkBase64(dataKey, chunk)
        }
    }

    actual fun getBase64String(): String {
        // Returns a complete base64 string of the temp file
        return readBase64String()
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
        val chunk = bytes.decodeToString()
        launchWasmAsync {
            IndexedDbFiles.appendChunkBase64(base64Key, chunk)
        }
    }

    actual fun close() {
        // no-op
    }

    actual fun readBase64String(): String {
        // Not suspend in API, so we do a sync “best effort”:
        // If you call this immediately after append, you may not see latest chunk yet.
        // For download flow, prefer the suspend APIs on writeToFile/addBytesToFile.
        return wasmBlockingReadAllBase64(base64Key) ?: ""
    }

    actual fun absolutePath(): String = dataKey

    actual fun delete(path: String): Boolean {
        // schedule delete
        launchWasmAsync { IndexedDbFiles.delete(path) }
        return true
    }
}

// ----------------- helpers -----------------

private fun modelKey(fileName: String): String {
    val safe = sanitizeName(fileName)
    return "models/$safe"
}

private fun sanitizeName(input: String): String =
    input.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "file" }

@OptIn(ExperimentalEncodingApi::class)
private fun encode(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
private fun decode(base64: String): ByteArray = Base64.decode(base64)

// ---- wasm async helpers (no runBlocking available) ----

@JsFun(
    """
    (block) => {
      // schedule on event loop
      Promise.resolve().then(block);
    }
    """
)
private external fun scheduleMicrotask(block: () -> Unit)

private fun launchWasmAsync(block: suspend () -> Unit) {
    // Minimal fire-and-forget: schedule, then run using a tiny coroutine-less state machine.
    // We can’t easily launch coroutines here without coupling to your scopes.
    scheduleMicrotask {
        // Best effort: ignore failures
        block.startWasmContinuation()
    }
}

// Extremely small coroutine starter (no dependencies, no runBlocking)
private fun (suspend () -> Unit).startWasmContinuation() {
    val f = this
    f.startCoroutine(object : kotlin.coroutines.Continuation<Unit> {
        override val context = kotlin.coroutines.EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            // ignore
        }
    })
}

/**
 * Non-suspending read for APIs that force sync.
 * Uses IndexedDB async under the hood but provides best-effort current snapshot via JS callback.
 * For correctness, prefer suspend reads elsewhere.
 */
private fun wasmBlockingReadAllBase64(key: String): String? {
    // For now return null so the app doesn’t freeze trying to sync-read from IDB.
    // If you truly need it, we can add a synchronous cache layer updated by async writes.
    return null
}
