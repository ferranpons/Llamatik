@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.llamatik.library.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual object MultimodalBridge {

    private var lastIdbKey: String? = null
    private var lastFsPath: String? = null
    private var lastMmprojIdbKey: String? = null
    private var lastMmprojFsPath: String? = null
    private var vlmReady = false

    private val wasmScope = CoroutineScope(Dispatchers.Default)

    actual fun initModel(modelPath: String, mmprojPath: String): Boolean {
        // On WASM, modelPath and mmprojPath are IndexedDB keys (sanitized file names).
        // We use the same sanitizeName logic as LlamaBridge to match the stored key.
        val modelName = sanitizeName(modelPath.substringAfterLast('/'))
        val mmprojName = sanitizeName(mmprojPath.substringAfterLast('/'))
        lastIdbKey = "models/$modelName"
        lastFsPath = "/models/$modelName"
        lastMmprojIdbKey = "models/$mmprojName"
        lastMmprojFsPath = "/models/$mmprojName"
        vlmReady = false
        return true
    }

    private fun sanitizeName(input: String): String =
        input.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "model.gguf" }

    actual fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream) {
        val idbKey = lastIdbKey
        val fsPath = lastFsPath
        val mmprojIdbKey = lastMmprojIdbKey
        val mmprojFsPath = lastMmprojFsPath

        if (idbKey == null || fsPath == null || mmprojIdbKey == null || mmprojFsPath == null) {
            callback.onError("VLM model not initialized (call initModel first)")
            return
        }

        val imageBytesBase64 = Base64.encode(imageBytes)
        wasmScope.launch {
            runVlmAnalyzeWorker(
                idbKey = idbKey,
                fsPath = fsPath,
                mmprojIdbKey = mmprojIdbKey,
                mmprojFsPath = mmprojFsPath,
                imageBytesBase64 = imageBytesBase64,
                prompt = prompt,
                onDelta = { callback.onDelta(it) },
                onDone = { callback.onComplete() },
                onErr = { callback.onError(it) }
            )
        }
    }

    actual fun cancelAnalysis() {
        runVlmCancelWorker()
    }

    actual fun release() {
        lastIdbKey = null
        lastFsPath = null
        lastMmprojIdbKey = null
        lastMmprojFsPath = null
        vlmReady = false
    }
}

@JsFun(
    """
    (idbKey, fsPath, mmprojIdbKey, mmprojFsPath, imageBytesBase64, prompt, onDelta, onDone, onErr) => {
      const RAW_WORKER_URL = "/kotlin/llamatik_wasm/llamatik_worker.mjs";
      const WORKER_URL = new URL(RAW_WORKER_URL, self.location.href).toString();

      (async () => {
        try {
          const r = await fetch(WORKER_URL, { method: "GET", cache: "no-store" });
          if (!r.ok) {
            onErr("VLM worker not reachable: " + WORKER_URL + " (HTTP " + r.status + ")");
            onDone();
            return;
          }
        } catch (e) {
          onErr("VLM worker preflight failed: " + String(e));
          onDone();
          return;
        }

        if (!globalThis.__llamatikVlmWorker) {
          const w = new Worker(WORKER_URL, { type: "module" });
          globalThis.__llamatikVlmWorker = w;
          globalThis.__llamatikVlmWorkerReady = false;
          globalThis.__llamatikVlmInitSent = false;
          globalThis.__llamatikVlmReqId = 1;
          globalThis.__llamatikVlmCallbacks = new Map();
          globalThis.__llamatikVlmQueue = [];
          globalThis.__llamatikVlmModelKey = null;

          w.onmessage = (ev) => {
            const m = ev.data || {};

            if (m.type === "worker-error") {
              const msg = String(m.message || "VLM worker error");
              for (const [id, cb] of globalThis.__llamatikVlmCallbacks.entries()) {
                try { cb.e(msg); } catch(_) {}
                try { cb.c(); } catch(_) {}
                globalThis.__llamatikVlmCallbacks.delete(id);
              }
              globalThis.__llamatikVlmWorkerReady = false;
              globalThis.__llamatikVlmInitSent = false;
              globalThis.__llamatikVlmModelKey = null;
              return;
            }

            if (m.type === "vlm_init_ok") {
              globalThis.__llamatikVlmWorkerReady = true;
              const q = globalThis.__llamatikVlmQueue.splice(0);
              q.forEach((payload) => w.postMessage(payload, payload.__transfer || []));
              return;
            }

            if (m.type === "vlm_init_err") {
              globalThis.__llamatikVlmWorkerReady = false;
              globalThis.__llamatikVlmInitSent = false;
              globalThis.__llamatikVlmModelKey = null;
              const q = globalThis.__llamatikVlmQueue.splice(0);
              q.forEach((payload) => {
                const cb = globalThis.__llamatikVlmCallbacks.get(payload.requestId);
                if (cb) {
                  try { cb.e(String(m.error || "vlm init error")); } catch(_) {}
                  try { cb.c(); } catch(_) {}
                  globalThis.__llamatikVlmCallbacks.delete(payload.requestId);
                }
              });
              return;
            }

            const cb = globalThis.__llamatikVlmCallbacks.get(m.requestId);
            if (!cb) return;

            if (m.type === "delta") { try { cb.d(String(m.delta || "")); } catch(_) {} }
            else if (m.type === "error") { try { cb.e(String(m.error || "error")); } catch(_) {} }
            else if (m.type === "done") {
              try { cb.c(); } catch(_) {}
              globalThis.__llamatikVlmCallbacks.delete(m.requestId);
            }
          };

          w.addEventListener("error", (ev) => {
            const msg = "VLM worker error: " + (ev && ev.message ? ev.message : "unknown");
            for (const [id, cb] of (globalThis.__llamatikVlmCallbacks || new Map()).entries()) {
              try { cb.e(msg); } catch(_) {}
              try { cb.c(); } catch(_) {}
            }
            if (globalThis.__llamatikVlmCallbacks) globalThis.__llamatikVlmCallbacks.clear();
            globalThis.__llamatikVlmWorkerReady = false;
            globalThis.__llamatikVlmInitSent = false;
            globalThis.__llamatikVlmModelKey = null;
            if (ev && ev.preventDefault) ev.preventDefault();
          });
        }

        const w = globalThis.__llamatikVlmWorker;
        if (!globalThis.__llamatikVlmCallbacks) globalThis.__llamatikVlmCallbacks = new Map();
        if (!globalThis.__llamatikVlmQueue)      globalThis.__llamatikVlmQueue = [];
        if (!globalThis.__llamatikVlmReqId)      globalThis.__llamatikVlmReqId = 1;
        const requestId = globalThis.__llamatikVlmReqId++;
        const modelKey = idbKey + "|" + fsPath + "|" + mmprojIdbKey + "|" + mmprojFsPath;

        const cbFns = { d: onDelta, c: onDone, e: onErr };
        globalThis.__llamatikVlmCallbacks.set(requestId, cbFns);

        // Decode base64 image bytes on the JS side
        const binStr = atob(imageBytesBase64);
        const imgU8 = new Uint8Array(binStr.length);
        for (let i = 0; i < binStr.length; i++) imgU8[i] = binStr.charCodeAt(i) & 255;

        const payload = {
          type: "vlm_analyze",
          requestId,
          imageBytes: imgU8,
          prompt
        };

        const needsInit =
          !globalThis.__llamatikVlmWorkerReady ||
          globalThis.__llamatikVlmModelKey !== modelKey;

        if (needsInit) {
          globalThis.__llamatikVlmWorkerReady = false;
          globalThis.__llamatikVlmQueue.push(payload);

          if (!globalThis.__llamatikVlmInitSent || globalThis.__llamatikVlmModelKey !== modelKey) {
            globalThis.__llamatikVlmInitSent = true;
            globalThis.__llamatikVlmModelKey = modelKey;
            w.postMessage({ type: "vlm_init", idbKey, fsPath, mmprojIdbKey, mmprojFsPath });
          }
        } else {
          w.postMessage(payload);
        }
      })();
    }
    """
)
private external fun runVlmAnalyzeWorker(
    idbKey: String,
    fsPath: String,
    mmprojIdbKey: String,
    mmprojFsPath: String,
    imageBytesBase64: String,
    prompt: String,
    onDelta: (String) -> Unit,
    onDone: () -> Unit,
    onErr: (String) -> Unit
)

@JsFun("() => { if (globalThis.__llamatikVlmWorker) globalThis.__llamatikVlmWorker.postMessage({ type: 'vlm_cancel' }); }")
private external fun runVlmCancelWorker()
