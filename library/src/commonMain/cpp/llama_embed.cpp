#include "llama_embed.h"
#include "llama_jni.h" // from prebuilt llama.cpp
#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#include <android/log.h>
#include <__filesystem/operations.h> // keep your current include; switch to <filesystem> if available

static struct llama_model  *model     = nullptr;
static struct llama_context*ctx       = nullptr;
static int embedding_size             = 0;

// Generation model state
static struct llama_model  *gen_model = nullptr;
static struct llama_context*gen_ctx   = nullptr;

// Track whether backend was initialized (so either init path can be used first)
static bool g_backend_inited = false;

// -------- helpers --------
static int tokenize_with_retry(const llama_vocab *vocab,
        const char *text,
        std::vector<llama_token> &tokens,
        bool add_bos,
        bool parse_special) {
    if (!text) return 0;
    const int text_len = (int) std::strlen(text); // do NOT pass -1
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
    // Keep last (n_ctx - reserve_tail) tokens so that we can still decode
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

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model loading...");
    llama_model_params model_params = llama_model_default_params();

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "File Path: %s", model_path);
    if (std::filesystem::exists(model_path)) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file exists: %s", model_path);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file size: %ju",
                std::filesystem::file_size(model_path));
    }

    model = llama_model_load_from_file(model_path, model_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file size: %s", "Model finished loading.");
    if (!model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model loaded successfully.");

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context creating...");
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;

    ctx = llama_init_from_model(model, ctx_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embeddings enabled: %d", ctx_params.embeddings);

    if (!ctx) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context created successfully.");

    embedding_size = llama_model_n_embd(model);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    // Tokenize
    std::vector<llama_token> tokens(512);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing...");
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Token Vector Size: %zu", tokens.size());

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization failed or too many tokens. n=%d", n_tokens);
        return nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization successful.");
    tokens.resize(n_tokens);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokens size: %d", n_tokens);

    // Create batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;
    }

    // Decode
    if (llama_decode(ctx, batch) != 0) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode failed.");
        llama_batch_free(batch);
        return nullptr;
    }

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode successful.");
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context Embeddings: %p", llama_get_embeddings(ctx));

    const int dim = llama_model_n_embd(model);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model Embedding Size: %d", dim);

    // Get embedding (sequence 0)
    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_get_embeddings returned null.");
        llama_batch_free(batch);
        return nullptr;
    }

    // Use malloc/free pair (matches llama_free_embedding below)
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "malloc failed for embedding.");
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, embedding, sizeof(float) * (size_t)dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size() {
    return llama_model_n_embd(model);
}

void llama_free_embedding(float *ptr) {
    if (ptr) std::free(ptr);
}

void llama_embed_free() {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx   = nullptr;
    model = nullptr;

    // Only free backend if generation side is also torn down
    if (!gen_ctx && !gen_model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// ================= Text Generation =================

bool llama_generate_init(const char *model_path) {
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_generate_init starts");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();
    gen_model = llama_model_load_from_file(model_path, model_params);
    if (!gen_model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate model loaded from file successfully.");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false; // not needed for generation
    ctx_params.n_ctx      = 2048;  // enlarge context for long prompts

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate context created successfully.");
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate context: %p", gen_ctx);
    return gen_ctx != nullptr;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    // Tokenize the prompt
    std::vector<llama_token> tokens(512);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing Generator...");

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(gen_model),
            prompt,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization failed. n=%d", n_tokens);
        return nullptr;
    }

    tokens.resize(n_tokens);

    // Ensure the prompt fits the context (keep tail)
    const int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > n_ctx - 8) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni",
                "Prompt too long (%d) for ctx (%d). Truncating.", n_tokens, n_ctx);
        truncate_to_ctx(tokens, n_ctx, /*reserve_tail*/ 8);
        n_tokens = (int)tokens.size();
    }

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization successful. Tokens: %d", n_tokens);

    // Prepare prompt evaluation batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;  // only last token produces logits
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Token[%d] = %d", i, tokens[i]);
    }
    batch.logits[n_tokens - 1] = true;

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling llama_decode for prompt...");
    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode failed on prompt.");
        return nullptr;
    }

    // ---- Sampler chain (fixed order & params) ----
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Failed to create sampler.");
        llama_batch_free(batch);
        return nullptr;
    }

    // Repetition / frequency / presence penalties first
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
            /*penalty_last_n=*/128,
            /*penalty_repeat=*/1.15f,
            /*penalty_freq=*/0.0f,
            /*penalty_present=*/0.10f
    ));

    // Top-k then top-p
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, /*min_keep*/ 1));

    // Temperature near the end
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.70f));

    // RNG source last
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::vector<llama_token> output_tokens;
    const int max_new_tokens = 256;
    int cur_pos = n_tokens;

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model context size: %d", n_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Starting generation...");

    for (int i = 0; i < max_new_tokens; ++i) {
        // Sample from last logits
        llama_token token = llama_sampler_sample(sampler, gen_ctx, -1);
        if (token < 0) {
            __android_log_print(ANDROID_LOG_WARN, "llama_jni", "Invalid token sampled (token=%d). Stopping.", token);
            break;
        }
        if (token == llama_vocab_eos(llama_model_get_vocab(gen_model))) {
            __android_log_print(ANDROID_LOG_INFO, "llama_jni", "EOS token. Stopping.");
            break;
        }

        llama_sampler_accept(sampler, token);
        output_tokens.push_back(token);

        // Avoid context overrun
        if (cur_pos >= n_ctx) {
            __android_log_print(ANDROID_LOG_WARN, "llama_jni", "Reached context limit at pos=%d (n_ctx=%d).", cur_pos, n_ctx);
            break;
        }

        // feed back one token
        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.n_tokens     = 1;
        gen_batch.token[0]     = token;
        gen_batch.pos[0]       = cur_pos++;
        gen_batch.n_seq_id[0]  = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0]    = true;

        if (llama_decode(gen_ctx, gen_batch) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "llama_decode failed during generation.");
            llama_batch_free(gen_batch);
            break;
        }
        llama_batch_free(gen_batch);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    // Convert tokens to text
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Converting tokens to text...");
    std::string output;
    char buf[8 * 1024];
    for (llama_token tok : output_tokens) {
        // 6-arg signature: vocab, token, buf, length, lstrip, special
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

    // Final C string allocation
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Final C string allocation...");
    char *result = (char *) std::malloc(output.size() + 1);
    if (!result) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "malloc failed.");
        return nullptr;
    }
    std::memcpy(result, output.c_str(), output.size() + 1);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Result: %s", result);
    return result;
}

void llama_generate_free() {
    if (gen_ctx)   llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx   = nullptr;
    gen_model = nullptr;

    // Only free backend if embeddings side is also torn down
    if (!ctx && !model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}