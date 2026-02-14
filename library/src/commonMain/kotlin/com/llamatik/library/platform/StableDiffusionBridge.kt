package com.llamatik.library.platform

import androidx.compose.runtime.Composable

/**
 * Minimal Stable Diffusion bridge backed by leejet/stable-diffusion.cpp.
 *
 * Mirrors the existing LlamaBridge / WhisperBridge pattern:
 * - Android/JVM: JNI via libllama_jni
 * - iOS: Kotlin/Native cinterop
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object StableDiffusionBridge {
    @Composable
    fun getModelPath(modelFileName: String): String

    /**
     * Initializes a Stable Diffusion context using a single model file.
     *
     * @param modelPath Absolute path to a .safetensors/.ckpt/.gguf model.
     * @param threads Number of CPU threads to use. Use -1 for default.
     */
    fun initModel(modelPath: String, threads: Int = -1): Boolean

    /**
     * Generates a single RGBA image (width * height * 4 bytes).
     *
     * @return RGBA byte array or an empty array on failure.
     */
    fun txt2img(
        prompt: String,
        negativePrompt: String? = null,
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Float = 7.0f,
        seed: Long = -1L,
    ): ByteArray

    fun release()
}
