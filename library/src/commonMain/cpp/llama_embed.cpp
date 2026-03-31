#include "llama_jni.h" // from prebuilt llama.cpp
#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <vector>
#include <string>
#if defined(__ANDROID__)
#include <android/log.h>
#else
#include <cstdio>
#endif
#include <filesystem>
#include <algorithm>
#include <cctype>

// Portable logging macros: use Android log on Android, stderr otherwise.
#if defined(__ANDROID__)
#define LOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "llama_jni", fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN,  "llama_jni", fmt, ##__VA_ARGS__)
#define LOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, "llama_jni", fmt, ##__VA_ARGS__)
#else
#define LOGI(fmt, ...) (std::fprintf(stderr, "[INFO]  llama_jni: " fmt "\n", ##__VA_ARGS__))
#define LOGW(fmt, ...) (std::fprintf(stderr, "[WARN]  llama_jni: " fmt "\n", ##__VA_ARGS__))
#define LOGE(fmt, ...) (std::fprintf(stderr, "[ERROR] llama_jni: " fmt "\n", ##__VA_ARGS__))
#endif

static struct llama_model  *model      = nullptr;
static struct llama_context* ctx        = nullptr;
static int embedding_size              = 0;

// Generation model state
static struct llama_model  *gen_model  = nullptr;
static struct llama_context* gen_ctx   = nullptr;

// Track whether backend was initialized
static bool g_backend_inited = false;

static int tokenize_with_retry(
        const llama_vocab *vocab,
        const char *text,
        std::vector<llama_token> &tokens,
        bool add_bos,
        bool parse_special
) {
    if (!text) return 0;
    const int text_len = (int) std::strlen(text);

    int n = llama_tokenize(
            vocab,
            text, text_len,
            tokens.data(),
            (int) tokens.size(),
            add_bos, parse_special
    );

    if (n < 0) {
        const int need = -n;
        if (need > 0) {
            tokens.resize(need);
            n = llama_tokenize(
                    vocab,
                    text, text_len,
                    tokens.data(),
                    (int) tokens.size(),
                    add_bos, parse_special
            );
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

static std::string trim(const std::string &s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}

static std::string lower_ascii(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
            [](unsigned char c) { return (char) std::tolower(c); });
    return s;
}

static bool looks_like_chat_formatted_prompt(const std::string &prompt) {
    const std::string low = lower_ascii(prompt);

    return low.find("<start_of_turn>") != std::string::npos ||
            low.find("<end_of_turn>")   != std::string::npos ||
            low.find("<|start_header_id|>") != std::string::npos ||
            low.find("<|end_header_id|>")   != std::string::npos ||
            low.find("<|eot_id|>")          != std::string::npos ||
            low.find("assistant\n")         != std::string::npos ||
            low.find("user\n")              != std::string::npos;
}

// Conservative JSON-root detector without adding a JSON dependency here.
// Good enough for schema strings like:
// {"type":"array", ...}
// {
//   "type": "object", ...
// }
static bool json_schema_root_is_array(const char *json_schema) {
    if (!json_schema || !json_schema[0]) return false;

    std::string s = lower_ascii(std::string(json_schema));
    s.erase(std::remove_if(s.begin(), s.end(),
                    [](unsigned char c) { return std::isspace(c); }),
            s.end());

    return s.find("\"type\":\"array\"") != std::string::npos;
}

static std::string build_json_instruction(const char *json_schema) {
    if (json_schema_root_is_array(json_schema)) {
        return "Return ONLY a valid JSON array. No markdown, no prose.";
    }
    return "Return ONLY valid JSON. No markdown, no prose.";
}

static std::string build_json_prompt_single(const char *prompt, const char *json_schema) {
    std::string p = prompt ? prompt : "";
    if (looks_like_chat_formatted_prompt(p)) {
        return p;
    }
    p += "\n\n";
    p += build_json_instruction(json_schema);
    return p;
}

extern "C" {

// ================= Embeddings =================

bool llama_embed_init(const char *model_path) {
    LOGI("Initializing llama (embeddings)...");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();

    LOGI("Embed file: %s", model_path ? model_path : "(null)");
    if (model_path && std::filesystem::exists(model_path)) {
        // cast to unsigned long long to avoid platform-specific printf width issues
        LOGI("Exists, size: %llu", (unsigned long long) std::filesystem::file_size(model_path));
    }

    model = llama_model_load_from_file(model_path, model_params);
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

    LOGI("Embed context created.");

    embedding_size = llama_model_n_embd(model);
    LOGI("Embed dim: %d", embedding_size);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    const int n_ctx = (int) llama_n_ctx(ctx);

    std::vector<llama_token> tokens(1024);
    LOGI("Tokenizing for embeddings...");

    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false
    );

    if (n_tokens <= 0) {
        LOGW("Embedding tokenize failed. n=%d", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    if ((int) tokens.size() > n_ctx) {
        LOGW("Embedding input too long. n=%d ctx=%d. Truncating tail.", (int) tokens.size(), n_ctx);
        truncate_to_ctx(tokens, n_ctx, /*reserve_tail*/ 0);
    }

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    batch.n_tokens = (int) tokens.size();
    for (int i = 0; i < batch.n_tokens; i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_encode(ctx, batch) != 0) {
        LOGE("llama_encode() for embeddings failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        LOGE("llama_get_embeddings_seq returned null");
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    auto *out = (float *) std::malloc(sizeof(float) * (size_t) dim);
    if (!out) {
        LOGE("malloc failed for embedding");
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, embedding, sizeof(float) * (size_t) dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size() {
    return model ? llama_model_n_embd(model) : 0;
}

void llama_free_embedding(float *ptr) {
    if (ptr) std::free(ptr);
}

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
    LOGI("llama_generate_init...");
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params model_params = llama_model_default_params();
    gen_model = llama_model_load_from_file(model_path, model_params);
    if (!gen_model) return false;
    LOGI("Gen model loaded.");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false;
    ctx_params.n_ctx      = 8192;

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return false;
    }
    LOGI("Gen context created. n_ctx=%d", ctx_params.n_ctx);
    return true;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    // Keep prompt exactly as provided.
    // This preserves already formatted chat prompts and avoids mutating them,
    // which is the core fix direction for issue #90.
    const std::string final_prompt = prompt;

    llama_memory_clear(llama_get_memory(gen_ctx), false);
    LOGI("Cleared KV cache for new generation turn");

    std::vector<llama_token> tokens(2048);
    LOGI("Tokenizing (gen)...");

    const llama_vocab* vocab = llama_model_get_vocab(gen_model);

    int n_tokens = tokenize_with_retry(
            vocab,
            final_prompt.c_str(),
            tokens,
            /*add_bos*/ false,
            /*parse_special*/ true // allow chat special tokens
    );

    if (n_tokens <= 0) {
        LOGE("Tokenization failed. n=%d", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    const int n_ctx = (int) llama_n_ctx(gen_ctx);
    if ((int) tokens.size() > n_ctx - 8) {
        LOGW("Prompt too long (%d) for ctx (%d). Truncating tail-keep.", (int) tokens.size(), n_ctx);
        truncate_to_ctx(tokens, n_ctx, 8);
    }

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    batch.n_tokens = (int) tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        LOGE("llama_decode failed on prompt.");
        return nullptr;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        LOGE("Failed to create sampler.");
        return nullptr;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::vector<llama_token> output_tokens;

    int cur_pos = batch.n_tokens;
    const int safety = 16;

    int remaining_ctx = n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;

    const int hard_cap = 2048;
    int max_new_tokens = std::min(remaining_ctx, hard_cap);

    LOGI("Generation loop start. n_ctx=%d, cur_pos=%d, remaining_ctx=%d, max_new_tokens=%d",
            n_ctx, cur_pos, remaining_ctx, max_new_tokens);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, gen_ctx, -1);
        if (token < 0) break;
        if (token == llama_vocab_eos(vocab)) break;

        // Early stop on chat EOT / next-turn special tokens
        {
            char piece_buf[64];
            int nn = llama_token_to_piece(
                    vocab,
                    token,
                    piece_buf,
                    (int) sizeof(piece_buf),
                    /* lstrip = */ 0,
                    /* special = */ true
            );
            if (nn > 0) {
                piece_buf[std::min(nn, (int) sizeof(piece_buf) - 1)] = '\0';
                if (std::strcmp(piece_buf, "<end_of_turn>") == 0 ||
                        std::strcmp(piece_buf, "<|eot_id|>")  == 0 ||
                        std::strcmp(piece_buf, "<start_of_turn>") == 0 ||
                        std::strcmp(piece_buf, "<|im_end|>") == 0) {
                    break;
                }
            }
        }

        llama_sampler_accept(sampler, token);
        output_tokens.push_back(token);

        if (cur_pos >= n_ctx) break;

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        gen_batch.n_tokens = 1;
        gen_batch.token[0] = token;
        gen_batch.pos[0]   = cur_pos;
        gen_batch.n_seq_id[0]  = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0]    = true;

        if (llama_decode(gen_ctx, gen_batch) != 0) {
            LOGE("llama_decode failed in loop.");
            llama_batch_free(gen_batch);
            break;
        }
        cur_pos++;
        llama_batch_free(gen_batch);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string output;
    output.reserve(output_tokens.size() * 4);

    char buf[8192];
    for (llama_token tok : output_tokens) {
        int n = llama_token_to_piece(
                vocab,
                tok,
                buf,
                (int) sizeof(buf),
                /* lstrip = */ 0,
                /* special = */ false
        );
        if (n > 0) output.append(buf, n);
    }

    output = trim(output);

    char *result = (char *) std::malloc(output.size() + 1);
    if (!result) {
        LOGE("malloc failed for result C string.");
        return nullptr;
    }
    std::memcpy(result, output.c_str(), output.size());
    result[output.size()] = '\0';

    LOGI("Generation done. bytes=%zu", output.size());
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