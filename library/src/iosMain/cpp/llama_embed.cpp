#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#include <filesystem>

#ifdef __APPLE__
#include <TargetConditionals.h>
#else
#define TARGET_OS_SIMULATOR 0
#endif

static struct llama_model  *model     = nullptr;
static struct llama_context*ctx       = nullptr;
static int embedding_size             = 0;

// Generation model state
static struct llama_model  *gen_model = nullptr;
static struct llama_context*gen_ctx   = nullptr;

// Track whether backend was initialized
static bool g_backend_inited = false;

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
    // keep tail (system+user are at the end), drop head
    std::vector<llama_token> out;
    out.reserve(keep);
    out.insert(out.end(), tokens.end() - keep, tokens.end());
    tokens.swap(out);
}

// Load helper that applies Simulator-safe defaults and retries with safer flags if needed
static llama_model *load_model_with_fallback(const char *path) {
    llama_model_params mp = llama_model_default_params();

#if TARGET_OS_SIMULATOR
    // Simulator: avoid mmap/mlock and any GPU offload to prevent issues
    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
#endif

    llama_model *m = llama_model_load_from_file(path, mp);
    if (m) return m;

    // Fallback: force the safest settings
    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;

    return llama_model_load_from_file(path, mp);
}

extern "C" {

// ================= Embeddings =================

bool llama_embed_init(const char *model_path) {
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    model = load_model_with_fallback(model_path);
    if (!model) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;
    ctx_params.n_ctx      = 2048;
    // If you need pooling, set it here for newer llama.cpp versions:
    // ctx_params.pooling_type = LLAMA_POOLING_TYPE_CLS; // only if supported by your headers

    ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    embedding_size = llama_model_n_embd(model);
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
        return nullptr;
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;
    }

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }

    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, embedding, sizeof(float) * (size_t)dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size() { return llama_model_n_embd(model); }
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

// ================= Text Generation =================

bool llama_generate_init(const char *model_path) {
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
    return true;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    llama_kv_self_clear(gen_ctx);

    std::vector<llama_token> tokens(2048);

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(gen_model),
            prompt,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ true);  // allow chat special tokens

    if (n_tokens <= 0) {
        return nullptr;
    }
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > n_ctx - 8) {
        truncate_to_ctx(tokens, n_ctx, 8);
    }

    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    batch.n_tokens = (int)tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;       // start at 0 each turn
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;       // single sequence id = 0
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }

    // Sampler
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

    std::vector<llama_token> output_tokens;
    const int max_new_tokens = 640;
    int cur_pos = batch.n_tokens;

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, gen_ctx, -1);
        if (token < 0) break;
        if (token == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

        // Early stop on chat EOT special tokens
        {
            char piece_buf[64];
            int nn = llama_token_to_piece(
                    llama_model_get_vocab(gen_model),
                    token,
                    piece_buf,
                    (int)sizeof(piece_buf),
                    /* lstrip = */ 0,
                    /* special = */ 0);
            if (nn > 0) {
                if (nn >= (int)sizeof(piece_buf)) {
                    piece_buf[sizeof(piece_buf) - 1] = '\0';
                } else {
                    piece_buf[nn] = '\0';
                }
                if (std::strcmp(piece_buf, "<end_of_turn>") == 0 ||
                        std::strcmp(piece_buf, "<|eot_id|>") == 0) {
                    break;
                }
            }
        }

        llama_sampler_accept(sampler, token);
        output_tokens.push_back(token);

        if (cur_pos >= (int)n_ctx) break;

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.n_tokens = 1;
        gen_batch.token[0] = token;
        gen_batch.pos[0]   = cur_pos;
        gen_batch.n_seq_id[0]  = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0]    = true;

        if (llama_decode(gen_ctx, gen_batch) != 0) {
            llama_batch_free(gen_batch);
            break;
        }
        cur_pos++;
        llama_batch_free(gen_batch);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string output;
    char buf[8192];
    for (llama_token tok : output_tokens) {
        int n = llama_token_to_piece(
                llama_model_get_vocab(gen_model),
                tok,
                buf,
                (int)sizeof(buf),
                /* lstrip = */ 0,
                /* special = */ false
        );
        if (n > 0) {
            output.append(buf, n);
        }
    }

    char *result = (char *) std::malloc(
            output.size() + 1 /* null */);
    if (!result) {
        return nullptr;
    }
    std::memcpy(result, output.c_str(), output.size() + 1);
    return result;
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