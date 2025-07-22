#include "llama_embed.h"
#include "llama_jni.h" // from prebuilt llama.cpp
#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <android/log.h>
#include <__filesystem/operations.h>

static struct llama_model *model = nullptr;
static struct llama_context *ctx = nullptr;
static int embedding_size = 0;

// Generation model state
static struct llama_model *gen_model = nullptr;
static struct llama_context *gen_ctx = nullptr;

bool llama_embed_init(const char *model_path) {
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Initializing llama...");
    llama_backend_init();
    //llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model loading...");
    llama_model_params model_params = llama_model_default_params();
    //model_params.n_ctx = 512; // or your desired context size
    //model_params.main_gpu = -1; // Disable GPU use
    //model_params.n_gpu_layers = 0; // Disable GPU layers
    //model_params.devices = nullptr; // Disable devices
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "File Path: %s", model_path);
    if (std::filesystem::exists(model_path)) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file exists: %s", model_path);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file size: %ju",
                std::filesystem::file_size(model_path));
    }

    model = llama_model_load_from_file(model_path, model_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model file size: %s",
            "Model finished loading.");
    if (!model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model loaded successfully.");

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context creating...");
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;

    ctx = llama_init_from_model(model, ctx_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Embeddings enabled: %d",
            ctx_params.embeddings);

    if (!ctx) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context created successfully.");

    embedding_size = llama_model_n_embd(model);

    return true;
}

/*
float *llama_embed(const char *input) {
    if (!ctx) return nullptr;

    std::vector<llama_token> tokens(128);
    auto embd_inp = ::llama_tokenize(llama_model_get_vocab(model), input, 128, tokens.data(),
                                     tokens.size(), true, false);

    //lama_eval(ctx, tokens.data(), n_tokens, 0, 1);

    llama_batch batch = llama_batch_init(llama_n_ctx(ctx), 0, 1);
    // prepare batch
    {
        batch.n_tokens = 128;
        for (int i = 0; i < batch.n_tokens; i++) {
            batch.token[i] = embd_inp;
            batch.pos[i] = i;
            batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = i == batch.n_tokens - 1;
        }
    }

    llama_decode(ctx, batch);
    const float *embedding = llama_get_embeddings(ctx);

    //int dim = llama_n_embd(model);
    auto *out = new float[embedding_size];
    memcpy(out, embedding, sizeof(float) * embedding_size);
    return out;
}
*/

float *llama_embed(const char *input) {
    if (!ctx || !model) return nullptr;

    // Tokenize
    std::vector <llama_token> tokens(512);

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing...");

    int tokenSize = static_cast<int>(tokens.size());
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Token Vector Size: %du", tokenSize);

    int n_tokens = ::llama_tokenize(
            llama_model_get_vocab(model),            // llama_model *
            input,            // input string
            128,
            tokens.data(),    // output token buffer
            tokenSize,    // max number of tokens
            true,             // add_bos
            false             // allow special tokens
    );

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        fprintf(stderr, "Tokenization failed or too many tokens\n");
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization failed or too many tokens.");
        return nullptr;
    }
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization successful.");
    tokens.resize(n_tokens);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokens size: %d", n_tokens);

    // Create batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }

    // Decode
    if (llama_decode(ctx, batch) != 0) {
        fprintf(stderr, "llama_decode failed\n");
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode failed.");
        llama_batch_free(batch);
        return nullptr;
    }

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode successful.");
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Context Embeddings: %f", llama_get_embeddings(ctx));

    int dim = llama_model_n_embd(model);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model Embedding Size: %d", dim);

    // Get embedding
    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        fprintf(stderr, "llama_get_embeddings returned null\n");
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_get_embeddings returned null.");
        llama_batch_free(batch);
        return nullptr;
    }

    auto *out = new float[dim];
    memcpy(out, embedding, sizeof(float) * dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size() {
    return llama_model_n_embd(model);
}

void llama_free_embedding(float *ptr) {
    if (ptr) free(ptr);
}

void llama_embed_free() {
    if (ctx) llama_free(ctx);
    if (model) llama_model_free(model);
    llama_backend_free();
}

bool llama_generate_init(const char *model_path) {
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_generate_init starts");
    llama_model_params model_params = llama_model_default_params();
    gen_model = llama_model_load_from_file(model_path, model_params);
    if (!gen_model) return false;
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate model loaded from file successfully.");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false; // not needed for generation

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate context created successfully.");
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Generate context: %p", gen_ctx);
    return gen_ctx != nullptr;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model) return nullptr;

    // Tokenize the prompt
    int max_prompt_tokens = 512;
    std::vector <llama_token> tokens(max_prompt_tokens);

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenizing Generator...");

    int tokenSize = static_cast<int>(tokens.size());
    int n_tokens = ::llama_tokenize(
            llama_model_get_vocab(gen_model),
            prompt,
            max_prompt_tokens,
            tokens.data(),
            tokenSize,
            true,  // add_bos
            false  // no special tokens
    );

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(gen_ctx)) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization failed or too many tokens.");
        return nullptr;
    }

    tokens.resize(n_tokens);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Tokenization successful. Tokens: %d", n_tokens);

    // Prepare prompt evaluation batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;  // only last token produces logits
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Token[%d] = %d", i, tokens[i]);
    }
    batch.logits[n_tokens - 1] = true;

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling llama_decode for prompt...");
    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode failed on prompt.");
        return nullptr;
    }

    const float *logits = llama_get_logits(gen_ctx);
    if (!logits) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Logits are NULL even after decode.");
        return nullptr;
    }

    // Create sampler
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling llama_sampler_chain_init...");
    llama_sampler_chain_params samplerChainDefaultParams = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(samplerChainDefaultParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    if (!sampler) {
        __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Failed to create sampler.");
        return nullptr;
    }

    std::vector <llama_token> output_tokens;
    int max_new_tokens = 256;
    int cur_pos = n_tokens;

    uint ctx_size = llama_n_ctx(gen_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Model context size: %d", ctx_size);

    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Starting llama_sampler_sample...");
    for (int i = 0; i < max_new_tokens; ++i) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling Sampler...");
        int n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(gen_model));
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "n_vocab: %d", n_vocab);
        std::vector <llama_token_data> candidates(n_vocab);

        for (int j = 0; j < n_vocab; ++j) {
            candidates[j] = {j, logits[j], 0.0f};
        }
        llama_token_data_array cur_p = {candidates.data(), candidates.size(), false};
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "cur_p: %p", &cur_p);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "cur_p.data: %p", cur_p.data);

        if (cur_p.size == 0) {
            __android_log_print(ANDROID_LOG_WARN, "llama_jni", "No candidates: breaking sampling loop at step %d", i);
            break;
        }

        llama_sampler_apply(sampler, &cur_p);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Starting llama_sampler_sample");

        llama_token token;
        int retries = 0;
        do {
            llama_sampler_apply(sampler, &cur_p);
            token = llama_sampler_sample(sampler, gen_ctx, -1);
            retries++;
        } while ((token == 0 || token >= n_vocab) && retries < 5);

        if (token <= 0 || token >= n_vocab) {
            __android_log_print(ANDROID_LOG_WARN, "llama_jni", "Sampling failed after retries.");
            break;
        }

        if (token == llama_vocab_eos(llama_model_get_vocab(gen_model))) {
            __android_log_print(ANDROID_LOG_INFO, "llama_jni", "EOS token received. Ending generation.");
            break;
        }

        llama_sampler_accept(sampler, token);

        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Finished llama_sampler_sample. Token: %d", i);
        if (token == n_vocab) break;
        output_tokens.push_back(token);

        // Feed token back
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling llama_batch_init...");
        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.token[0] = token;
        gen_batch.pos[0] = cur_pos++;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0] = true;
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Finished llama_batch_init.");

        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Calling llama_decode...");
        if (llama_decode(gen_ctx, gen_batch) != 0) {
            llama_batch_free(gen_batch);
            __android_log_print(ANDROID_LOG_INFO, "llama_jni", "llama_decode failed during generation.");
            break;
        }

        logits = llama_get_logits(gen_ctx);
        if (!logits) {
            __android_log_print(ANDROID_LOG_ERROR, "llama_jni", "Logits NULL after decode in loop.");
            llama_batch_free(gen_batch);
            break;
        }

        llama_batch_free(gen_batch);
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Finished llama_decode.");
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    // Convert tokens to text
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Converting tokens to text...");
    std::string output;
    char buf[8 * 1024];
    for (llama_token tok: output_tokens) {
        int n = llama_token_to_piece(
                llama_model_get_vocab(gen_model),
                tok,
                buf,
                sizeof(buf),
                sizeof(buf),
                false
        );
        if (n > 0) {
            output.append(buf, n);
        }
    }

    // Final C string allocation
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Final C string allocation...");
    char *result = (char *) malloc(output.size() + 1);
    if (!result) {
        __android_log_print(ANDROID_LOG_INFO, "llama_jni", "malloc failed.");
        return nullptr;
    }
    strcpy(result, output.c_str());
    __android_log_print(ANDROID_LOG_INFO, "llama_jni", "Result: %s", result);
    return result;
}

void llama_generate_free() {
    if (gen_ctx) llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx = nullptr;
    gen_model = nullptr;
}
