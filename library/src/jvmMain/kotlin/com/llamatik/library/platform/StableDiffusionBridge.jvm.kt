package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

actual object StableDiffusionBridge {
    init {
        // Reuse the same JNI shared library that contains llama/whisper/sd exports.
        loadNativeFromResources()
    }

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // Desktop: callers typically pass an absolute path to downloaded model files.
        val p = Path(modelFileName)
        return if (Files.exists(p)) p.toAbsolutePath().toString() else modelFileName
    }

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

    private fun loadNativeFromResources() {
        val os = System.getProperty("os.name").lowercase()
        val platform = when {
            os.contains("mac") -> "macos"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> error("Unsupported OS: $os")
        }

        val libFileName = System.mapLibraryName("llama_jni")
        val resourcePath = "/native/$platform/$libFileName"

        val input = object {}.javaClass.getResourceAsStream(resourcePath)
            ?: error("Native library not found in resources: $resourcePath")

        val out = File.createTempFile("llama_jni_", libFileName)
        out.deleteOnExit()
        input.use { it.copyTo(out.outputStream()) }

        System.load(out.absolutePath)
    }
}
