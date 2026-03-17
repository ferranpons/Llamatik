const WASM_MJS_URL = "/kotlin/llamatik_wasm/llamatik_wasm.mjs";
const WASM_BASE_URL = "/kotlin/llamatik_wasm/";

let Module = null;
let initDone = false;
let initPromise = null;
let currentRequestId = null;
let currentModelKey = null; // idbKey + fsPath identity

function post(msg) {
  self.postMessage(msg);
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

function ensureDir(Module, path) {
  const parts = path.split("/").filter(Boolean);
  let cur = "";
  for (let i = 0; i < parts.length - 1; i++) {
    cur += "/" + parts[i];
    try { Module.FS.mkdir(cur); } catch (_) {}
  }
}

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open("llamatik", 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("chunks")) db.createObjectStore("chunks");
      if (!db.objectStoreNames.contains("meta")) db.createObjectStore("meta");
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(String(req.error || "open error"));
  });
}

function readChunkCount(db, key) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction("meta", "readonly");
    const r = tx.objectStore("meta").get(key);
    r.onsuccess = () => {
      const countStr = r.result;
      resolve((countStr == null) ? 0 : parseInt(countStr, 10));
    };
    r.onerror = () => reject(String(r.error || "meta get error"));
  });
}

function readChunk(db, key, i) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction("chunks", "readonly");
    const r = tx.objectStore("chunks").get(key + "#" + i);
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(String(r.error || "chunk read error"));
  });
}

async function loadModule() {
  if (Module) return Module;

  const mod = await import(WASM_MJS_URL);
  const factory = mod.default || mod;

  Module = await factory({
    locateFile: (p) => WASM_BASE_URL + p
  });

  Module.__llamatik_model_loaded = false;
  Module.__llamatik_model_path = null;

  Module.__llamatik_stream_token = (delta) => {
    if (currentRequestId != null) {
      post({ type: "delta", requestId: currentRequestId, delta });
    }
  };

  Module.__llamatik_stream_done = () => {
    if (currentRequestId != null) {
      post({ type: "done", requestId: currentRequestId });
    }
    currentRequestId = null;
  };

  Module.__llamatik_stream_error = (error) => {
    if (currentRequestId != null) {
      post({ type: "error", requestId: currentRequestId, error: String(error) });
    }
    currentRequestId = null;
  };

  return Module;
}

async function ensureModel(idbKey, fsPath) {
  const mod = await loadModule();
  const requestedKey = `${idbKey}::${fsPath}`;

  // Already initialized for same model
  if (initDone && currentModelKey === requestedKey) {
    return;
  }

  // Another init for the same model is already in progress
  if (initPromise && currentModelKey === requestedKey) {
    await initPromise;
    return;
  }

  // If a different init is in progress, wait for it first
  if (initPromise) {
    await initPromise;
    if (initDone && currentModelKey === requestedKey) {
      return;
    }
  }

  currentModelKey = requestedKey;

  initPromise = (async () => {
    const db = await openDb();
    const count = await readChunkCount(db, idbKey);
    if (!count || count <= 0) {
      throw new Error("Model not found in IndexedDB for key: " + idbKey);
    }

    if (!(mod.__llamatik_model_loaded && mod.__llamatik_model_path === fsPath)) {
      ensureDir(mod, fsPath);

      let stream;
      try {
        stream = mod.FS.open(fsPath, "w+");
        let offset = 0;

        for (let i = 0; i < count; i++) {
          const chunkVal = await readChunk(db, idbKey, i);
          const u8 = chunkToU8(chunkVal);
          mod.FS.write(stream, u8, 0, u8.length, offset);
          offset += u8.length;
        }
      } finally {
        try { if (stream) mod.FS.close(stream); } catch (_) {}
      }

      const ok = mod.ccall("llamatik_llama_init_generate", "number", ["string"], [fsPath]);
      if (ok !== 1) {
        throw new Error("llamatik_llama_init_generate returned " + ok);
      }

      mod.__llamatik_model_loaded = true;
      mod.__llamatik_model_path = fsPath;
    }

    initDone = true;
  })();

  try {
    await initPromise;
  } finally {
    initPromise = null;
  }
}

self.onmessage = async (ev) => {
  const m = ev.data || {};

  try {
    if (m.type === "init") {
      await ensureModel(m.idbKey, m.fsPath);
      post({ type: "init_ok" });
      return;
    }

    if (!initDone) {
      post({ type: "worker-error", message: "Worker not initialized" });
      return;
    }

    if (m.type === "generate") {
      if (currentRequestId != null) {
        post({ type: "error", requestId: m.requestId, error: "Generation already in progress" });
        post({ type: "done", requestId: m.requestId });
        return;
      }

      currentRequestId = m.requestId;
      Module.ccall("llamatik_llama_generate_stream", null, ["string"], [m.prompt]);
      return;
    }

    if (m.type === "generate_continue") {
      if (currentRequestId != null) {
        post({ type: "error", requestId: m.requestId, error: "Generation already in progress" });
        post({ type: "done", requestId: m.requestId });
        return;
      }

      currentRequestId = m.requestId;
      Module.ccall("llamatik_llama_generate_continue_stream", null, ["string"], [m.prompt]);
      return;
    }

    if (m.type === "reset_session") {
      if (currentRequestId != null) {
        post({ type: "error", requestId: m.requestId, error: "Cannot reset during active generation" });
        post({ type: "done", requestId: m.requestId });
        return;
      }

      Module.ccall("llamatik_llama_session_reset", null, [], []);
      post({ type: "done", requestId: m.requestId });
      return;
    }

    post({ type: "worker-error", message: "Unknown worker message type: " + m.type });
  } catch (e) {
    currentRequestId = null;
    post({
      type: "worker-error",
      message: String(e && e.message ? e.message : e),
      detail: e && e.stack ? String(e.stack) : ""
    });
  }
};