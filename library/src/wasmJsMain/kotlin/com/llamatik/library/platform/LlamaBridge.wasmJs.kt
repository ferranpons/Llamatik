@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * WASM implementation:
 * - Reads model from IndexedDB (DB "llamatik", stores "chunks"/"meta") using the SAME schema as shared module.
 * - Writes model into Emscripten FS at /models/<sanitizedFileName>
 * - Calls exported C function: _llamatik_llama_init_generate("/models/<file>")
 *
 * Notes:
 * - initGenerateModel is synchronous in the expect API; on web we cannot block.
 *   We start async init and return true; readiness is tracked via isReady().
 */
actual object LlamaBridge {

    private val moduleReady = AtomicBoolean(false)
    private val modelReady = AtomicBoolean(false)
    private val initInFlight = AtomicBoolean(false)
    private val wasmScope = CoroutineScope(Dispatchers.Default)

    @Composable
    actual fun getModelPath(modelFileName: String): String {
        // We return the "logical file name". The loader will map it to IndexedDB key and FS path.
        return modelFileName
    }

    actual fun initEmbedModel(modelPath: String): Boolean = false
    actual fun embed(input: String): FloatArray = floatArrayOf()

    actual fun initGenerateModel(modelPath: String): Boolean {
        // modelPath will likely be the model filename in web.
        if (modelReady.load()) return true
        if (initInFlight.load()) return true

        val fileName = sanitizeName(modelPath.substringAfterLast('/'))
        // IndexedDB key must match shared/src/wasmJsMain modelKey(): "models/<safe>"
        val idbKey = "models/$fileName"
        val fsPath = "/models/$fileName"

        ensureWasmModuleAndModel(
            idbKey = idbKey,
            fsPath = fsPath,
            onOk = {
                moduleReady.store(true)
                modelReady.store(true)
            },
            onErr = { err ->
                // allow retry
                initInFlight.store(false)
                modelReady.store(false)
                // You can replace this with your logger
                println("WASM initGenerateModel failed: $err")
            }
        )

        // Async start ok
        return true
    }

    actual fun generate(prompt: String): String {
        if (!modelReady.load()) {
            return "Web/WASM: model is still loading…"
        }
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

                // Emit in chunks to mimic streaming UI
                val chunkSize = 24
                var i = 0
                while (i < full.length) {
                    val end = (i + chunkSize).coerceAtMost(full.length)
                    callback.onDelta(full.substring(i, end))
                    i = end

                    // Yield so UI stays responsive
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
    ) {
        generateStream("$systemPrompt\n\n$contextBlock\n\n$userPrompt", callback)
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) {
        // No schema enforcement yet on wasm; same behavior as plain stream
        generateStream(prompt, callback)
    }

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) {
        generateStreamWithContext(systemPrompt, contextBlock, userPrompt, callback)
    }

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

    // ---- helpers ----

    private fun sanitizeName(input: String): String =
        input.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "model.gguf" }
}

/**
 * Calls into JS:
 * - loads the Emscripten module (from /llamatik_wasm/llamatik_wasm.mjs)
 * - reads base64 from IndexedDB (llamatik DB)
 * - writes model bytes into Emscripten FS
 * - calls _llamatik_llama_init_generate(fsPath)
 */
@JsFun(
    """
    (idbKey, fsPath, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_CHUNKS = "chunks";
      const STORE_META = "meta";

      const WASM_MJS_URL = "./kotlin/llamatik_wasm/llamatik_wasm.mjs";

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

      function readAllBase64(db, key, cb) {
        const tx0 = db.transaction(STORE_META, "readonly");
        const meta = tx0.objectStore(STORE_META);
        const getReq = meta.get(key);

        getReq.onsuccess = () => {
          const countStr = getReq.result;
          const count = (countStr == null) ? 0 : parseInt(countStr, 10);
          if (!count || count <= 0) { cb(null, null); return; }

          const chunksArr = new Array(count);
          let remaining = count;

          for (let i = 0; i < count; i++) {
            const tx = db.transaction(STORE_CHUNKS, "readonly");
            const chunks = tx.objectStore(STORE_CHUNKS);
            const r = chunks.get(key + "#" + i);

            r.onsuccess = () => {
              chunksArr[i] = r.result || "";
              remaining--;
              if (remaining === 0) cb(null, chunksArr.join(""));
            };
            r.onerror = () => cb(String(r.error || "chunk read error"), null);
          }
        };

        getReq.onerror = () => cb(String(getReq.error || "meta get error"), null);
      }

      function b64ToU8(b64) {
        // atob gives binary string
        const bin = atob(b64);
        const len = bin.length;
        const u8 = new Uint8Array(len);
        for (let i = 0; i < len; i++) u8[i] = bin.charCodeAt(i);
        return u8;
      }

      function ensureDir(Module, path) {
        // mkdir -p
        const parts = path.split("/").filter(Boolean);
        let cur = "";
        for (let i = 0; i < parts.length - 1; i++) {
          cur += "/" + parts[i];
          try { Module.FS.mkdir(cur); } catch(e) {}
        }
      }

      async function loadModule() {
        if (globalThis.__llamatikModule) return globalThis.__llamatikModule;

        // Import the Emscripten ES module
        const mod = await import(WASM_MJS_URL);
        const factory = mod.default || mod;
        const instance = await factory({
          locateFile: (p) => "./kotlin/llamatik_wasm/" + p
        });

        globalThis.__llamatikModule = instance;
        return instance;
      }

      (async () => {
        try {
          const Module = await loadModule();

          openDb((e, db) => {
            if (e) { onErr(e); return; }

            readAllBase64(db, idbKey, (e2, b64) => {
              if (e2) { onErr(e2); return; }
              if (!b64) { onErr("Model not found in IndexedDB for key: " + idbKey); return; }

              const bytes = b64ToU8(b64);

              // write file into Emscripten FS
              ensureDir(Module, fsPath);
              try {
                Module.FS.writeFile(fsPath, bytes, { encoding: "binary" });
              } catch (e3) {
                onErr("FS.writeFile failed: " + String(e3));
                return;
              }

              // call exported init
              try {
                // If ccall is available
                if (Module.ccall) {
                  const ok = Module.ccall("llamatik_llama_init_generate", "number", ["string"], [fsPath]);
                  if (ok === 1) onOk();
                  else onErr("llamatik_llama_init_generate returned " + ok);
                } else if (Module._llamatik_llama_init_generate) {
                  // fallback: call raw symbol (string passing not supported here)
                  onErr("ccall not available on Module");
                } else {
                  onErr("Init function not found on Module");
                }
              } catch (e4) {
                onErr("Init call failed: " + String(e4));
              }
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

      // If you later expose a real generate function, call it here.
      // Right now your C++ wrapper was echoing; this will still work once you export the function and add ccall.
      if (Module.ccall) {
        try {
          const ptr = Module.ccall("llamatik_llama_generate", "number", ["string"], [prompt]);
          if (!ptr) return "Web/WASM: generate returned null";

          const out = Module.UTF8ToString(ptr);
          Module.ccall("llamatik_free_string", null, ["number"], [ptr]);
          return out;
        } catch (e) {
          return "Web/WASM: generate error: " + String(e);
        }
      }
      return "Web/WASM: ccall not available";
    }
    """
)
private external fun runGenerate(prompt: String): String
