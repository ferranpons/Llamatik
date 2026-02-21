@file:OptIn(ExperimentalWasmJsInterop::class)

package com.llamatik.app.platform

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual object AppStorage {

    private const val KEY_PREFIX = "llamatik_app_data:"

    private fun key(relativePath: String): String {
        val safe = relativePath.trimStart('/').replace("..", "_")
        return KEY_PREFIX + safe
    }

    actual suspend fun writeBytes(relativePath: String, bytes: ByteArray) {
        // NOTE: localStorage is synchronous; suspend signature is for API symmetry.
        val encoded = encode(bytes)
        localStorageSet(key(relativePath), encoded)
    }

    actual suspend fun readBytes(relativePath: String): ByteArray? {
        val v = localStorageGet(key(relativePath)) ?: return null
        return decode(v)
    }

    actual fun exists(relativePath: String): Boolean {
        return localStorageGet(key(relativePath)) != null
    }

    actual fun delete(relativePath: String): Boolean {
        return try {
            localStorageRemove(key(relativePath))
            true
        } catch (_: Throwable) {
            false
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encode(bytes: ByteArray): String = Base64.encode(bytes)

    @OptIn(ExperimentalEncodingApi::class)
    private fun decode(base64: String): ByteArray = Base64.decode(base64)
}

// Kotlin/Wasm-safe JS interop (no `dynamic`, no `@JsNonModule`)
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
