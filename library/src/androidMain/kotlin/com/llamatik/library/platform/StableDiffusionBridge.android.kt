package com.llamatik.library.platform

actual object StableDiffusionBridge {
    init {
        // Single native shared library for all JNI bridges (llama/whisper/sd).
        System.loadLibrary("llama_jni")
    }

    actual fun getModelPath(modelFileName: String): String = modelFileName

    actual external fun initModel(modelPath: String, threads: Int): Boolean

    actual external fun txt2img(
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
    ): ByteArray

    actual external fun release()
}
