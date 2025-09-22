#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <vector>
#include <string>
#include <cstdint>
#include <algorithm>

#ifdef __APPLE__
#include <TargetConditionals.h>
#else
#define TARGET_OS_SIMULATOR 0
#endif

// ===================== Debug logging =====================

static bool g_enable_debug = false;

static void dbg_init() {
    if (g_enable_debug) return;
    const char *e = std::getenv("LLAMATIK_DEBUG");
    g_enable_debug = (e && std::strcmp(e, "0") != 0);
}

static void dbg_printf(const char *fmt, ...) {
    if (!g_enable_debug) return;
    va_list args;
    va_start(args, fmt);
    std::vfprintf(stderr, fmt, args);
    std::fprintf(stderr, "\n");
    va_end(args);
}

#define DBG(fmt, ...) \
    do { dbg_printf("[ios] " fmt, ##__VA_ARGS__); } while (0)

// ===================== Global state =====================

static struct llama_model   *model      = nullptr; // embeddings model
static struct llama_context *ctx        = nullptr;
static int                   embedding_size = 0;

static struct llama_model   *gen_model  = nullptr; // generation model
static struct llama_context *gen_ctx    = nullptr;

static bool g_backend_inited = false;

// ===================== Helpers =====================

static int tokenize_with_retry(const llama_vocab *vocab,
        const char *text,
        std::vector<llama_token> &tokens,
        bool add_bos,
        bool parse_special) {
    if (!text) return 0;
    const int text_len = (int) std::strlen(text);

    int n = llama_tokenize(vocab, text, text_len,
            tokens.data(),
            (int) tokens.size(),
            add_bos, parse_special);
    if (n < 0) {
        const int need = -n;
        if (need > 0) {
            tokens.resize(need);
            n = llama_tokenize(vocab, text, text_len,
                    tokens.data(),
                    (int) tokens.size(),
                    add_bos, parse_special);
        }
    }
    return n;
}

static void truncate_to_ctx(std::vector<llama_token> &tokens, int n_ctx, int reserve_tail) {
    if ((int)tokens.size() <= n_ctx - reserve_tail) return;
    const int keep = n_ctx - reserve_tail;
    std::vector<llama_token> out;
    out.reserve(keep);
    out.insert(out.end(), tokens.end() - keep, tokens.end());
    tokens.swap(out);
}

static llama_model *load_model_with_fallback(const char *path) {
    llama_model_params mp = llama_model_default_params();

#if TARGET_OS_SIMULATOR
    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
#endif

    llama_model *m = llama_model_load_from_file(path, mp);
    if (m) return m;

    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;

    return llama_model_load_from_file(path, mp);
}

// ----- lightweight "plain chat" prompt (fallback if no chat template present) -----

static std::string build_plain_chat_prompt(const std::string &system_msg,
        const std::string &context_block,
        const std::string &user_msg) {
    std::string p;
    p.reserve(system_msg.size() + context_block.size() + user_msg.size() + 128);
    p += "System: ";   p += system_msg; p += "\n\n";
    if (!context_block.empty()) { p += "Context:\n"; p += context_block; p += "\n\n"; }
    p += "User: ";     p += user_msg;   p += "\n";
    p += "Assistant:";
    return p;
}

// Try to use chat template (if model has it). If used, return true and fill `wrapped` with the prompt.
// If no template available, return false.
static bool apply_chat_template_if_available(const char *system_msg,
        const char *user_msg,
        std::string &wrapped) {
    // For simplicity here we always return false; in your setup you may have
    // llama_chat_apply_template available to format roles for chatty models.
    (void)system_msg; (void)user_msg;
    return false;
}

// ===================== Embeddings =====================

extern "C" {

bool llama_embed_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    model = load_model_with_fallback(model_path);
    if (!model) return false;

    llama_context_params cp = llama_context_default_params();
    cp.embeddings = true;
    cp.n_ctx      = 2048;

    ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    embedding_size = llama_model_n_embd(model);
    DBG("embed: dim=%d", embedding_size);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    std::vector<llama_token> tokens(1024);
    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        DBG("embed: tokenize fail/too long n=%d ctx=%u", n_tokens, (unsigned)llama_n_ctx(ctx));
        return nullptr;
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;
    }

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        DBG("embed: decode failed");
        return nullptr;
    }

    const float *emb = llama_get_embeddings_seq(ctx, 0);
    if (!emb) {
        llama_batch_free(batch);
        DBG("embed: embeddings null");
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, emb, sizeof(float) * (size_t)dim);
    llama_batch_free(batch);
    return out;
}

int   llama_embedding_size()          { return llama_model_n_embd(model); }
void  llama_free_embedding(float *p)  { if (p) std::free(p); }

void llama_embed_free() {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr; model = nullptr;

    if (!gen_ctx && !gen_model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// ===================== Text Generation =====================

bool llama_generate_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    gen_model = load_model_with_fallback(model_path);
    if (!gen_model) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false;
    ctx_params.n_ctx      = 8192;   // <- larger context (was 4096)

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return false;
    }
    DBG("generate: n_ctx = %u", (unsigned)llama_n_ctx(gen_ctx));
    return true;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    llama_kv_self_clear(gen_ctx);

    // 1) Build the right prompt
    std::string wrapped;
    if (!apply_chat_template_if_available(
            "You are a helpful assistant. Answer in plain text. Do NOT output XML/HTML-like tags such as <admin>, <help>, <info>.",
            prompt,
            wrapped)) {
        wrapped = build_plain_chat_prompt(
                "You are a helpful assistant. Answer in plain text. Do NOT output XML/HTML-like tags such as <admin>, <help>, <info>.",
                "",
                prompt);
        DBG("generate: using plain prompt");
    }

    // 2) Tokenize + prompt decode
    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) return nullptr;
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int) n_ctx - 8) {
        truncate_to_ctx(tokens, (int) n_ctx, 8);
        DBG("generate: prompt truncated");
    }

    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    batch.n_tokens = (int)tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        DBG("generate: decode prompt failed");
        return nullptr;
    }

    // Sampler: light defaults
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 3) Decode loop
    std::vector<llama_token> out;
    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::max(remaining_ctx, 2048);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) {
            DBG("generate: hit EOS");
            break;
        }

        // Early stop on common “end” pieces
        char piece[64];
        int nn = llama_token_to_piece(v, tok, piece, (int)sizeof(piece), 0, /*special*/ true);
        if (nn > 0) {
            if (nn >= (int)sizeof(piece)) piece[sizeof(piece)-1] = '\0';
            else piece[nn] = '\0';
            if (std::strcmp(piece, "<|eot_id|>") == 0 ||
                    std::strcmp(piece, "<end_of_turn>") == 0 ||
                    std::strcmp(piece, "</s>") == 0 ||
                    std::strcmp(piece, "<start_of_turn>") == 0) {
                DBG("generate: hit EOT piece: %s", piece);
                break;
            }
        }

        llama_sampler_accept(sampler, tok);
        out.push_back(tok);

        if (cur_pos >= (int)n_ctx) {
            DBG("generate: context full at %d positions", cur_pos);
            break;
        }

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens      = 1;
        step.token[0]      = tok;
        step.pos[0]        = cur_pos;
        step.n_seq_id[0]   = 1;
        step.seq_id[0][0]  = 0;
        step.logits[0]     = true;

        if (llama_decode(gen_ctx, step) != 0) {
            DBG("generate: decode step failed at pos=%d", cur_pos);
            llama_batch_free(step);
            break;
        }
        cur_pos++;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    // 4) Detokenize
    std::string text;
    char buf[8192];
    for (llama_token t : out) {
        int n = llama_token_to_piece(v, t, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n > 0) {
            if (n >= (int)sizeof(buf)) buf[sizeof(buf)-1] = '\0';
            text.append(buf, n);
        }
    }

    // Trim leading lines like "<foo>\n" if any slipped through (best-effort)
    if (!text.empty()) {
        size_t i = 0;
        while (i + 3 < text.size() && text[i] == '<') {
            size_t nl = text.find('\n', i);
            if (nl == std::string::npos) break;
            if (nl - i <= 20 && text[nl - 1] == '>') i = nl + 1;
            else break;
        }
        if (i > 0) text.erase(0, i);
    }

    char *result = (char *) std::malloc(text.size() + 1);
    if (!result) return nullptr;
    std::memcpy(result, text.c_str(), text.size() + 1);
    return result;
}

// Clean prompt helper used by chat wrapper
static std::string build_clean_prompt(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    std::string sys = system_prompt ? system_prompt : "";
    std::string ctx = context_block ? context_block : "";
    std::string usr = user_prompt   ? user_prompt   : "";
    std::string p;
    p.reserve(sys.size() + ctx.size() + usr.size() + 128);
    p += "System: ";   p += sys; p += "\n\n";
    if (!ctx.empty()) {
        p += "Context:\n"; p += ctx; p += "\n\n";
    }
    p += "User: "; p += usr; p += "\n";
    p += "Assistant:";
    return p;
}

char *llama_generate_chat(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    std::string prompt = build_clean_prompt(system_prompt, context_block, user_prompt);

    // OPTIONAL: add lightweight post-stop to avoid the model continuing back into a new "User:" cue.
    const llama_vocab *v = llama_model_get_vocab(gen_model);
    (void)v; // not used here; kept for parity
    return llama_generate(prompt.c_str());
}

// ===================== Streaming APIs (iOS) =====================

typedef void (*llm_on_delta)(const char *utf8, void *user);
typedef void (*llm_on_done)(void *user);
typedef void (*llm_on_error)(const char *utf8, void *user);

static void emit_piece(const llama_vocab *v, llama_token tok, bool allow_special, std::string &accum, llm_on_delta on_delta, void *user) {
    char buf[256];
    int n = llama_token_to_piece(v, tok, buf, (int)sizeof(buf), 0, allow_special);
    if (n > 0) {
        if (n >= (int)sizeof(buf)) buf[sizeof(buf)-1] = '\0';
        else buf[n] = '\0';
        accum.append(buf);
        if (on_delta) on_delta(buf, user);
    }
}

void llama_generate_stream(const char *prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    if (!gen_ctx || !gen_model || !prompt) { if (on_error) on_error("generator not ready", user); return; }

    llama_kv_self_clear(gen_ctx);

    std::string wrapped;
    if (!apply_chat_template_if_available(
            "You are a helpful assistant. Answer in plain text. Do NOT output XML/HTML-like tags such as <admin>, <help>, <info>.",
            prompt,
            wrapped)) {
        wrapped = build_plain_chat_prompt(
                "You are a helpful assistant. Answer in plain text. Do NOT output XML/HTML-like tags such as <admin>, <help>, <info>.",
                "",
                prompt);
    }

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
            wrapped.c_str(),
            tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) { if (on_error) on_error("tokenize failed", user); return; }
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int)n_ctx - 8) {
        truncate_to_ctx(tokens, (int)n_ctx, 8);
    }

    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    batch.n_tokens = (int)tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        if (on_error) on_error("decode failed", user);
        return;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        if (on_error) on_error("sampler init failed", user);
        return;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const llama_vocab *v = llama_model_get_vocab(gen_model);

    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::max(remaining_ctx, 2048);

    std::string assembled;
    assembled.reserve(4096);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) break;

        char piece[64];
        int nn = llama_token_to_piece(v, tok, piece, (int)sizeof(piece), 0, /*special*/ true);
        if (nn > 0) {
            if (nn >= (int)sizeof(piece)) piece[sizeof(piece)-1] = '\0';
            else piece[nn] = '\0';
            if (std::strcmp(piece, "<|eot_id|>") == 0 ||
                    std::strcmp(piece, "<end_of_turn>") == 0 ||
                    std::strcmp(piece, "</s>") == 0 ||
                    std::strcmp(piece, "<start_of_turn>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);
        emit_piece(v, tok, /*allow_special*/ false, assembled, on_delta, user);

        if (cur_pos >= (int)n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens      = 1;
        step.token[0]      = tok;
        step.pos[0]        = cur_pos;
        step.n_seq_id[0]   = 1;
        step.seq_id[0][0]  = 0;
        step.logits[0]     = true;
        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            break;
        }
        cur_pos++;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    size_t p;
    if ((p = assembled.find("<start_of_turn>")) != std::string::npos) assembled.resize(p);
    if ((p = assembled.find("QUESTION:"))       != std::string::npos) assembled.resize(p);
    if ((p = assembled.find("USER:"))           != std::string::npos) assembled.resize(p);

    if (on_done) on_done(user);
}

void llama_generate_chat_stream(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    std::string prompt = build_clean_prompt(system_prompt ? system_prompt : "",
            context_block ? context_block : "",
            user_prompt ? user_prompt : "");
    llama_generate_stream(prompt.c_str(), on_delta, on_done, on_error, user);
}

void llama_generate_free() {
    if (gen_ctx)   llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx   = nullptr;
    gen_model = nullptr;

    if (!ctx && !model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

} // extern "C"