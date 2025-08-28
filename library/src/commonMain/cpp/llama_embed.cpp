#include "llama_jni.h" // from prebuilt llama.cpp
#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#include <android/log.h>
#include <filesystem>

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

static void truncate_to_ctx(std::vector<llama_token> &tokens, int n_ctx, int reserve_tail = 8) {
    const int max_prompt = std::max(8, n_ctx - reserve_tail);
    if ((int)tokens.size() > max_prompt) {
        const int drop = (int)tokens.size() - max_prompt;
        tokens.erase(tokens.begin(), tokens.begin() + drop);
    }
}

// ================= Embeddings =================

bool llama_embed_init(const char *model_path) {
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Initializing llama (embeddings)...");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embed file: %s", model_path);
    if (std::filesystem::exists(model_path)) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Exists, size: %ju",
                (uintmax_t)std::filesystem::file_size(model_path));
    }

    model = llama_model_load_from_file(model_path, model_params);
    if (!model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embed model loaded.");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;

    ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embed context created.");

    embedding_size = llama_model_n_embd(model);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embed dim: %d", embedding_size);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    std::vector<llama_token> tokens(1024);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing for embeddings...");

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        __android_log_print(ANDROID_LOG_WARN, "llama_jni",
                "Embedding tokenize fail/too long. n=%d ctx=%d", n_tokens, llama_n_ctx(ctx));
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
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "llama_decode() for embeddings failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "llama_get_embeddings_seq returned null");
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "malloc failed for embedding");
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
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_generate_init...");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();
    gen_model = llama_model_load_from_file(model_path, model_params);
    if (!gen_model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Gen model loaded.");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false;
    ctx_params.n_ctx      = 4096;

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Gen context: %p", gen_ctx);
    return gen_ctx != nullptr;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    std::vector<llama_token> tokens(2048);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing (gen)...");

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(gen_model),
            prompt,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Tokenization failed. n=%d", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    const int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > n_ctx - 8) {
        __android_log_print(ANDROID_LOG_WARN, "llama_jni",
                "Prompt too long (%d) for ctx (%d). Truncating tail-keep.", n_tokens, n_ctx);
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
        llama_batch_free(batch);
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "llama_decode failed on prompt.");
        return nullptr;
    }

    // Sampler: more deterministic to avoid listy/digit starts
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Failed to create sampler.");
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

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generation loop start. n_ctx=%d", n_ctx);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, gen_ctx, -1);
        if (token < 0) break;
        if (token == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

        llama_sampler_accept(sampler, token);
        output_tokens.push_back(token);

        if (cur_pos >= n_ctx) break;

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.n_tokens     = 1;
        gen_batch.token[0]     = token;
        gen_batch.pos[0]       = cur_pos++;
        gen_batch.n_seq_id[0]  = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0]    = true;

        if (llama_decode(gen_ctx, gen_batch) != 0) { llama_batch_free(gen_batch); break; }
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

    char *result = (char *) std::malloc(output.size() + 1);
    if (!result) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "malloc failed for result C string.");
        return nullptr;
    }
    std::memcpy(result, output.c_str(), output.size() + 1);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generation done. bytes=%zu", output.size());
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