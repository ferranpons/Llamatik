// kotlin/llamatik_wasm/llamatik_worker.mjs
// Worker that:
// - loads the Emscripten wasm module
// - reconstructs the model from IndexedDB chunks into Emscripten FS
// - calls llamatik_llama_init_generate(fsPath)
// - runs llamatik_llama_generate_stream(prompt) and forwards tokens to main thread

const DB_NAME = "llamatik";
const DB_VER = 1;
const STORE_CHUNKS = "chunks";
const STORE_META = "meta";

const WASM_MJS_URL = "/kotlin/llamatik_wasm/llamatik_wasm.mjs";
const WASM_BASE_URL = "/kotlin/llamatik_wasm/";

let Module = null;
let initPromise = null;
let initKey = null; // `${idbKey}::${fsPath}`

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VER);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_CHUNKS)) db.createObjectStore(STORE_CHUNKS);
      if (!db.objectStoreNames.contains(STORE_META)) db.createObjectStore(STORE_META);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error("indexedDB.open failed"));
  });
}

function readChunkCount(db, key) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_META, "readonly");
    const meta = tx.objectStore(STORE_META);
    const r = meta.get(key);
    r.onsuccess = () => {
      const countStr = r.result;
      const count = countStr == null ? 0 : parseInt(countStr, 10);
      resolve(count);
    };
    r.onerror = () => reject(r.error || new Error("meta.get failed"));
  });
}

function readChunk(db, key, i) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_CHUNKS, "readonly");
    const chunks = tx.objectStore(STORE_CHUNKS);
    const r = chunks.get(`${key}#${i}`);
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error || new Error("chunks.get failed"));
  });
}

function ensureDir(fsPath) {
  const parts = fsPath.split("/").filter(Boolean);
  let cur = "";
  for (let i = 0; i < parts.length - 1; i++) {
    cur += "/" + parts[i];
    try { Module.FS.mkdir(cur); } catch (_) {}
  }
}

function chunkToU8(chunk) {
  if (chunk instanceof Uint8Array) return chunk;
  if (chunk instanceof ArrayBuffer) return new Uint8Array(chunk);

  if (typeof chunk !== "string") {
    // some browsers may give stored objects back as plain arrays
    try { return new Uint8Array(chunk); } catch (e) {
      throw new Error("Unsupported chunk type: " + (typeof chunk));
    }
  }

  // base64 -> bytes
  const bin = atob(chunk);
  const len = bin.length;
  const u8 = new Uint8Array(len);
  for (let i = 0; i < len; i++) u8[i] = bin.charCodeAt(i) & 255;
  return u8;
}

async function loadModuleOnce() {
  if (Module) return Module;

  const mod = await import(/* webpackIgnore: true */ WASM_MJS_URL);
  const factory = mod.default || mod;

  Module = await factory({
    locateFile: (p) => WASM_BASE_URL + p,
  });

  if (!Module || !Module.FS || !Module.ccall) {
    throw new Error(
      "Emscripten module missing FS/ccall. Rebuild wasm with -sFORCE_FILESYSTEM=1 " +
      "and export runtime methods (FS, ccall, UTF8ToString, etc.)"
    );
  }

  return Module;
}

async function writeModelFromIndexedDb(idbKey, fsPath) {
  const db = await openDb();
  const count = await readChunkCount(db, idbKey);
  if (!count || count <= 0) throw new Error("Model not downloaded yet. Please download the model first. (key: " + idbKey + ")");

  ensureDir(fsPath);

  let stream;
  try {
    stream = Module.FS.open(fsPath, "w+");
  } catch (e) {
    throw new Error("FS.open failed: " + String(e));
  }

  let offset = 0;
  try {
    for (let idx = 0; idx < count; idx++) {
      const chunkVal = await readChunk(db, idbKey, idx);
      const u8 = chunkToU8(chunkVal);
      Module.FS.write(stream, u8, 0, u8.length, offset);
      offset += u8.length;
    }
  } finally {
    try { Module.FS.close(stream); } catch (_) {}
  }
}

async function ensureInitialized(idbKey, fsPath) {
  await loadModuleOnce();

  const key = `${idbKey}::${fsPath}`;
  if (initPromise && initKey === key) return initPromise;

  initKey = key;
  initPromise = (async () => {
    await writeModelFromIndexedDb(idbKey, fsPath);

    const ok = Module.ccall(
      "llamatik_llama_init_generate",
      "number",
      ["string"],
      [fsPath]
    );

    if (ok !== 1) throw new Error("llamatik_llama_init_generate returned " + ok);
  })().catch((e) => {
    // Reset so next attempt can retry
    initKey = null;
    initPromise = null;
    throw e;
  });

  return initPromise;
}

let vlmInitKey = null;
let vlmInitPromise = null;
let vlmLoaded = false;

async function ensureVlmInitialized(idbKey, fsPath, mmprojIdbKey, mmprojFsPath) {
  await loadModuleOnce();

  const key = `${idbKey}::${fsPath}::${mmprojIdbKey}::${mmprojFsPath}`;
  if (vlmInitPromise && vlmInitKey === key) return vlmInitPromise;

  vlmInitKey = key;
  vlmLoaded = false;
  vlmInitPromise = (async () => {
    if (Module.ccall && vlmLoaded) {
      try { Module.ccall("llamatik_vlm_release", null, [], []); } catch (_) {}
    }
    vlmLoaded = false;

    await writeModelFromIndexedDb(idbKey, fsPath);
    await writeModelFromIndexedDb(mmprojIdbKey, mmprojFsPath);

    const ok = Module.ccall(
      "llamatik_vlm_init",
      "number",
      ["string", "string"],
      [fsPath, mmprojFsPath]
    );

    if (ok !== 1) throw new Error("llamatik_vlm_init returned " + ok);
    vlmLoaded = true;
  })();

  return vlmInitPromise;
}

// Hooked by C++ via EM_ASM in streaming mode
globalThis.__llamatik_stream_token = (s) => {
  try { self.postMessage({ type: "delta", requestId: currentRequestId, delta: String(s) }); } catch (_) {}
};
globalThis.__llamatik_stream_done = () => {
  try { self.postMessage({ type: "done", requestId: currentRequestId }); } catch (_) {}
};
globalThis.__llamatik_stream_error = (s) => {
  try { self.postMessage({ type: "error", requestId: currentRequestId, error: String(s) }); } catch (_) {}
};

let currentRequestId = 0;

self.onmessage = async (ev) => {
  const m = ev.data || {};
  try {
    if (m.type === "init") {
      try {
        await ensureInitialized(m.idbKey, m.fsPath);
        self.postMessage({ type: "init_ok" });
      } catch (e) {
        self.postMessage({ type: "init_err", error: String(e && e.message ? e.message : e) });
      }
      return;
    }

    if (m.type === "generate") {
      currentRequestId = m.requestId || 0;

      // Ensure init (worker can be created before init message arrives in some races)
      try {
        await ensureInitialized(m.idbKey, m.fsPath);
      } catch (e) {
        self.postMessage({ type: "error", requestId: currentRequestId, error: String(e && e.message ? e.message : e) });
        self.postMessage({ type: "done", requestId: currentRequestId });
        currentRequestId = 0;
        return;
      }

      // Run streaming generation (C++ will call __llamatik_stream_* callbacks)
      Module.ccall(
        "llamatik_llama_generate_stream",
        null,
        ["string"],
        [m.prompt || ""]
      );

      // Note: 'done' is sent by C++ via __llamatik_stream_done
      return;
    }

    if (m.type === "vlm_init") {
      await ensureVlmInitialized(m.idbKey, m.fsPath, m.mmprojIdbKey, m.mmprojFsPath);
      self.postMessage({ type: "vlm_init_ok" });
      return;
    }

    if (m.type === "vlm_analyze") {
      if (!vlmLoaded) {
        self.postMessage({ type: "error", requestId: m.requestId, error: "VLM model not initialized" });
        self.postMessage({ type: "done", requestId: m.requestId });
        return;
      }

      currentRequestId = m.requestId || 0;

      const imgU8 = m.imageBytes instanceof Uint8Array ? m.imageBytes : new Uint8Array(m.imageBytes);
      // _malloc may return Number or BigInt depending on Emscripten wasm32/wasm64 build.
      // Normalize: numPtr for heap.set (needs Number), bigPtr for wasm function calls (needs BigInt i64).
      const rawPtr = Module._malloc(imgU8.length);
      const numPtr = typeof rawPtr === 'bigint' ? Number(rawPtr) : rawPtr;
      const bigPtr = typeof rawPtr === 'bigint' ? rawPtr : BigInt(rawPtr);

      const heap = Module.HEAPU8 ?? (Module.wasmMemory ? new Uint8Array(Module.wasmMemory.buffer) : null);
      if (!heap) throw new Error("WASM memory not accessible — rebuild with HEAPU8 in EXPORTED_RUNTIME_METHODS");
      heap.set(imgU8, numPtr);

      const promptStr = m.prompt || "";
      const promptLen = Module.lengthBytesUTF8(promptStr) + 1;
      const rawPromptPtr = Module._malloc(promptLen);
      const numPromptPtr = typeof rawPromptPtr === 'bigint' ? Number(rawPromptPtr) : rawPromptPtr;
      const bigPromptPtr = typeof rawPromptPtr === 'bigint' ? rawPromptPtr : BigInt(rawPromptPtr);
      // stringToUTF8 is a runtime helper that expects Number offset
      Module.stringToUTF8(promptStr, numPromptPtr, promptLen);
      try {
        // _llamatik_vlm_analyze_stream takes i64 pointer args (BigInt in JS)
        Module._llamatik_vlm_analyze_stream(bigPtr, BigInt(imgU8.length), bigPromptPtr);
      } finally {
        try { Module._free(bigPtr); } catch (_) { Module._free(numPtr); }
        try { Module._free(bigPromptPtr); } catch (_) { Module._free(numPromptPtr); }
      }
      return;
    }

    if (m.type === "vlm_cancel") {
      if (vlmLoaded) {
        try { Module.ccall("llamatik_vlm_cancel", null, [], []); } catch (_) {}
      }
      return;
    }

  } catch (e) {
    const msg = String(e && e.message ? e.message : e);
    self.postMessage({ type: "worker-error", message: msg, detail: String(e && e.stack ? e.stack : "") });
  }
};
