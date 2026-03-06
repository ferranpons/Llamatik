@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.llamatik.library.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual object LlamaBridge {

    private val moduleReady = AtomicBoolean(false)
    private val modelReady = AtomicBoolean(false)
    private val initInFlight = AtomicBoolean(false)
    private val wasmScope = CoroutineScope(Dispatchers.Default)

    // Remember last model paths so streaming can init the worker with the same model.
    private var lastIdbKey: String? = null
    private var lastFsPath: String? = null

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

        lastIdbKey = idbKey
        lastFsPath = fsPath

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

        val idbKey = lastIdbKey
        val fsPath = lastFsPath
        if (idbKey == null || fsPath == null) {
            callback.onError("Web/WASM: model path not set (initGenerateModel not called?)")
            return
        }

        // Real streaming: run generation in a Worker and receive token deltas.
        wasmScope.launch {
            runGenerateStreamWorker(
                idbKey = idbKey,
                fsPath = fsPath,
                prompt = prompt,
                onDelta = { callback.onDelta(it) },
                onDone = { callback.onComplete() },
                onErr = { callback.onError(it) }
            )
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

        val idbKey = lastIdbKey
        val fsPath = lastFsPath
        if (idbKey == null || fsPath == null) {
            onError("Web/WASM: model path not set (initGenerateModel not called?)")
            return
        }

        wasmScope.launch {
            runGenerateStreamWorker(
                idbKey = idbKey,
                fsPath = fsPath,
                prompt = "$system\n\n$context\n\n$user",
                onDelta = onDelta,
                onDone = onDone,
                onErr = onError
            )
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

        const bin = atob(chunk);
        const len = bin.length;
        const u8 = new Uint8Array(len);
        for (let i = 0; i < len; i++) u8[i] = bin.charCodeAt(i) & 255;
        return u8;
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
                    const ok = Module.ccall("llamatik_llama_init_generate", "number", ["string"], [fsPath]);
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
    """
)
private external fun runGenerate(prompt: String): String

@JsFun(
    """
    (idbKey, fsPath, prompt, onDelta, onDone, onErr) => {
      const RAW_WORKER_URL = "/kotlin/llamatik_wasm/llamatik_worker.mjs";
      const WORKER_URL = new URL(RAW_WORKER_URL, self.location.href).toString();

      function safeText(resp) {
        try { return resp.text(); } catch (_) { return Promise.resolve(""); }
      }

      function failAllPending(msg) {
        try {
          for (const [id, cb] of globalThis.__llamatikGenCallbacks.entries()) {
            cb.onErr(String(msg));
            cb.onDone();
            globalThis.__llamatikGenCallbacks.delete(id);
          }
        } catch (_) {}
      }

      (async () => {
        // --- Preflight: verify worker URL is actually reachable ---
        try {
          const r = await fetch(WORKER_URL, { method: "GET", cache: "no-store" });
          if (!r.ok) {
            const body = await safeText(r);
            onErr("Worker script not reachable: " + WORKER_URL + " (HTTP " + r.status + "). " + (body ? body.slice(0, 200) : ""));
            onDone();
            return;
          }
          const ct = (r.headers.get("content-type") || "").toLowerCase();
          // Not strictly required, but helps catch the common case: HTML returned instead of JS.
          if (ct && ct.indexOf("javascript") === -1 && ct.indexOf("ecmascript") === -1 && ct.indexOf("text/plain") === -1) {
            onErr("Worker script has unexpected content-type: " + ct + " for " + WORKER_URL);
            onDone();
            return;
          }
        } catch (e) {
          onErr("Worker preflight fetch failed for " + WORKER_URL + ": " + String(e));
          onDone();
          return;
        }

        if (!globalThis.__llamatikGenWorker) {
          const w = new Worker(WORKER_URL, { type: "module" });
          globalThis.__llamatikGenWorker = w;
          globalThis.__llamatikGenWorkerReady = false;
          globalThis.__llamatikGenInitSent = false;
          globalThis.__llamatikGenReqId = 1;
          globalThis.__llamatikGenCallbacks = new Map();
          globalThis.__llamatikGenQueue = [];

          w.onmessage = (ev) => {
            const m = ev.data || {};

            if (m.type === "worker-error") {
              const msg = String(m.message || "Worker error");
              const detail = m.detail ? ("\\n" + String(m.detail)) : "";
              console.error("llamatik worker-error:", msg, m);
              failAllPending(msg + detail);
              globalThis.__llamatikGenWorkerReady = false;
              globalThis.__llamatikGenInitSent = false;
              return;
            }

            if (m.type === "init_ok") {
              globalThis.__llamatikGenWorkerReady = true;
              const q = globalThis.__llamatikGenQueue.splice(0);
              q.forEach((payload) => w.postMessage(payload));
              return;
            }

            if (m.type === "init_err") {
              globalThis.__llamatikGenWorkerReady = false;
              globalThis.__llamatikGenInitSent = false;

              const q = globalThis.__llamatikGenQueue.splice(0);
              q.forEach((payload) => {
                const cb = globalThis.__llamatikGenCallbacks.get(payload.requestId);
                if (cb) {
                  cb.onErr(String(m.error || "init error"));
                  cb.onDone();
                  globalThis.__llamatikGenCallbacks.delete(payload.requestId);
                }
              });
              return;
            }

            const cb = globalThis.__llamatikGenCallbacks.get(m.requestId);
            if (!cb) return;

            if (m.type === "delta") cb.onDelta(String(m.delta || ""));
            else if (m.type === "error") cb.onErr(String(m.error || "error"));
            else if (m.type === "done") { cb.onDone(); globalThis.__llamatikGenCallbacks.delete(m.requestId); }
          };

          w.addEventListener("error", (ev) => {
            const msg =
              "Worker error event: " + (ev && ev.message ? ev.message : "unknown") +
              " at " + (ev && ev.filename ? ev.filename : "") +
              ":" + (ev && ev.lineno ? ev.lineno : "") +
              ":" + (ev && ev.colno ? ev.colno : "");
            console.error(msg, ev);
            failAllPending(msg);
            globalThis.__llamatikGenWorkerReady = false;
            globalThis.__llamatikGenInitSent = false;
            if (ev && ev.preventDefault) ev.preventDefault();
          });

          w.addEventListener("messageerror", (ev) => {
            const msg = "Worker messageerror: " + String(ev || "unknown");
            console.error(msg, ev);
            failAllPending(msg);
            globalThis.__llamatikGenWorkerReady = false;
            globalThis.__llamatikGenInitSent = false;
          });
        }

        const w = globalThis.__llamatikGenWorker;
        const requestId = globalThis.__llamatikGenReqId++;

        globalThis.__llamatikGenCallbacks.set(requestId, { onDelta, onDone, onErr });

        if (!globalThis.__llamatikGenWorkerReady) {
          if (!globalThis.__llamatikGenInitSent) {
            globalThis.__llamatikGenInitSent = true;
            w.postMessage({ type: "init", idbKey, fsPath });
          }
          globalThis.__llamatikGenQueue.push({ type: "generate", requestId, idbKey, fsPath, prompt });
        } else {
          w.postMessage({ type: "generate", requestId, idbKey, fsPath, prompt });
        }
      })();
    }
    """
)
private external fun runGenerateStreamWorker(
    idbKey: String,
    fsPath: String,
    prompt: String,
    onDelta: (String) -> Unit,
    onDone: () -> Unit,
    onErr: (String) -> Unit
)
