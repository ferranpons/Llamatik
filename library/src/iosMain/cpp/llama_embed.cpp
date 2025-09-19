#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <vector>
#include <string>
#include <cstdint>

#ifdef __APPLE__
#include <TargetConditionals.h>
#else
#define TARGET_OS_SIMULATOR 0
#endif

// ===================== Debug logging =====================

static bool g_dbg_enabled = true;

static inline void dbg_init() {
    const char *e = std::getenv("LLAMA_WRAPPER_DEBUG");
    // g_dbg_enabled = (e && *e && std::strcmp(e, "0") != 0);
}

#define DBG(fmt, ...) \
    do { if (g_dbg_enabled) std::fprintf(stderr, "[llama-wrapper] " fmt "\n", ##__VA_ARGS__); } while (0)

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
    if ((int) tokens.size() <= n_ctx - reserve_tail) return;
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

// Build a plain prompt (fallback when chat template is unavailable)
static std::string build_plain_chat_prompt(const char *system, const char *context, const char *user) {
    const char *sys = (system  && *system)  ? system  : "You are a concise, helpful assistant.";
    const char *ctx = (context && *context) ? context : "";
    const char *usr = (user    && *user)    ? user    : "";

    std::string p;
    p.reserve(256 + std::strlen(sys) + std::strlen(ctx) + std::strlen(usr));
    p += "System: ";
    p += sys;
    if (*ctx) {
        p += "\n\nContext:\n";
        p += ctx;
    }
    p += "\n\nUser: ";
    p += usr;
    p += "\nAssistant (reply in plain text, no angle-bracket tags):";
    return p;
}

// Stub: no chat-template support in your headers
static bool apply_chat_template_if_available(const char * /*system*/, const char * /*user*/, std::string & /*out_str*/) {
    return false;
}

extern "C" {

// ===================== Embeddings =====================

bool llama_embed_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    model = load_model_with_fallback(model_path);
    if (!model) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;
    ctx_params.n_ctx      = 2048;

    ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    embedding_size = llama_model_n_embd(model);
    DBG("embed: embedding_size = %d", embedding_size);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    std::vector<llama_token> tokens(1024);
    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /* add_bos       */ true,
            /* parse_special */ false);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        DBG("embed: bad token count: %d (n_ctx=%d)", n_tokens, llama_n_ctx(ctx));
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
        DBG("embed: llama_decode failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        DBG("embed: llama_get_embeddings_seq returned null");
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t) dim);
    if (!out) {
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, embedding, sizeof(float) * (size_t) dim);

    llama_batch_free(batch);
    return out;
}

int  llama_embedding_size()           { return llama_model_n_embd(model); }
void llama_free_embedding(float *ptr) { if (ptr) std::free(ptr); }

void llama_embed_free() {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx   = nullptr;
    model = nullptr;

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
    ctx_params.n_ctx      = 4096;

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
        DBG("generate: using plain fallback prompt");
    } else {
        DBG("generate: using chat template prompt");
    }

    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) {
        DBG("generate: tokenize failed (n=%d)", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int) n_ctx - 8) {
        truncate_to_ctx(tokens, (int) n_ctx, 8);
        DBG("generate: prompt truncated to %zu tokens", tokens.size());
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
        DBG("generate: prefill llama_decode failed");
        llama_batch_free(batch);
        return nullptr;
    }

    // 2) Sampler chain (conservative settings)
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(0));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.50f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // Optional: bias down a literal "<" to discourage taggy outputs — using the correct API.
    {
        std::vector<llama_token> t(8);
        int nn = llama_tokenize(v, "<", 1, t.data(), (int)t.size(), /*add_bos*/ false, /*special*/ true);
        if (nn > 0) {
            llama_logit_bias lb[1];
            lb[0].token = t[0];
            lb[0].bias  = -2.0f; // downweight "<"
            const int32_t n_vocab = llama_n_vocab(llama_model_get_vocab(gen_model));
            llama_sampler *bias = llama_sampler_init_logit_bias(
                    n_vocab,
                    /*n_logit_bias*/ 1,
                    /*logit_bias*/    lb
            );
            if (bias) llama_sampler_chain_add(sampler, bias);
        }
    }

    // 3) Decode loop
    std::vector<llama_token> out;
    const int max_new_tokens = 640;
    int cur_pos = batch.n_tokens;

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
                    std::strcmp(piece, "</s>") == 0) {
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
        if (n > 0) text.append(buf, n);
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

static std::string build_clean_prompt(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    std::string sys = (system_prompt && *system_prompt) ? system_prompt : "You are a helpful assistant. Answer in plain text.";
    std::string ctx = (context_block && *context_block) ? context_block : "";
    std::string usr = (user_prompt && *user_prompt) ? user_prompt : "";

    // NO angle-bracket placeholders, NO meta-instructions inline.
    // Finish with "Assistant:" so generation starts there.
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
    // If your lower-level generate() supports stop strings, pass e.g. ["\nUser:", "\nSystem:", "\nContext:"] there.
    char *out = llama_generate(prompt.c_str());
    if (!out) return nullptr;

    // Trim any trailing scaffolding if the model produced it (belt & suspenders)
    // e.g., cut at "\nUser:" if it appears in output.
    const char *stop1 = strstr(out, "\nUser:");
    if (stop1) *const_cast<char*>(stop1) = '\0';

    return out;
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