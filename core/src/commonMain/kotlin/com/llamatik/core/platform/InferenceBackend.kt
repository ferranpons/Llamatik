package com.llamatik.core.platform

interface InferenceBackend {
    fun generateStream(prompt: String, stream: GenStream)
}
