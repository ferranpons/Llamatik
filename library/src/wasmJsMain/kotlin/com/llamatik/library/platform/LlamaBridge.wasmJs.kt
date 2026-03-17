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
    private val hasSession = AtomicBoolean(false)
    private val wasmScope = CoroutineScope(Dispatchers.Default)

    private var lastIdbKey: String? = null
    private var lastFsPath: String? = null

    actual fun getModelPath(modelFileName: String): String = modelFileName

    actual fun initEmbedModel(modelPath: String): Boolean = false
    actual fun embed(input: String): FloatArray = floatArrayOf()

    actual fun initGenerateModel(modelPath: String): Boolean {
        val fileName = sanitizeName(modelPath.substringAfterLast('/'))
        val idbKey = "models/$fileName"
        val fsPath = "/models/$fileName"

        lastIdbKey = idbKey
        lastFsPath = fsPath
        hasSession.store(false)

        // Worker-only ownership for WASM generation.
        // We only remember which model to use; the worker performs the real init.
        moduleReady.store(true)
        modelReady.store(true)
        initInFlight.store(false)

        return true
    }

    actual fun generate(prompt: String): String {
        return "Web/WASM: synchronous generate() is not supported in worker-only mode. Use generateStream()."
    }

    actual fun generateContinue(prompt: String): String {
        return "Web/WASM: synchronous generateContinue() is not supported in worker-only mode. Use generateStream()."
    }

    actual fun sessionReset(): Boolean {
        if (!modelReady.load()) return false

        val idbKey = lastIdbKey
        val fsPath = lastFsPath
        if (idbKey == null || fsPath == null) return false

        runSessionResetWorker(
            idbKey = idbKey,
            fsPath = fsPath,
            onDone = {},
            onErr = {}
        )
        hasSession.store(false)
        return true
    }

    actual fun sessionSave(path: String): Boolean = false
    actual fun sessionLoad(path: String): Boolean = false

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

        val continueMode = hasSession.load()

        wasmScope.launch {
            runGenerateStreamWorker(
                idbKey = idbKey,
                fsPath = fsPath,
                prompt = prompt,
                continueMode = continueMode,
                onDelta = { callback.onDelta(it) },
                onDone = {
                    hasSession.store(true)
                    callback.onComplete()
                },
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

        val continueMode = hasSession.load()

        wasmScope.launch {
            runGenerateStreamWorker(
                idbKey = idbKey,
                fsPath = fsPath,
                prompt = "$system\n\n$context\n\n$user",
                continueMode = continueMode,
                onDelta = onDelta,
                onDone = {
                    hasSession.store(true)
                    onDone()
                },
                onErr = onError
            )
        }
    }

    actual fun shutdown() {
        hasSession.store(false)
    }

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

@JsFun(
    """
    (idbKey, fsPath, prompt, continueMode, onDelta, onDone, onErr) => {
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
        try {
          const r = await fetch(WORKER_URL, { method: "GET", cache: "no-store" });
          if (!r.ok) {
            const body = await safeText(r);
            onErr("Worker script not reachable: " + WORKER_URL + " (HTTP " + r.status + "). " + (body ? body.slice(0, 200) : ""));
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
          globalThis.__llamatikGenInitKey = null;

          w.onmessage = (ev) => {
            const m = ev.data || {};

            if (m.type === "worker-error") {
              const msg = String(m.message || "Worker error");
              const detail = m.detail ? ("\\n" + String(m.detail)) : "";
              failAllPending(msg + detail);
              globalThis.__llamatikGenWorkerReady = false;
              globalThis.__llamatikGenInitSent = false;
              globalThis.__llamatikGenInitKey = null;
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
              globalThis.__llamatikGenInitKey = null;

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
            else if (m.type === "done") {
              cb.onDone();
              globalThis.__llamatikGenCallbacks.delete(m.requestId);
            }
          };

          w.addEventListener("error", (ev) => {
            const msg =
              "Worker error event: " + (ev && ev.message ? ev.message : "unknown") +
              " at " + (ev && ev.filename ? ev.filename : "") +
              ":" + (ev && ev.lineno ? ev.lineno : "") +
              ":" + (ev && ev.colno ? ev.colno : "");
            failAllPending(msg);
            globalThis.__llamatikGenWorkerReady = false;
            globalThis.__llamatikGenInitSent = false;
            globalThis.__llamatikGenInitKey = null;
            if (ev && ev.preventDefault) ev.preventDefault();
          });

          w.addEventListener("messageerror", (ev) => {
            const msg = "Worker messageerror: " + String(ev || "unknown");
            failAllPending(msg);
            globalThis.__llamatikGenWorkerReady = false;
            globalThis.__llamatikGenInitSent = false;
            globalThis.__llamatikGenInitKey = null;
          });
        }

        const w = globalThis.__llamatikGenWorker;
        const requestId = globalThis.__llamatikGenReqId++;
        const initKey = idbKey + "|" + fsPath;

        globalThis.__llamatikGenCallbacks.set(requestId, { onDelta, onDone, onErr });

        const payload = {
          type: continueMode ? "generate_continue" : "generate",
          requestId,
          idbKey,
          fsPath,
          prompt
        };

        const needsInit =
          !globalThis.__llamatikGenWorkerReady ||
          globalThis.__llamatikGenInitKey !== initKey;

        if (needsInit) {
          globalThis.__llamatikGenWorkerReady = false;
          globalThis.__llamatikGenQueue.push(payload);

          if (!globalThis.__llamatikGenInitSent || globalThis.__llamatikGenInitKey !== initKey) {
            globalThis.__llamatikGenInitSent = true;
            globalThis.__llamatikGenInitKey = initKey;
            w.postMessage({ type: "init", idbKey, fsPath });
          }
        } else {
          w.postMessage(payload);
        }
      })();
    }
    """
)
private external fun runGenerateStreamWorker(
    idbKey: String,
    fsPath: String,
    prompt: String,
    continueMode: Boolean,
    onDelta: (String) -> Unit,
    onDone: () -> Unit,
    onErr: (String) -> Unit
)

@JsFun(
    """
    (idbKey, fsPath, onDone, onErr) => {
      const RAW_WORKER_URL = "/kotlin/llamatik_wasm/llamatik_worker.mjs";
      const WORKER_URL = new URL(RAW_WORKER_URL, self.location.href).toString();

      function safeText(resp) {
        try { return resp.text(); } catch (_) { return Promise.resolve(""); }
      }

      (async () => {
        try {
          const r = await fetch(WORKER_URL, { method: "GET", cache: "no-store" });
          if (!r.ok) {
            const body = await safeText(r);
            onErr("Worker script not reachable: " + WORKER_URL + " (HTTP " + r.status + "). " + (body ? body.slice(0, 200) : ""));
            return;
          }
        } catch (e) {
          onErr("Worker preflight fetch failed for " + WORKER_URL + ": " + String(e));
          return;
        }

        if (!globalThis.__llamatikGenWorker) {
          onErr("Worker not initialized");
          return;
        }

        const w = globalThis.__llamatikGenWorker;
        const initKey = idbKey + "|" + fsPath;

        if (!globalThis.__llamatikGenWorkerReady || globalThis.__llamatikGenInitKey !== initKey) {
          onErr("Worker model not initialized");
          return;
        }

        const requestId = globalThis.__llamatikGenReqId++;
        globalThis.__llamatikGenCallbacks.set(requestId, {
          onDelta: () => {},
          onDone: () => {
            globalThis.__llamatikGenCallbacks.delete(requestId);
            onDone();
          },
          onErr: (msg) => {
            globalThis.__llamatikGenCallbacks.delete(requestId);
            onErr(String(msg));
          }
        });

        w.postMessage({
          type: "reset_session",
          requestId
        });
      })();
    }
    """
)
private external fun runSessionResetWorker(
    idbKey: String,
    fsPath: String,
    onDone: () -> Unit,
    onErr: (String) -> Unit
)