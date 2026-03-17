#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <limits>
#include <algorithm>

#include "llama.h"

#if defined(__EMSCRIPTEN__)
#include <emscripten/emscripten.h>
#endif

// ------------------------------
// Globals / helpers
// ------------------------------

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;

static int32_t g_cur_pos = 0;
static bool g_session_has_prompt = false;

static void free_if(void * p) {
    if (p) std::free(p);
}

static void destroy_runtime() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    g_cur_pos = 0;
    g_session_has_prompt = false;
}

static void reset_session_state() {
    if (!g_ctx) return;
    llama_memory_clear(llama_get_memory(g_ctx), false);
    g_cur_pos = 0;
    g_session_has_prompt = false;
}

static int32_t tokenize_dynamic(
        const llama_vocab * vocab,
        const std::string & text,
        std::vector<llama_token> & out,
        bool add_special,
        bool parse_special
) {
    if (!vocab) return 0;

    int32_t cap = std::max<int32_t>(64, (int32_t) text.size() + 16);
    out.resize((size_t) cap);

    int32_t n = llama_tokenize(
            vocab,
            text.c_str(),
            (int32_t) text.size(),
            out.data(),
            (int32_t) out.size(),
            add_special,
            parse_special
    );

    if (n < 0) {
        cap = -n;
        out.resize((size_t) cap);
        n = llama_tokenize(
                vocab,
                text.c_str(),
                (int32_t) text.size(),
                out.data(),
                (int32_t) out.size(),
                add_special,
                parse_special
        );
    }

    if (n < 0) return 0;
    out.resize((size_t) n);
    return n;
}

static void batch_set_tokens(llama_batch & batch, const llama_token * toks, int32_t n, int32_t start_pos) {
    batch.n_tokens = 0;

    for (int32_t i = 0; i < n; i++) {
        const int32_t j = batch.n_tokens;

        batch.token[j] = toks[i];
        batch.pos[j]   = start_pos + i;

        batch.seq_id[j][0] = 0;
        batch.n_seq_id[j]  = 1;
        batch.logits[j]    = false;

        batch.n_tokens++;
    }
}

static void batch_set_one(llama_batch & batch, llama_token tok, int32_t pos) {
    batch.n_tokens = 1;
    batch.token[0] = tok;
    batch.pos[0]   = pos;

    batch.seq_id[0][0] = 0;
    batch.n_seq_id[0]  = 1;
    batch.logits[0]    = true;
}

static llama_token sample_greedy(llama_context * ctx, int32_t n_vocab) {
    const float * logits = llama_get_logits(ctx);
    if (!logits || n_vocab <= 0) return (llama_token) 0;

    int32_t best_i = 0;
    float best_v = -std::numeric_limits<float>::infinity();

    for (int32_t i = 0; i < n_vocab; i++) {
        const float v = logits[i];
        if (v > best_v) {
            best_v = v;
            best_i = i;
        }
    }
    return (llama_token) best_i;
}

static void append_token_piece(std::string & out, const llama_vocab * vocab, llama_token id) {
    if (!vocab) return;

    char buf[8 * 1024];
    const int32_t n_piece = llama_token_to_piece(
            vocab,
            id,
            buf,
            (int32_t) sizeof(buf),
            0,
            true
    );

    if (n_piece > 0) {
        out.append(buf, buf + n_piece);
    }
}

static bool feed_tokens(const std::vector<llama_token> & tokens) {
    if (!g_ctx || !g_vocab) return false;
    if (tokens.empty()) return true;

    const int32_t n_ctx = llama_n_ctx(g_ctx);
    if (g_cur_pos + (int32_t) tokens.size() >= n_ctx - 1) {
        return false;
    }

    llama_batch batch = llama_batch_init(std::max<int32_t>(1, (int32_t) tokens.size()), 0, 1);
    batch_set_tokens(batch, tokens.data(), (int32_t) tokens.size(), g_cur_pos);
    batch.logits[batch.n_tokens - 1] = true;

    const int rc = llama_decode(g_ctx, batch);
    llama_batch_free(batch);

    if (rc != 0) return false;

    g_cur_pos += (int32_t) tokens.size();
    return true;
}

static char * make_c_string(const std::string & s) {
    char * out = (char *) std::malloc(s.size() + 1);
    if (!out) return nullptr;
    std::memcpy(out, s.data(), s.size());
    out[s.size()] = '\0';
    return out;
}

#if defined(__EMSCRIPTEN__)
static void js_emit_done() {
    EM_ASM({
        try {
            if (globalThis.__llamatik_stream_done) {
                globalThis.__llamatik_stream_done();
            }
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
            } else if (typeof self !== "undefined" && self.postMessage) {
                self.postMessage({ type: "worker-error", message: s });
            } else if (typeof postMessage === "function") {
                postMessage({ type: "worker-error", message: s });
            } else {
                console.error("llamatik error:", s);
            }
        } catch (e) {}
    }, utf8);
}

static void js_emit_token_utf8(const char * utf8) {
    if (!utf8) return;
    EM_ASM({
        try {
            const s = UTF8ToString($0);
            if (globalThis.__llamatik_stream_token) {
                globalThis.__llamatik_stream_token(s);
            }
        } catch (e) {}
    }, utf8);
}
#endif

extern "C" {

EMSCRIPTEN_KEEPALIVE
int llamatik_llama_init_generate(const char * model_path) {
    if (!model_path || !*model_path) return 0;

    destroy_runtime();
    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, mparams);
    if (!g_model) return 0;

    g_vocab = llama_model_get_vocab(g_model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx    = 4096;
    cparams.n_batch  = 256;
    cparams.n_ubatch = 64;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        destroy_runtime();
        return 0;
    }

    g_cur_pos = 0;
    g_session_has_prompt = false;
    return 1;
}

EMSCRIPTEN_KEEPALIVE
void llamatik_llama_session_reset() {
    reset_session_state();
}

EMSCRIPTEN_KEEPALIVE
int llamatik_llama_session_prompt(const char * prompt) {
    if (!g_ctx || !g_model || !g_vocab) return 0;

    reset_session_state();

    std::string p = prompt ? prompt : "";
    std::vector<llama_token> tokens;
    const int32_t n = tokenize_dynamic(g_vocab, p, tokens, true, true);
    if (n <= 0) return 0;

    if (!feed_tokens(tokens)) return 0;

    g_session_has_prompt = true;
    return 1;
}

EMSCRIPTEN_KEEPALIVE
int llamatik_llama_session_continue(const char * prompt) {
    if (!g_ctx || !g_model || !g_vocab) return 0;
    if (!g_session_has_prompt) {
        return llamatik_llama_session_prompt(prompt);
    }

    std::string p = prompt ? prompt : "";
    std::vector<llama_token> tokens;
    const int32_t n = tokenize_dynamic(g_vocab, p, tokens, false, true);
    if (n <= 0) return 0;

    if (!feed_tokens(tokens)) return 0;

    return 1;
}

EMSCRIPTEN_KEEPALIVE
char * llamatik_llama_generate(const char * prompt) {
    if (!g_ctx || !g_model || !g_vocab) {
        return make_c_string("Model not initialized");
    }

    if (!llamatik_llama_session_prompt(prompt)) {
        return make_c_string("Prompt decode failed");
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_ctx   = llama_n_ctx(g_ctx);

    const int max_new_tokens = 256;
    std::string output;

    llama_batch batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;
        if (g_cur_pos >= n_ctx - 1) break;

        append_token_piece(output, g_vocab, id);

        batch_set_one(batch, id, g_cur_pos);
        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
        g_cur_pos++;
    }

    llama_batch_free(batch);
    return make_c_string(output);
}

EMSCRIPTEN_KEEPALIVE
char * llamatik_llama_generate_continue(const char * prompt) {
    if (!g_ctx || !g_model || !g_vocab) {
        return make_c_string("Model not initialized");
    }

    const int ok = g_session_has_prompt
            ? llamatik_llama_session_continue(prompt)
            : llamatik_llama_session_prompt(prompt);

    if (!ok) {
        return make_c_string("Continue decode failed");
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_ctx   = llama_n_ctx(g_ctx);

    const int max_new_tokens = 256;
    std::string output;

    llama_batch batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;
        if (g_cur_pos >= n_ctx - 1) break;

        append_token_piece(output, g_vocab, id);

        batch_set_one(batch, id, g_cur_pos);
        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
        g_cur_pos++;
    }

    llama_batch_free(batch);
    g_session_has_prompt = true;
    return make_c_string(output);
}

EMSCRIPTEN_KEEPALIVE
void llamatik_free_string(char * p) {
    free_if(p);
}

EMSCRIPTEN_KEEPALIVE
void llamatik_llama_generate_stream(const char * prompt) {
#if !defined(__EMSCRIPTEN__)
    (void) prompt;
    return;
#else
    if (!g_ctx || !g_model || !g_vocab) {
        js_emit_error_utf8("Model not initialized");
        js_emit_done();
        return;
    }

    if (!llamatik_llama_session_prompt(prompt)) {
        js_emit_error_utf8("Prompt decode failed");
        js_emit_done();
        return;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_ctx   = llama_n_ctx(g_ctx);

    const int max_new_tokens = 256;
    llama_batch batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;
        if (g_cur_pos >= n_ctx - 1) break;

        char buf[8 * 1024];
        const int32_t n_piece = llama_token_to_piece(
            g_vocab,
            id,
            buf,
            (int32_t) sizeof(buf),
            0,
            true
        );

        if (n_piece > 0) {
            if (n_piece < (int32_t) sizeof(buf)) buf[n_piece] = '\0';
            else buf[sizeof(buf) - 1] = '\0';
            js_emit_token_utf8(buf);
        }

        batch_set_one(batch, id, g_cur_pos);
        if (llama_decode(g_ctx, batch) != 0) {
            js_emit_error_utf8("Decode failed during generation");
            break;
        }
        g_cur_pos++;
    }

    llama_batch_free(batch);
    g_session_has_prompt = true;
    js_emit_done();
#endif
}

EMSCRIPTEN_KEEPALIVE
void llamatik_llama_generate_continue_stream(const char * prompt) {
#if !defined(__EMSCRIPTEN__)
    (void) prompt;
    return;
#else
    if (!g_ctx || !g_model || !g_vocab) {
        js_emit_error_utf8("Model not initialized");
        js_emit_done();
        return;
    }

    const int ok = g_session_has_prompt
        ? llamatik_llama_session_continue(prompt)
        : llamatik_llama_session_prompt(prompt);

    if (!ok) {
        js_emit_error_utf8("Continue decode failed");
        js_emit_done();
        return;
    }

    const int32_t n_vocab = llama_vocab_n_tokens(g_vocab);
    const llama_token eos = llama_vocab_eos(g_vocab);
    const int32_t n_ctx   = llama_n_ctx(g_ctx);

    const int max_new_tokens = 256;
    llama_batch batch = llama_batch_init(1, 0, 1);

    for (int i = 0; i < max_new_tokens; i++) {
        const llama_token id = sample_greedy(g_ctx, n_vocab);
        if (id == eos) break;
        if (g_cur_pos >= n_ctx - 1) break;

        char buf[8 * 1024];
        const int32_t n_piece = llama_token_to_piece(
            g_vocab,
            id,
            buf,
            (int32_t) sizeof(buf),
            0,
            true
        );

        if (n_piece > 0) {
            if (n_piece < (int32_t) sizeof(buf)) buf[n_piece] = '\0';
            else buf[sizeof(buf) - 1] = '\0';
            js_emit_token_utf8(buf);
        }

        batch_set_one(batch, id, g_cur_pos);
        if (llama_decode(g_ctx, batch) != 0) {
            js_emit_error_utf8("Decode failed during generation");
            break;
        }
        g_cur_pos++;
    }

    llama_batch_free(batch);
    g_session_has_prompt = true;
    js_emit_done();
#endif
}

} // extern "C"
