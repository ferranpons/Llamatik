@file:OptIn(ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.whisper.whisper_stt_free_string
import com.llamatik.library.platform.whisper.whisper_stt_init
import com.llamatik.library.platform.whisper.whisper_stt_release
import com.llamatik.library.platform.whisper.whisper_stt_transcribe_wav
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual object WhisperBridge {

    private var loadedWhisperModelPath: String? = null
    private var whisperInitialized: Boolean = false

    actual fun getModelPath(modelFileName: String): String {
        resolveExistingModelPath(modelFileName)?.let { resolved ->
            NSLog("[WhisperBridge] resolved model path=$resolved")
            return resolved
        }
        return modelFileName
    }

    private fun pathBasename(path: String): String =
        path.substringAfterLast('/')

    private fun pathStem(path: String): String {
        val base = pathBasename(path)
        val dot = base.lastIndexOf('.')
        return if (dot > 0) base.substring(0, dot) else base
    }

    private fun normalizeKey(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    private fun appSupportModelsDir(): String? {
        val fm = NSFileManager.defaultManager()
        val base = fm.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        ) ?: return null

        val appDir = base.URLByAppendingPathComponent("Llamatik", true)
        val modelsDir = appDir?.URLByAppendingPathComponent("models", true)
        return modelsDir?.path
    }

    private fun directoryCandidates(): List<String> {
        val fm = NSFileManager.defaultManager()
        val out = mutableListOf<String>()

        val tmp = NSTemporaryDirectory()
        if (!tmp.isNullOrBlank()) out += tmp.trimEnd('/')

        val caches = (fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).firstOrNull() as? NSURL)?.path
        if (!caches.isNullOrBlank()) out += caches.trimEnd('/')

        val appSupportModels = appSupportModelsDir()
        if (!appSupportModels.isNullOrBlank()) out += appSupportModels.trimEnd('/')

        return out.distinct()
    }

    private fun exactPathCandidates(input: String): List<String> {
        val base = pathBasename(input)
        val stem = pathStem(input)
        val candidates = mutableListOf<String>()

        if (input.startsWith("/")) {
            candidates += input
        }

        directoryCandidates().forEach { dir ->
            candidates += "$dir/$base"
            candidates += "$dir/$stem"
            if (!base.endsWith(".bin", ignoreCase = true)) {
                candidates += "$dir/$base.bin"
            }
            if (!stem.endsWith(".bin", ignoreCase = true)) {
                candidates += "$dir/$stem.bin"
            }
        }

        return candidates.distinct()
    }

    private fun fuzzySearchInDir(dirPath: String, requested: String): String? {
        val fm = NSFileManager.defaultManager()
        val items = (fm.contentsOfDirectoryAtPath(dirPath, null) as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (items.isEmpty()) return null

        val requestedBase = pathBasename(requested)
        val requestedStem = pathStem(requested)
        val requestedKey = normalizeKey(requestedStem)

        items.firstOrNull { it == requestedBase }?.let { return "$dirPath/$it" }
        items.firstOrNull { pathStem(it) == requestedStem }?.let { return "$dirPath/$it" }
        items.firstOrNull { normalizeKey(pathStem(it)) == requestedKey }?.let { return "$dirPath/$it" }
        items.firstOrNull {
            val candidateKey = normalizeKey(pathStem(it))
            candidateKey.contains(requestedKey) || requestedKey.contains(candidateKey)
        }?.let { return "$dirPath/$it" }

        return null
    }

    private fun resolveExistingModelPath(input: String): String? {
        val fm = NSFileManager.defaultManager()
        if (input.isBlank()) return null

        if (fm.fileExistsAtPath(input)) return input

        exactPathCandidates(input).firstOrNull { fm.fileExistsAtPath(it) }?.let { found ->
            NSLog("[WhisperBridge] exact recovered path for '$input' -> $found")
            return found
        }

        directoryCandidates().firstNotNullOfOrNull { dir ->
            fuzzySearchInDir(dir, input)
        }?.let { found ->
            if (fm.fileExistsAtPath(found)) {
                NSLog("[WhisperBridge] fuzzy recovered path for '$input' -> $found")
                return found
            }
        }

        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun initModel(modelPath: String): Boolean {
        val resolvedPath = resolveExistingModelPath(modelPath) ?: getModelPath(modelPath)

        NSLog("[WhisperBridge] initModel requested=$modelPath")
        NSLog("[WhisperBridge] initModel resolved=$resolvedPath")

        if (whisperInitialized && loadedWhisperModelPath == resolvedPath) {
            NSLog("[WhisperBridge] initModel already initialized for path=$resolvedPath")
            return true
        }

        if (whisperInitialized && loadedWhisperModelPath != resolvedPath) {
            NSLog("[WhisperBridge] switching whisper model from $loadedWhisperModelPath to $resolvedPath")
            whisper_stt_release()
            whisperInitialized = false
            loadedWhisperModelPath = null
        }

        val fm = NSFileManager.defaultManager()
        val exists = fm.fileExistsAtPath(resolvedPath)

        if (!exists) {
            NSLog("[WhisperBridge] file does not exist at path=$resolvedPath")
            return false
        }

        val ok = whisper_stt_init(resolvedPath)
        if (ok == 0) {
            NSLog("[WhisperBridge] whisper_stt_init FAILED for path=$resolvedPath")
            return false
        }

        whisperInitialized = true
        loadedWhisperModelPath = resolvedPath

        NSLog("[WhisperBridge] whisper_stt_init OK")
        return true
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun transcribeWav(wavPath: String, language: String?): String {
        val ptr = whisper_stt_transcribe_wav(wavPath, language) ?: return ""

        return try {
            ptr.toKString()
        } finally {
            whisper_stt_free_string(ptr)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun release() {
        whisper_stt_release()
        whisperInitialized = false
        loadedWhisperModelPath = null
    }
}
