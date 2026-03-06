package com.llamatik.library.platform

actual object StableDiffusionBridge {
    actual fun getModelPath(modelFileName: String): String = "/models/$modelFileName"
    actual fun initModel(modelPath: String, threads: Int): Boolean = false
    actual fun txt2img(
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long
    ): ByteArray = ByteArray(0)
    actual fun release() {}
}