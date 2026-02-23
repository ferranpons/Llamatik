package com.llamatik.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Kotlin/Wasm constraints:
 * - Can't use runBlocking (not supported)
 * - org.jetbrains.compose.resources.readResourceBytes(...) is suspend
 * - JS interop cannot return ByteArray
 *
 * So for now:
 * - Fonts: use a safe fallback (Default font family) so UI compiles/runs.
 * - readResourceFile: return empty string (non-crashing placeholder).
 *
 * Next step (when you want real resource loading):
 * - add JS glue that returns String (base64) and decode in Kotlin
 * - or change your common API to suspend and load resources asynchronously
 */
@Composable
actual fun font(
    name: String,
    res: String,
    weight: FontWeight,
    style: FontStyle
): Font {
    // Fallback: use platform default font.
    // We still need to return a Font instance; we can create one with empty data
    // but that may crash. The safest is to rely on FontFamily.Default usage in your typography.
    //
    // If your code expects an actual Font object, return a minimal embedded fallback:
    // identity must be stable; data must be non-empty for some runtimes, so we avoid it.
    //
    // NOTE: If you do not strictly need this function on wasm, it can return a "dummy"
    // font identity via getData, but it will still fail if used to render.
    return Font(
        identity = "wasm-fallback-$res",
        getData = { ByteArray(0) },
        weight = weight,
        style = style
    )
}

actual fun readResourceFile(fileName: String): String {
    // Placeholder for wasm until you wire async resource loading.
    return ""
}