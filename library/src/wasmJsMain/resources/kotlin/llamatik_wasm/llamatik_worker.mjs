const DB_NAME = "llamatik";
const DB_VER = 1;
const STORE_CHUNKS = "chunks";
const STORE_META = "meta";

const WASM_MJS_URL  = "/kotlin/llamatik_wasm/llamatik_wasm.mjs";
const WASM_BASE_URL = "/kotlin/llamatik_wasm/";

let Module = null;
let modelReady = false;
let initInFlight = false;

let currentRequestId = 0;

// --- Report hard worker failures back to main thread (when possible) ---
function postWorkerError(message, detail) {
  try {
    postMessage({
      type: "worker-error",
      message: String(message || "Worker error"),
      detail: detail ? String(detail) : ""
    });
  } catch (_) {}
}

self.addEventListener("error", (ev) => {
  // This will run only if the worker script loaded enough to register listeners.
  const msg =
    "Worker runtime error: " + String(ev && ev.message ? ev.message : "unknown") +
    " at " + String(ev && ev.filename ? ev.filename : "") +
    ":" + String(ev && ev.lineno ? ev.lineno : "") +
    ":" + String(ev && ev.colno ? ev.colno : "");
  postWorkerError(msg, ev && ev.error && ev.error.stack ? ev.error.stack : "");
  if (ev && ev.preventDefault) ev.preventDefault();
});

self.addEventListener("unhandledrejection", (ev) => {
  const reason = ev && ev.reason ? ev.reason : "unknown";
  const msg = "Worker unhandledrejection: " + String(reason && reason.message ? reason.message : reason);
  const stack = reason && reason.stack ? reason.stack : "";
  postWorkerError(msg, stack);
  if (ev && ev.preventDefault) ev.preventDefault();
});

// --- streaming callbacks used by EM_ASM ---
globalThis.__llamatik_stream_token = (delta) => {
  postMessage({ type: "delta", requestId: currentRequestId, delta: String(delta ?? "") });
};
globalThis.__llamatik_stream_done = () => {
  postMessage({ type: "done", requestId: currentRequestId });
};
globalThis.__llamatik_stream_error = (error) => {
  postMessage({ type: "error", requestId: currentRequestId, error: String(error ?? "error") });
};

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

function ensureDir(path) {
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

  // Base64 chunk
  const bin = atob(chunk);
  const len = bin.length;
  const u8 = new Uint8Array(len);
  for (let i = 0; i < len; i++) u8[i] = bin.charCodeAt(i) & 255;
  return u8;
}

async function loadModuleOnce() {
  if (Module) return Module;

  let mod;
  try {
    mod = await import(/* webpackIgnore: true */ WASM_MJS_URL);
  } catch (e) {
    // If import fails, tell main thread explicitly.
    postWorkerError("Failed to import WASM module: " + WASM_MJS_URL, String(e));
    throw e;
  }

  const factory = mod.default || mod;

  Module = await factory({
    locateFile: (p) => WASM_BASE_URL + p
  });

  // Make Module globally visible for EM_ASM
  globalThis.Module = Module;

  return Module;
}

async function ensureModelReady(idbKey, fsPath) {
  if (modelReady) return true;
  if (initInFlight) return true;

  initInFlight = true;

  try {
    await loadModuleOnce();

    if (!Module.FS || !Module.ccall) {
      postMessage({ type: "init_err", error: "Emscripten runtime missing FS/ccall" });
      initInFlight = false;
      return false;
    }

    return await new Promise((resolve) => {
      openDb((e, db) => {
        if (e) {
          postMessage({ type: "init_err", error: e });
          initInFlight = false;
          resolve(false);
          return;
        }

        readChunkCount(db, idbKey, (eCount, count) => {
          if (eCount || !count || count <= 0) {
            postMessage({ type: "init_err", error: eCount || ("Model not found in IDB: " + idbKey) });
            initInFlight = false;
            resolve(false);
            return;
          }

          ensureDir(fsPath);

          let stream;
          try {
            stream = Module.FS.open(fsPath, "w+");
          } catch (e0) {
            postMessage({ type: "init_err", error: "FS.open failed: " + String(e0) });
            initInFlight = false;
            resolve(false);
            return;
          }

          let idx = 0;
          let offset = 0;

          function next() {
            if (idx >= count) {
              try { Module.FS.close(stream); } catch(eClose) {}

              try {
                const ok = Module.ccall("llamatik_llama_init_generate", "number", ["string"], [fsPath]);
                if (ok === 1) {
                  modelReady = true;
                  initInFlight = false;
                  postMessage({ type: "init_ok" });
                  resolve(true);
                } else {
                  modelReady = false;
                  initInFlight = false;
                  postMessage({ type: "init_err", error: "llamatik_llama_init_generate returned " + ok });
                  resolve(false);
                }
              } catch (eInit) {
                modelReady = false;
                initInFlight = false;
                postMessage({ type: "init_err", error: "Init call failed: " + String(eInit) });
                resolve(false);
              }
              return;
            }

            readChunk(db, idbKey, idx, (eChunk, chunkVal) => {
              if (eChunk) {
                try { Module.FS.close(stream); } catch(eClose) {}
                initInFlight = false;
                postMessage({ type: "init_err", error: eChunk });
                resolve(false);
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
                initInFlight = false;
                postMessage({ type: "init_err", error: "Chunk decode/write failed at #" + idx + ": " + String(eWrite) });
                resolve(false);
              }
            });
          }

          next();
        });
      });
    });
  } catch (e) {
    initInFlight = false;
    postMessage({ type: "init_err", error: String(e) });
    return false;
  }
}

onmessage = async (ev) => {
  const msg = ev.data || {};

  if (msg.type === "init") {
    await ensureModelReady(msg.idbKey, msg.fsPath);
    return;
  }

  if (msg.type === "generate") {
    const ok = await ensureModelReady(msg.idbKey, msg.fsPath);
    if (!ok) {
      postMessage({ type: "error", requestId: msg.requestId, error: "Worker model init failed" });
      postMessage({ type: "done", requestId: msg.requestId });
      return;
    }

    try {
      currentRequestId = msg.requestId;
      Module.ccall("llamatik_llama_generate_stream", null, ["string"], [String(msg.prompt ?? "")]);
      // completion is signaled by __llamatik_stream_done()
    } catch (e) {
      postMessage({ type: "error", requestId: msg.requestId, error: String(e) });
      postMessage({ type: "done", requestId: msg.requestId });
    }
  }
};