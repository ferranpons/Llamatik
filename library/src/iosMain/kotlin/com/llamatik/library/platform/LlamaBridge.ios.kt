@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.llama.llama_embed
import com.llamatik.library.platform.llama.llama_embed_free
import com.llamatik.library.platform.llama.llama_embed_init
import com.llamatik.library.platform.llama.llama_embedding_size
import com.llamatik.library.platform.llama.llama_free_embedding
import com.llamatik.library.platform.llama.llama_generate
import com.llamatik.library.platform.llama.llama_generate_cancel
import com.llamatik.library.platform.llama.llama_generate_chat
import com.llamatik.library.platform.llama.llama_generate_chat_json_schema
import com.llamatik.library.platform.llama.llama_generate_chat_json_schema_stream
import com.llamatik.library.platform.llama.llama_generate_chat_stream
import com.llamatik.library.platform.llama.llama_generate_continue
import com.llamatik.library.platform.llama.llama_generate_free
import com.llamatik.library.platform.llama.llama_generate_init
import com.llamatik.library.platform.llama.llama_generate_json_schema
import com.llamatik.library.platform.llama.llama_generate_json_schema_stream
import com.llamatik.library.platform.llama.llama_generate_session_load
import com.llamatik.library.platform.llama.llama_generate_session_reset
import com.llamatik.library.platform.llama.llama_generate_session_save
import com.llamatik.library.platform.llama.llama_generate_set_params
import com.llamatik.library.platform.llama.llama_generate_stream
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.posix.free

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual object LlamaBridge {

    private var loadedEmbedModelPath: String? = null
    private var loadedGenerateModelPath: String? = null
    private var embedInitialized: Boolean = false
    private var generateInitialized: Boolean = false

    @OptIn(BetaInteropApi::class)
    actual fun getModelPath(modelFileName: String): String {
        val fm = NSFileManager.defaultManager

        resolveExistingModelPath(modelFileName)?.let { resolved ->
            println("✅ [getModelPath] Using existing resolved path: $resolved")
            return resolved
        }

        val cachesDir = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as NSURL
        val dstUrl = cachesDir.URLByAppendingPathComponent(modelFileName)!!

        println("📂 [getModelPath] Looking for: $modelFileName")
        println("📂 [getModelPath] Destination path: ${dstUrl.path}")
        println("📦 [getModelPath] Main bundle: ${NSBundle.mainBundle.bundlePath}")

        if (!fm.fileExistsAtPath(dstUrl.path!!)) {
            val (name, ext) = splitNameAndExt(modelFileName)

            fun urlFor(bundle: NSBundle, n: String, e: String?, subdir: String?): NSURL? {
                val url = bundle.URLForResource(n, e, subdir)
                if (url != null && fm.fileExistsAtPath(url.path!!)) return url
                return null
            }

            val candidates = mutableListOf<Pair<String, NSURL?>>()

            candidates += "main bundle models/" to urlFor(NSBundle.mainBundle, name, ext, "models")
            candidates += "main bundle <root>" to urlFor(NSBundle.mainBundle, name, ext, null)

            NSBundle.allBundles().forEach { b ->
                (b as? NSBundle)?.let { bundle ->
                    candidates += "${bundle.bundlePath}: models/" to urlFor(bundle, name, ext, "models")
                    candidates += "${bundle.bundlePath}: <root>" to urlFor(bundle, name, ext, null)
                }
            }

            NSBundle.allFrameworks().forEach { b ->
                (b as? NSBundle)?.let { bundle ->
                    candidates += "framework ${bundle.bundlePath}: models/" to urlFor(bundle, name, ext, "models")
                    candidates += "framework ${bundle.bundlePath}: <root>" to urlFor(bundle, name, ext, null)
                }
            }

            var resUrl: NSURL? = null
            for ((label, url) in candidates) {
                println("🔎 [getModelPath] Probe -> $label => $url")
                if (url != null) {
                    resUrl = url
                    println("✅ [getModelPath] Found in $label")
                    break
                }
            }

            requireNotNull(resUrl) {
                """
                Resource "$modelFileName" not found in any bundle.
                Searched: models/ and root of main bundle and all frameworks.
                Main bundle path: ${NSBundle.mainBundle.bundlePath}
                """.trimIndent()
            }

            val data = NSData.create(contentsOfURL = resUrl)
            requireNotNull(data) { "Couldn't read model from bundle url=$resUrl" }
            val ok = data.writeToURL(dstUrl, true)
            require(ok) { "Failed to copy model to caches dir: ${dstUrl.path}" }
            println("📥 [getModelPath] Copied to caches: ${dstUrl.path}")
        } else {
            println("✅ [getModelPath] Already in caches: ${dstUrl.path}")
        }

        return dstUrl.path!!
    }

    private fun splitNameAndExt(fileName: String): Pair<String, String?> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return fileName to null
        return fileName.substring(0, dot) to fileName.substring(dot + 1)
    }

    private fun pathBasename(path: String): String = path.substringAfterLast('/')

    private fun pathStem(path: String): String {
        val base = pathBasename(path)
        val dot = base.lastIndexOf('.')
        return if (dot > 0) base.substring(0, dot) else base
    }

    private fun normalizeKey(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    private fun appSupportModelsDir(): NSURL? {
        val fm = NSFileManager.defaultManager
        val base = fm.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        ) ?: return null

        val appDir = base.URLByAppendingPathComponent("Llamatik", true)
        return appDir?.URLByAppendingPathComponent("models", true)
    }

    private fun directoryCandidates(): List<String> {
        val fm = NSFileManager.defaultManager
        val out = mutableListOf<String>()

        val tmp = NSTemporaryDirectory()
        if (!tmp.isNullOrBlank()) out += tmp.trimEnd('/')

        val caches = (fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).firstOrNull() as? NSURL)?.path
        if (!caches.isNullOrBlank()) out += caches.trimEnd('/')

        val appSupportModels = appSupportModelsDir()?.path
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
            if (!base.endsWith(".gguf", ignoreCase = true)) {
                candidates += "$dir/$base.gguf"
            }
            if (!stem.endsWith(".gguf", ignoreCase = true)) {
                candidates += "$dir/$stem.gguf"
            }
        }

        return candidates.distinct()
    }

    private fun fuzzySearchInDir(dirPath: String, requested: String): String? {
        val fm = NSFileManager.defaultManager
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
        val fm = NSFileManager.defaultManager

        if (input.isBlank()) return null
        if (fm.fileExistsAtPath(input)) return input

        exactPathCandidates(input).firstOrNull { fm.fileExistsAtPath(it) }?.let { found ->
            println("✅ [resolveModelPath] Exact recovered path for '$input' -> $found")
            return found
        }

        directoryCandidates().firstNotNullOfOrNull { dir ->
            fuzzySearchInDir(dir, input)
        }?.let { found ->
            if (fm.fileExistsAtPath(found)) {
                println("✅ [resolveModelPath] Fuzzy recovered path for '$input' -> $found")
                return found
            }
        }

        return null
    }

    actual fun initEmbedModel(modelPath: String): Boolean {
        val resolved = resolveExistingModelPath(modelPath) ?: getModelPath(modelPath)
        println("🧠 [initEmbedModel] requested=$modelPath resolved=$resolved")

        if (embedInitialized && loadedEmbedModelPath == resolved) {
            println("✅ [initEmbedModel] already initialized for $resolved")
            return true
        }

        if (embedInitialized && loadedEmbedModelPath != resolved) {
            println("♻️ [initEmbedModel] switching embed model from $loadedEmbedModelPath to $resolved")
            llama_embed_free()
            embedInitialized = false
            loadedEmbedModelPath = null
        }

        val ok = llama_embed_init(resolved)
        if (ok) {
            embedInitialized = true
            loadedEmbedModelPath = resolved
        }
        return ok
    }

    actual fun embed(input: String): FloatArray {
        val ptr = llama_embed(input) ?: return FloatArray(0)
        val dim = llama_embedding_size()
        val out = FloatArray(dim)
        for (i in 0 until dim) out[i] = ptr[i]
        llama_free_embedding(ptr)
        return out
    }

    actual fun initGenerateModel(modelPath: String): Boolean {
        val resolved = resolveExistingModelPath(modelPath) ?: getModelPath(modelPath)
        println("📝 [initGenerateModel] requested=$modelPath resolved=$resolved")

        if (generateInitialized && loadedGenerateModelPath == resolved) {
            println("✅ [initGenerateModel] already initialized for $resolved")
            return true
        }

        if (generateInitialized && loadedGenerateModelPath != resolved) {
            println("♻️ [initGenerateModel] switching generate model from $loadedGenerateModelPath to $resolved")
            llama_generate_free()
            generateInitialized = false
            loadedGenerateModelPath = null
        }

        val ok = llama_generate_init(resolved)
        if (ok) {
            generateInitialized = true
            loadedGenerateModelPath = resolved
        }
        return ok
    }

    actual fun generate(prompt: String): String {
        val c = llama_generate(prompt) ?: return ""
        try {
            return c.toKString()
        } finally {
            free(c)
        }
    }

    actual fun generateContinue(prompt: String): String {
        val c = llama_generate_continue(prompt) ?: return ""
        try {
            return c.toKString()
        } finally {
            free(c)
        }
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        val c = llama_generate_chat(systemPrompt, contextBlock, userPrompt) ?: return ""
        try {
            return c.toKString()
        } finally {
            free(c)
        }
    }

    actual fun generateJson(prompt: String, jsonSchema: String?): String {
        val c = llama_generate_json_schema(prompt, jsonSchema) ?: return ""
        try {
            return c.toKString()
        } finally {
            free(c)
        }
    }

    actual fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?
    ): String {
        val c = llama_generate_chat_json_schema(systemPrompt, contextBlock, userPrompt, jsonSchema) ?: return ""
        try {
            return c.toKString()
        } finally {
            free(c)
        }
    }

    actual fun sessionReset(): Boolean = llama_generate_session_reset()
    actual fun sessionSave(path: String): Boolean = llama_generate_session_save(path)
    actual fun sessionLoad(path: String): Boolean = llama_generate_session_load(path)

    actual fun shutdown() {
        llama_embed_free()
        llama_generate_free()
        embedInitialized = false
        generateInitialized = false
        loadedEmbedModelPath = null
        loadedGenerateModelPath = null
    }

    actual fun generateStream(prompt: String, callback: GenStream) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_stream(prompt, onDelta, onDone, onError, ref.asCPointer())
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_chat_stream(
                    systemPrompt,
                    contextBlock,
                    userPrompt,
                    onDelta,
                    onDone,
                    onError,
                    ref.asCPointer()
                )
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_json_schema_stream(prompt, jsonSchema, onDelta, onDone, onError, ref.asCPointer())
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_chat_json_schema_stream(
                    systemPrompt,
                    contextBlock,
                    userPrompt,
                    jsonSchema,
                    onDelta,
                    onDone,
                    onError,
                    ref.asCPointer()
                )
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val proxy = object : GenStream {
            override fun onDelta(text: String) = onDelta(text)
            override fun onComplete() = onDone()
            override fun onError(message: String) = onError(message)
        }
        generateStreamWithContext(system, context, user, proxy)
    }

    actual fun nativeCancelGenerate() {
        llama_generate_cancel()
    }

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    ) {
        llama_generate_set_params(
            temperature,
            maxTokens,
            topP,
            topK,
            repeatPenalty
        )
    }
}
