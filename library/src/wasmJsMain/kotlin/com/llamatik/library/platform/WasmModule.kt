package com.llamatik.library.platform

/**
 * Kotlin/Wasm JS interop does not support Kotlin/JS-style:
 * - dynamic
 * - @JsNonModule
 * - arbitrary js("...") usage
 *
 * We'll wire proper JS glue later. For now this module is a placeholder.
 */
internal object WasmModule