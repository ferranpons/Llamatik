#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#include <filesystem>
#include <cstdio>

#include "../c_interop/include/llama_embed.h"
#include "llama.h"

// ============== Shared state ==============

static struct llama_model   *model      = nullptr;
static struct llama_context *ctx        = nullptr;
static int embedding_size               = 0;

static struct llama_model   *gen_model  = nullptr;
static struct llama_context *gen_ctx    = nullptr;

static bool g_backend_inited = false;

// ============== Utils ==============

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

static char *dup_cstr(const std::string &s) {
    char *p = (char *) std::malloc(s.size() + 1);
    if (!p) return nullptr;
    std::memcpy(p, s.c_str(), s.size() + 1);
    return p;
}

// ============== C API ==============

extern "C" {

// -------- Embeddings --------

bool llama_embed_init(const char *model_path) {
    std::fprintf(stderr, "[llama_embed] init\n");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();

    if (model_path && std::filesystem::exists(model_path)) {
        std::error_code ec;
        auto sz = std::filesystem::file_size(model_path, ec);
        std::fprintf(stderr, "[llama_embed] model: %s size=%ju\n",
                model_path, (uintmax_t)(ec ? 0 : sz));
    }

    model = llama_model_load_from_file(model_path, model_params);
    if (!model) {
        std::fprintf(stderr, "[llama_embed] load failed\n");
        return false;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;
    ctx_params.n_ctx      = 2048;

    ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        std::fprintf(stderr, "[llama_embed] ctx failed\n");
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    embedding_size = llama_model_n_embd(model);
    std::fprintf(stderr, "[llama_embed] ready dim=%d\n", embedding_size);
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
            /*parse_special*/ false
    );
    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        std::fprintf(stderr, "[llama_embed] tokenize fail/too long n=%d ctx=%d\n",
                n_tokens, llama_n_ctx(ctx));
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
        std::fprintf(stderr, "[llama_embed] decode failed\n");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        std::fprintf(stderr, "[llama_embed] get_embeddings null\n");
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        std::fprintf(stderr, "[llama_embed] malloc fail\n");
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, embedding, sizeof(float) * (size_t)dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size(void) { return llama_model_n_embd(model); }

void llama_free_embedding(float *ptr) { if (ptr) std::free(ptr); }

void llama_embed_free(void) {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr;
    model = nullptr;

    if (!gen_ctx && !gen_model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// -------- Generation --------

bool llama_generate_init(const char *model_path) {
    std::fprintf(stderr, "[llama_gen] init\n");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();
    gen_model = llama_model_load_from_file(model_path, model_params);
    if (!gen_model) {
        std::fprintf(stderr, "[llama_gen] load failed\n");
        return false;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false;
    ctx_params.n_ctx      = 4096;

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    if (!gen_ctx) {
        std::fprintf(stderr, "[llama_gen] ctx failed\n");
        llama_model_free(gen_model);
        gen_model = nullptr;
        return false;
    }
    return true;
}

static char *generate_from_prompt(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    llama_kv_self_clear(gen_ctx);

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(gen_model),
            prompt,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ true
    );
    if (n_tokens <= 0) {
        std::fprintf(stderr, "[llama_gen] tokenize failed n=%d\n", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    const int n_ctx = (int)llama_n_ctx(gen_ctx);
    if (n_tokens > n_ctx - 8) {
        truncate_to_ctx(tokens, n_ctx, 8);
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
        std::fprintf(stderr, "[llama_gen] decode failed on prompt\n");
        llama_batch_free(batch);
        return nullptr;
    }

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

    std::vector<llama_token> out_tokens;
    const int max_new_tokens = 640;
    int cur_pos = batch.n_tokens;

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (tok == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

        // Early stop on some common chat EOT markers
        char piece[64];
        int nn = llama_token_to_piece(
                llama_model_get_vocab(gen_model), tok, piece, (int)sizeof(piece), 0, 0);
        if (nn > 0) {
            if (nn >= (int)sizeof(piece)) piece[sizeof(piece) - 1] = '\0';
            else                          piece[nn] = '\0';
            if (std::strcmp(piece, "<end_of_turn>") == 0 ||
                    std::strcmp(piece, "<|eot_id|>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);
        out_tokens.push_back(tok);

        if (cur_pos >= n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens = 1;
        step.token[0] = tok;
        step.pos[0]   = cur_pos++;
        step.n_seq_id[0]  = 1;
        step.seq_id[0][0] = 0;
        step.logits[0]    = true;

        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            break;
        }
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string text;
    text.reserve(out_tokens.size() * 4);
    char buf[8192];
    for (llama_token t : out_tokens) {
        int n = llama_token_to_piece(
                llama_model_get_vocab(gen_model), t, buf, (int)sizeof(buf), 0, false);
        if (n > 0) text.append(buf, n);
    }
    return dup_cstr(text);
}

char *llama_generate(const char *prompt) {
    return generate_from_prompt(prompt);
}

char *llama_generate_with_context(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    // Simple concatenation; adjust to your chat template if needed.
    std::string prompt;
    if (system_prompt && *system_prompt) {
        prompt += system_prompt;
        prompt += "\n";
    }
    if (context_block && *context_block) {
        prompt += context_block;
        prompt += "\n";
    }
    if (user_prompt) prompt += user_prompt;
    return generate_from_prompt(prompt.c_str());
}

void llama_generate_free(void) {
    if (gen_ctx)   llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx   = nullptr;
    gen_model = nullptr;

    if (!ctx && !model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// -------- Common / shutdown --------

void llama_free_cstr(char *p) { if (p) std::free(p); }

void llama_shutdown(void) {
    // Free gen first, then embed; embed_free will release backend if both are null.
    llama_generate_free();
    llama_embed_free();
}

} // extern "C"