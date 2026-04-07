package com.llamatik.library.platform

actual object MultimodalBridge {
    actual fun initModel(modelPath: String, mmprojPath: String): Boolean = false

    actual fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream) {
        callback.onError("Multimodal inference is not supported on WASM")
    }

    actual fun cancelAnalysis() {}

    actual fun release() {}
}
