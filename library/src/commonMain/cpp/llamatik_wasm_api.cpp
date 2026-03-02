#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <limits>

#include "llama.h"

#if defined(__EMSCRIPTEN__)
#include <emscripten/emscripten.h>
#endif

// ------------------------------
// Globals / helpers
// ------------------------------

static llama_model *   g_model = nullptr;
static llama_context * g_ctx   = nullptr;

// New llama.cpp API: tokenize / pieces / eos are vocab-based
static const llama_vocab * g_vocab = nullptr;

static void free_if(void * p) {
    if (p) std::free(p);
}

// Fill llama_batch in a way that works with newer llama.cpp (no llama_batch_add/clear)
static void batch_set_tokens(llama_batch & batch, const llama_token * toks, int32_t n, int32_t start_pos) {
    batch.n_tokens = 0;

    for (int32_t i = 0; i < n; i++) {
        const int32_t j = batch.n_tokens;

        batch.token[j] = toks[i];
        batch.pos[j]   = start_pos + i;

        // single sequence id 0
        batch.seq_id[j][0] = 0;
        batch.n_seq_id[j]  = 1;

        // request logits only for the last token (we'll flip the last one later)
        batch.logits[j] = false;

        batch.n_tokens++;
    }
}

static void batch_set_one(llama_batch & batch, llama_token tok, int32_t pos) {
    batch.n_tokens = 1;
    batch.token[0] = tok;
    batch.pos[0]   = pos;

    batch.seq_id[0][0] = 0;
    batch.n_seq_id[0]  = 1;

    // request logits for this token
    batch.logits[0] = true;
}

// Greedy sample from current logits (avoids llama_sampler API drift)
static llama_token sample_greedy(llama_context * ctx, int32_t n_vocab) {
    const float * logits = llama_get_logits(ctx);
    if (!logits || n_vocab <= 0) return (llama_token)0;

    int32_t best_i = 0;
    float best_v = -std::numeric_limits<float>::infinity();

    for (int32_t i = 0; i < n_vocab; i++) {
        const float v = logits[i];
        if (v > best_v) {
            best_v = v;
            best_i = i;
        }
    }
    return (llama_token)best_i;
}

static void append_token_piece(std::string & out, const llama_vocab * vocab, llama_token id) {
    if (!vocab) return;

    // token -> utf8 piece (new API requires buffer)
    char buf[8 * 1024];
    const int32_t n_piece = llama_token_to_piece(
            vocab,
            id,
            buf,
            (int32_t)sizeof(buf),
            /* lstrip = */ 0,
            /* special = */ true
    );

    if (n_piece > 0) {
        out.append(buf, buf + n_piece);
    }
}

extern "C" {

EMSCRIPTEN_KEEPALIVE
int llamatik_llama_init_generate(const char * model_path) {
    if (!model_path || !*model_path) return 0;

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, mparams);
    if (!g_model) return 0;

    // New: keep vocab pointer (tokenize/pieces/eos)
    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params cparams = llama_context_default_params();

    // --- WASM memory pressure tuning (critical) ---
    // Keep these small in browser to avoid worker OOM / termination.
    cparams.n_ctx    = 2048; // was 4096
    cparams.n_batch  = 512;  // was 2048
    cparams.n_ubatch = 64;   // was 512

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) return 0;

    return 1;
}

EMSCRIPTEN_KEEPALIVE
char * llamatik_llama_generate(const char * prompt) {
    if (!g_ctx || !g_model || !g_vocab) {
        const char * msg = "Model not initialized";
        char * out = (char *) std::malloc(std::strlen(msg) + 1);
        std::strcpy(out, msg);
        return out;
    }

    std::string p = prompt ? prompt : "";

    // Tokenize (new signature: vocab first)
    std::vector<llama_token> tokens;
    tokens.resize(p.size() + 8);

    int32_t n = llama_tokenize(
            g_vocab,
            p.c_str(),
            (int32_t)p.size(),
            tokens.data(),
            (int32_t)tokens.size(),
            /* add_special = */ true,
            /* parse_special = */ true
    );

    if (n < 0) n = 0;
    tokens.resize((size_t)n);

    // Prepare batch and decode prompt
    llama_batch batch = llama_batch_init(512, 0, 1); // was 2048

    if (!tokens.empty()) {
        batch_set_tokens(batch, tokens.data(), (int32_t)tokens.size(), /*start_pos=*/0);
        batch.logits[batch.n_tokens - 1] = true; // need logits for sampling next token
    } else {
        // If empty prompt, still request logits by feeding BOS
        const llama_token bos = llama_vocab_bos(g_vocab);
        batch_set_one(batch, bos, 0);
        tokens.push_back(bos);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        const char * msg = "Decode failed";
        char * out = (char *) std::malloc(std::strlen(msg) + 1);
        std::strcpy(out, msg);
        return out;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);

    const int max_new_tokens = 256;
    std::string output;

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;

        append_token_piece(output, g_vocab, id);

        batch_set_one(batch, id, (int32_t)tokens.size() + i);

        if (llama_decode(g_ctx, batch) != 0) break;
    }

    llama_batch_free(batch);

    char * out = (char *) std::malloc(output.size() + 1);
    std::memcpy(out, output.data(), output.size());
    out[output.size()] = '\0';
    return out;
}

EMSCRIPTEN_KEEPALIVE
void llamatik_free_string(char * p) {
    free_if(p);
}

// ------------------------------
// Streaming (Emscripten)
// ------------------------------
#if defined(__EMSCRIPTEN__)

static void js_post_worker_error(const char * utf8) {
    if (!utf8) return;
    EM_ASM({
        try {
            const s = UTF8ToString($0);
            if (typeof self !== 'undefined' && self.postMessage) {
                self.postMessage({ type: "worker-error", message: s });
            } else if (typeof postMessage === 'function') {
                postMessage({ type: "worker-error", message: s });
            }
        } catch (e) {}
    }, utf8);
}

static void js_emit_token_utf8(const char * utf8) {
    if (!utf8) return;
    EM_ASM({
        try {
            const s = UTF8ToString($0);
            if (globalThis.__llamatik_stream_token) globalThis.__llamatik_stream_token(s);
        } catch (e) {}
    }, utf8);
}

static void js_emit_done() {
    EM_ASM({
        try {
            if (globalThis.__llamatik_stream_done) globalThis.__llamatik_stream_done();
        } catch (e) {}
    });
}

static void js_emit_error_utf8(const char * utf8) {
    if (!utf8) return;
    EM_ASM({
        try {
            const s = UTF8ToString($0);
            if (globalThis.__llamatik_stream_error) {
                globalThis.__llamatik_stream_error(s);
            } else {
                // fallback: tell worker/main thread directly
                if (typeof self !== 'undefined' && self.postMessage) {
                    self.postMessage({ type: "worker-error", message: s });
                } else if (typeof postMessage === 'function') {
                    postMessage({ type: "worker-error", message: s });
                } else {
                    console.error("llamatik error:", s);
                }
            }
        } catch (e) {}
    }, utf8);
}
#endif

EMSCRIPTEN_KEEPALIVE
void llamatik_llama_generate_stream(const char * prompt) {
#if !defined(__EMSCRIPTEN__)
    (void)prompt;
    return;
#else
    if (!g_ctx || !g_model || !g_vocab) {
        js_emit_error_utf8("Model not initialized");
        js_emit_done();
        return;
    }

    std::string p = prompt ? prompt : "";

    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(p.size() + 8);

    int32_t n = llama_tokenize(
        g_vocab,
        p.c_str(),
        (int32_t)p.size(),
        tokens.data(),
        (int32_t)tokens.size(),
        /* add_special = */ true,
        /* parse_special = */ true
    );

    if (n < 0) n = 0;
    tokens.resize((size_t)n);

    llama_batch batch = llama_batch_init(512, 0, 1); // was 2048

    if (!tokens.empty()) {
        batch_set_tokens(batch, tokens.data(), (int32_t)tokens.size(), /*start_pos=*/0);
        batch.logits[batch.n_tokens - 1] = true;
    } else {
        const llama_token bos = llama_vocab_bos(g_vocab);
        batch_set_one(batch, bos, 0);
        tokens.push_back(bos);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        js_emit_error_utf8("Decode failed");
        js_emit_done();
        return;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);

    const int max_new_tokens = 256;

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;

        // token -> piece -> emit
        char buf[8 * 1024];
        const int32_t n_piece = llama_token_to_piece(
            g_vocab,
            id,
            buf,
            (int32_t)sizeof(buf),
            /* lstrip = */ 0,
            /* special = */ true
        );

        if (n_piece > 0) {
            // Ensure null-terminated for UTF8ToString
            if (n_piece < (int32_t)sizeof(buf)) buf[n_piece] = '\0';
            else buf[sizeof(buf) - 1] = '\0';
            js_emit_token_utf8(buf);
        }

        batch_set_one(batch, id, (int32_t)tokens.size() + i);

        if (llama_decode(g_ctx, batch) != 0) {
            js_emit_error_utf8("Decode failed during generation");
            break;
        }
    }

    llama_batch_free(batch);
    js_emit_done();
#endif
}

} // extern "C"
