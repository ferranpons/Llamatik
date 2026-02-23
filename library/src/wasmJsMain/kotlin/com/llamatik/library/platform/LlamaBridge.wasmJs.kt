@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual object LlamaBridge {

    private val moduleReady = AtomicBoolean(false)
    private val modelReady = AtomicBoolean(false)
    private val initInFlight = AtomicBoolean(false)
    private val wasmScope = CoroutineScope(Dispatchers.Default)

    @Composable
    actual fun getModelPath(modelFileName: String): String = modelFileName

    actual fun initEmbedModel(modelPath: String): Boolean = false
    actual fun embed(input: String): FloatArray = floatArrayOf()

    actual fun initGenerateModel(modelPath: String): Boolean {
        if (modelReady.load()) return true
        if (initInFlight.load()) return true

        initInFlight.store(true)

        val fileName = sanitizeName(modelPath.substringAfterLast('/'))
        val idbKey = "models/$fileName"
        val fsPath = "/models/$fileName"

        ensureWasmModuleAndModel(
            idbKey = idbKey,
            fsPath = fsPath,
            onOk = {
                moduleReady.store(true)
                modelReady.store(true)
                initInFlight.store(false)
            },
            onErr = { err ->
                initInFlight.store(false)
                modelReady.store(false)
                moduleReady.store(false)
                println("WASM initGenerateModel failed: $err")
            }
        )

        return true
    }

    actual fun generate(prompt: String): String {
        if (!modelReady.load()) return "Web/WASM: model is still loading…"
        return runGenerate(prompt)
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String =
        generate("$systemPrompt\n\n$contextBlock\n\n$userPrompt")

    actual fun generateJson(prompt: String, jsonSchema: String?): String = generate(prompt)

    actual fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?
    ): String = generateWithContext(systemPrompt, contextBlock, userPrompt)

    actual fun generateStream(prompt: String, callback: GenStream) {
        if (!modelReady.load()) {
            callback.onError("Web/WASM: model is still loading…")
            return
        }

        wasmScope.launch {
            try {
                val full = runGenerate(prompt)

                val chunkSize = 24
                var i = 0
                while (i < full.length) {
                    val end = (i + chunkSize).coerceAtMost(full.length)
                    callback.onDelta(full.substring(i, end))
                    i = end
                    delay(0)
                }
                callback.onComplete()
            } catch (t: Throwable) {
                callback.onError("Web/WASM: generate failed: ${t.message ?: t.toString()}")
            }
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) = generateStream("$systemPrompt\n\n$contextBlock\n\n$userPrompt", callback)

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) =
        generateStream(prompt, callback)

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) = generateStreamWithContext(systemPrompt, contextBlock, userPrompt, callback)

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!modelReady.load()) {
            onError("Web/WASM: model is still loading…")
            return
        }

        wasmScope.launch {
            try {
                val full = runGenerate("$system\n\n$context\n\n$user")

                val chunkSize = 24
                var i = 0
                while (i < full.length) {
                    val end = (i + chunkSize).coerceAtMost(full.length)
                    onDelta(full.substring(i, end))
                    i = end
                    delay(0)
                }
                onDone()
            } catch (t: Throwable) {
                onError("Web/WASM: generate failed: ${t.message ?: t.toString()}")
            }
        }
    }

    actual fun shutdown() {}
    actual fun nativeCancelGenerate() {}

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ) {}

    private fun sanitizeName(input: String): String =
        input.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "model.gguf" }
}

/**
 * Loads the Emscripten module, reads *chunked* model from IndexedDB, writes to FS incrementally,
 * and calls llamatik_llama_init_generate(fsPath).
 */
@JsFun(
    """
    (idbKey, fsPath, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_CHUNKS = "chunks";
      const STORE_META = "meta";

      const WASM_MJS_URL  = "/kotlin/llamatik_wasm/llamatik_wasm.mjs";
      const WASM_BASE_URL = "/kotlin/llamatik_wasm/";

      function openDb(cb) {
        const req = indexedDB.open(DB_NAME, DB_VER);
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains(STORE_CHUNKS)) db.createObjectStore(STORE_CHUNKS);
          if (!db.objectStoreNames.contains(STORE_META)) db.createObjectStore(STORE_META);
        };
        req.onsuccess = () => cb(null, req.result);
        req.onerror = () => cb(String(req.error || "open error"), null);
      }

      function readChunkCount(db, key, cb) {
        const tx = db.transaction(STORE_META, "readonly");
        const meta = tx.objectStore(STORE_META);
        const r = meta.get(key);
        r.onsuccess = () => {
          const countStr = r.result;
          const count = (countStr == null) ? 0 : parseInt(countStr, 10);
          cb(null, count);
        };
        r.onerror = () => cb(String(r.error || "meta get error"), 0);
      }

      function readChunk(db, key, i, cb) {
        const tx = db.transaction(STORE_CHUNKS, "readonly");
        const chunks = tx.objectStore(STORE_CHUNKS);
        const r = chunks.get(key + "#" + i);
        r.onsuccess = () => cb(null, r.result);
        r.onerror = () => cb(String(r.error || "chunk read error"), null);
      }

      function ensureDir(Module, path) {
        const parts = path.split("/").filter(Boolean);
        let cur = "";
        for (let i = 0; i < parts.length - 1; i++) {
          cur += "/" + parts[i];
          try { Module.FS.mkdir(cur); } catch(e) {}
        }
      }

      function chunkToU8(chunk) {
        if (chunk instanceof Uint8Array) return chunk;
        if (chunk instanceof ArrayBuffer) return new Uint8Array(chunk);

        if (typeof chunk !== "string") {
          try { return new Uint8Array(chunk); }
          catch (e) { throw new Error("Unsupported chunk type: " + (typeof chunk)); }
        }

        try {
          const bin = atob(chunk);
          const len = bin.length;
          const u8 = new Uint8Array(len);
          for (let i = 0; i < len; i++) u8[i] = bin.charCodeAt(i) & 255;
          return u8;
        } catch (e) {
          console.warn("atob failed — treating chunk as raw binary string");
          const len = chunk.length;
          const u8 = new Uint8Array(len);
          for (let i = 0; i < len; i++) u8[i] = chunk.charCodeAt(i) & 255;
          return u8;
        }
      }

      async function loadModule() {
        if (globalThis.__llamatikModule) return globalThis.__llamatikModule;

        const mod = await import(/* webpackIgnore: true */ WASM_MJS_URL);
        const factory = mod.default || mod;

        const instance = await factory({
          locateFile: (p) => WASM_BASE_URL + p
        });

        globalThis.__llamatikModule = instance;
        return instance;
      }

      function ccallSafe(Module, name, returnType, argTypes, args) {
        // Try normal ccall first.
        try {
          return Module.ccall(name, returnType, argTypes, args);
        } catch (e) {
          const msg = String(e || "");
          // MEMORY64 builds may require bigint return types for pointers.
          if (msg.includes("BigInt") || msg.includes("bigint") || msg.includes("Cannot mix BigInt")) {
            // Retry: if caller asked for "number", try "bigint"
            if (returnType === "number") {
              return Module.ccall(name, "bigint", argTypes, args);
            }
          }
          throw e;
        }
      }

      function toNumberPtr(p) {
        // If we ever get a BigInt pointer, convert safely.
        // In wasm32 builds this is already a number.
        if (typeof p === "bigint") return Number(p);
        return p;
      }

      (async () => {
        try {
          const Module = await loadModule();

          if (!Module.FS || !Module.FS.open || !Module.FS.write) {
            onErr(
              "Emscripten FS is not available on Module. " +
              "Rebuild wasm with -sFORCE_FILESYSTEM=1 and export FS in EXPORTED_RUNTIME_METHODS."
            );
            return;
          }

          openDb((e, db) => {
            if (e) { onErr(e); return; }

            readChunkCount(db, idbKey, (eCount, count) => {
              if (eCount) { onErr(eCount); return; }
              if (!count || count <= 0) { onErr("Model not found in IndexedDB for key: " + idbKey); return; }

              ensureDir(Module, fsPath);

              let stream;
              try {
                stream = Module.FS.open(fsPath, "w+");
              } catch (e0) {
                onErr("FS.open failed: " + String(e0));
                return;
              }

              let idx = 0;
              let offset = 0;

              function next() {
                if (idx >= count) {
                  try { Module.FS.close(stream); } catch(eClose) {}

                  try {
                    const ok = ccallSafe(Module, "llamatik_llama_init_generate", "number", ["string"], [fsPath]);
                    if (ok === 1) onOk();
                    else onErr("llamatik_llama_init_generate returned " + ok);
                  } catch (eInit) {
                    onErr("Init call failed: " + String(eInit));
                  }
                  return;
                }

                readChunk(db, idbKey, idx, (eChunk, chunkVal) => {
                  if (eChunk) {
                    try { Module.FS.close(stream); } catch(eClose) {}
                    onErr(eChunk);
                    return;
                  }

                  try {
                    const u8 = chunkToU8(chunkVal);
                    Module.FS.write(stream, u8, 0, u8.length, offset);
                    offset += u8.length;
                    idx++;
                    next();
                  } catch (eWrite) {
                    try { Module.FS.close(stream); } catch(eClose) {}
                    onErr("Chunk decode/write failed at #" + idx + ": " + String(eWrite));
                  }
                });
              }

              next();
            });
          });
        } catch (e) {
          onErr(String(e));
        }
      })();
    }
    """
)
private external fun ensureWasmModuleAndModel(
    idbKey: String,
    fsPath: String,
    onOk: () -> Unit,
    onErr: (String) -> Unit
)

@JsFun(
    """
    (prompt) => {
      const Module = globalThis.__llamatikModule;
      if (!Module) return "Web/WASM: module not ready";
      if (!Module.ccall) return "Web/WASM: ccall not available";

      function ccallSafe(name, returnType, argTypes, args) {
        try {
          return Module.ccall(name, returnType, argTypes, args);
        } catch (e) {
          const msg = String(e || "");
          if (msg.includes("BigInt") || msg.includes("bigint") || msg.includes("Cannot mix BigInt")) {
            if (returnType === "number") {
              return Module.ccall(name, "bigint", argTypes, args);
            }
          }
          throw e;
        }
      }

      function toNumberPtr(p) {
        if (typeof p === "bigint") return Number(p);
        return p;
      }

      try {
        const ptrAny = ccallSafe("llamatik_llama_generate", "number", ["string"], [prompt]);
        const ptr = toNumberPtr(ptrAny);

        if (!ptr) return "Web/WASM: generate returned null";

        const out = Module.UTF8ToString(ptr);

        // free(ptr) — tolerate bigint builds by retrying if needed
        try {
          Module.ccall("llamatik_free_string", null, ["number"], [ptr]);
        } catch (eFree) {
          const msg = String(eFree || "");
          if (msg.includes("BigInt") || msg.includes("bigint") || msg.includes("Cannot mix BigInt")) {
            // If memory64 build expects bigint, pass BigInt(ptr)
            Module.ccall("llamatik_free_string", null, ["bigint"], [BigInt(ptr)]);
          } else {
            throw eFree;
          }
        }

        return out;
      } catch (e) {
        return "Web/WASM: generate error: " + String(e);
      }
    }
    """
)
private external fun runGenerate(prompt: String): String
