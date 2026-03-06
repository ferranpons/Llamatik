package com.llamatik.library.platform

import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

actual object WhisperBridge {
    init {
        // Your project already loads native for LlamaBridge on JVM via System.load(...)
        // If you already have a loader util, reuse it. Otherwise:
        loadNativeFromResources()
    }

    actual fun getModelPath(modelFileName: String): String {
        // Desktop: expect models to exist on disk (downloaded).
        // Return as-is if caller passes an absolute path; otherwise resolve from working dir.
        val p = Path(modelFileName)
        return if (Files.exists(p)) p.toAbsolutePath().toString() else modelFileName
    }

    actual external fun initModel(modelPath: String): Boolean
    actual external fun transcribeWav(wavPath: String, language: String?): String
    actual external fun release()

    private fun loadNativeFromResources() {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val platform = when {
            os.contains("mac") -> "macos"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> error("Unsupported OS: $os")
        }

        // If ever ship separate builds, split by arch too
        // val archFolder = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x64"

        val libFileName = System.mapLibraryName("llama_jni") // mac -> libllama_jni.dylib
        val resourcePath = "/native/$platform/$libFileName"

        val input = object {}.javaClass.getResourceAsStream(resourcePath)
            ?: error("Native library not found in resources: $resourcePath")

        val out = File.createTempFile("llama_jni_", libFileName)
        out.deleteOnExit()

        input.use { it.copyTo(out.outputStream()) }
        System.load(out.absolutePath)
    }
}