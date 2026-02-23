@file:OptIn(ExperimentalWasmJsInterop::class)

package com.llamatik.app.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IndexedDB-backed storage for WASM.
 *
 * Data model:
 * - DB: "llamatik"
 * - store: "chunks"
 *   key: "<fileKey>#<index>"
 *   value: base64 chunk string
 * - store: "meta"
 *   key: "<fileKey>"
 *   value: "<count>" (string)
 *
 * We keep everything as String in IDB to avoid forbidden ByteArray interop.
 */
internal object IndexedDbFiles {

    suspend fun appendChunkBase64(fileKey: String, chunkBase64: String) {
        idbAppendChunkBase64Suspend(fileKey, chunkBase64)
    }

    suspend fun writeAllBase64(fileKey: String, fullBase64: String) {
        // Replace file by deleting first, then append one chunk
        delete(fileKey)
        appendChunkBase64(fileKey, fullBase64)
    }

    suspend fun readAllBase64(fileKey: String): String? {
        return idbReadAllBase64Suspend(fileKey)
    }

    suspend fun exists(fileKey: String): Boolean {
        return (idbGetChunkCountSuspend(fileKey) ?: 0) > 0
    }

    suspend fun delete(fileKey: String): Boolean {
        return idbDeleteFileSuspend(fileKey)
    }
}

// ---------------- suspend wrappers ----------------

private suspend fun idbAppendChunkBase64Suspend(fileKey: String, chunkBase64: String) =
    suspendCancellableCoroutine<Unit> { cont ->
        idbAppendChunkBase64(
            fileKey,
            chunkBase64,
            onOk = { cont.resume(Unit) },
            onErr = { cont.resumeWithException(IllegalStateException(it)) }
        )
    }

private suspend fun idbReadAllBase64Suspend(fileKey: String): String? =
    suspendCancellableCoroutine { cont ->
        idbReadAllBase64(
            fileKey,
            onOk = { cont.resume(it) },
            onErr = { cont.resumeWithException(IllegalStateException(it)) }
        )
    }

private suspend fun idbGetChunkCountSuspend(fileKey: String): Int? =
    suspendCancellableCoroutine { cont ->
        idbGetChunkCount(
            fileKey,
            onOk = { cont.resume(it) },
            onErr = { cont.resumeWithException(IllegalStateException(it)) }
        )
    }

private suspend fun idbDeleteFileSuspend(fileKey: String): Boolean =
    suspendCancellableCoroutine { cont ->
        idbDeleteFile(
            fileKey,
            onOk = { cont.resume(it) },
            onErr = { cont.resumeWithException(IllegalStateException(it)) }
        )
    }

// ---------------- JS interop ----------------
//
// NOTE: Kotlin/Wasm JS interop only allows primitives/strings/functions/external types.
// We use callbacks (functions) for async IDB operations.

@JsFun(
    """
    (fileKey, chunkB64, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_CHUNKS = "chunks";
      const STORE_META = "meta";

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

      openDb((e, db) => {
        if (e) { onErr(e); return; }

        // Read current count
        const tx0 = db.transaction(STORE_META, "readonly");
        const meta = tx0.objectStore(STORE_META);
        const getReq = meta.get(fileKey);
        getReq.onsuccess = () => {
          const countStr = getReq.result;
          const idx = (countStr == null) ? 0 : parseInt(countStr, 10);

          // Write chunk + update count
          const tx = db.transaction([STORE_CHUNKS, STORE_META], "readwrite");
          const chunks = tx.objectStore(STORE_CHUNKS);
          const meta2 = tx.objectStore(STORE_META);

          chunks.put(chunkB64, fileKey + "#" + idx);
          meta2.put(String(idx + 1), fileKey);

          tx.oncomplete = () => onOk();
          tx.onerror = () => onErr(String(tx.error || "tx error"));
        };
        getReq.onerror = () => onErr(String(getReq.error || "get meta error"));
      });
    }
    """
)
private external fun idbAppendChunkBase64(
    fileKey: String,
    chunkBase64: String,
    onOk: () -> Unit,
    onErr: (String) -> Unit
)

@JsFun(
    """
    (fileKey, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_CHUNKS = "chunks";
      const STORE_META = "meta";

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

      openDb((e, db) => {
        if (e) { onErr(e); return; }

        const tx0 = db.transaction(STORE_META, "readonly");
        const meta = tx0.objectStore(STORE_META);
        const getReq = meta.get(fileKey);

        getReq.onsuccess = () => {
          const countStr = getReq.result;
          const count = (countStr == null) ? 0 : parseInt(countStr, 10);
          if (!count || count <= 0) { onOk(null); return; }

          // Read all chunks into an array of base64 strings
          const chunksArr = new Array(count);
          let remaining = count;

          for (let i = 0; i < count; i++) {
            const tx = db.transaction(STORE_CHUNKS, "readonly");
            const chunks = tx.objectStore(STORE_CHUNKS);
            const r = chunks.get(fileKey + "#" + i);

            r.onsuccess = () => {
              chunksArr[i] = r.result || "";
              remaining--;
              if (remaining === 0) {
                // Concatenate base64 strings (still base64 per chunk)
                // We store raw base64 chunks with no separators; safe because base64 charset doesn't need delimiters
                onOk(chunksArr.join(""));
              }
            };
            r.onerror = () => onErr(String(r.error || "chunk read error"));
          }
        };

        getReq.onerror = () => onErr(String(getReq.error || "get meta error"));
      });
    }
    """
)
private external fun idbReadAllBase64(
    fileKey: String,
    onOk: (String?) -> Unit,
    onErr: (String) -> Unit
)

@JsFun(
    """
    (fileKey, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_META = "meta";

      function openDb(cb) {
        const req = indexedDB.open(DB_NAME, DB_VER);
        req.onupgradeneeded = () => {
          const db = req.result;
          if (!db.objectStoreNames.contains("chunks")) db.createObjectStore("chunks");
          if (!db.objectStoreNames.contains(STORE_META)) db.createObjectStore(STORE_META);
        };
        req.onsuccess = () => cb(null, req.result);
        req.onerror = () => cb(String(req.error || "open error"), null);
      }

      openDb((e, db) => {
        if (e) { onErr(e); return; }

        const tx = db.transaction(STORE_META, "readonly");
        const meta = tx.objectStore(STORE_META);
        const r = meta.get(fileKey);

        r.onsuccess = () => {
          const v = r.result;
          if (v == null) onOk(null);
          else onOk(parseInt(v, 10));
        };
        r.onerror = () => onErr(String(r.error || "meta read error"));
      });
    }
    """
)
private external fun idbGetChunkCount(
    fileKey: String,
    onOk: (Int?) -> Unit,
    onErr: (String) -> Unit
)

@JsFun(
    """
    (fileKey, onOk, onErr) => {
      const DB_NAME = "llamatik";
      const DB_VER = 1;
      const STORE_CHUNKS = "chunks";
      const STORE_META = "meta";

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

      openDb((e, db) => {
        if (e) { onErr(e); return; }

        // Get count first
        const tx0 = db.transaction(STORE_META, "readonly");
        const meta0 = tx0.objectStore(STORE_META);
        const getReq = meta0.get(fileKey);

        getReq.onsuccess = () => {
          const countStr = getReq.result;
          const count = (countStr == null) ? 0 : parseInt(countStr, 10);

          const tx = db.transaction([STORE_CHUNKS, STORE_META], "readwrite");
          const chunks = tx.objectStore(STORE_CHUNKS);
          const meta = tx.objectStore(STORE_META);

          for (let i = 0; i < count; i++) {
            chunks.delete(fileKey + "#" + i);
          }
          meta.delete(fileKey);

          tx.oncomplete = () => onOk(true);
          tx.onerror = () => onErr(String(tx.error || "delete tx error"));
        };

        getReq.onerror = () => onErr(String(getReq.error || "meta get error"));
      });
    }
    """
)
private external fun idbDeleteFile(
    fileKey: String,
    onOk: (Boolean) -> Unit,
    onErr: (String) -> Unit
)
