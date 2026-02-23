// llamatik_wasm_api.cpp
#include "llama.h"

#include <string>
#include <vector>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <cmath>

// -------------------------------------------------------------------------------------
// WASM-side minimal C API for the Kotlin/WASM bridge.
//
// IMPORTANT:
// This file is updated to use the SAME llama.cpp API style as the Android/JVM code you
// pasted (vocab-based APIs + llama_memory_clear(llama_get_memory(ctx), ...)).
// That fixes the build errors caused by older model-based tokenize / vocab functions.
// -------------------------------------------------------------------------------------

static llama_model*   g_model = nullptr;
static llama_context* g_ctx   = nullptr;

// Basic generation params (you can expose setters later)
static int   g_n_ctx          = 4096;
static int   g_max_tokens     = 256;
static float g_temp           = 0.8f;
static float g_top_p          = 0.95f;
static int   g_top_k          = 40;
static float g_repeat_penalty = 1.10f;
static int   g_repeat_last_n  = 64;

static inline const llama_vocab* vocab_of(llama_model* model) {
    return model ? llama_model_get_vocab(model) : nullptr;
}

static std::string token_to_piece(const llama_vocab* vocab, llama_token token) {
    char buf[8 * 1024];
    int n = llama_token_to_piece(vocab, token, buf, (int)sizeof(buf), /*lstrip*/0, /*special*/false);
    if (n <= 0) return std::string();
    return std::string(buf, buf + n);
}

static void apply_repeat_penalty(
        float* logits,
        int n_vocab,
        const std::vector<llama_token>& last_tokens,
        float repeat_penalty
) {
    if (!logits) return;
    if (repeat_penalty == 1.0f || last_tokens.empty()) return;

    // For each token in history, adjust its logit
    for (llama_token t : last_tokens) {
        if (t < 0 || t >= n_vocab) continue;
        float& logit = logits[t];
        if (logit < 0.0f) {
            logit *= repeat_penalty;
        } else {
            logit /= repeat_penalty;
        }
    }
}

// Softmax over candidates
static void softmax(std::vector<llama_token_data>& candidates) {
    float max_logit = -INFINITY;
    for (auto& c : candidates) max_logit = std::max(max_logit, c.logit);

    double sum = 0.0;
    for (auto& c : candidates) {
        double p = std::exp((double)c.logit - (double)max_logit);
        c.p = (float)p;
        sum += p;
    }
    if (sum <= 0.0) {
        // fallback to uniform
        float inv = 1.0f / std::max<size_t>(1, candidates.size());
        for (auto& c : candidates) c.p = inv;
        return;
    }
    float inv_sum = (float)(1.0 / sum);
    for (auto& c : candidates) c.p *= inv_sum;
}

static llama_token sample_top_p_top_k(
        const llama_vocab* vocab,
        float* logits,
        float temp,
        int top_k,
        float top_p
) {
    const int n_vocab = vocab ? llama_vocab_n_tokens(vocab) : 0;
    if (n_vocab <= 0 || !logits) return (llama_token)0;

    // Build candidate list
    std::vector<llama_token_data> cand;
    cand.reserve((size_t)n_vocab);
    for (int i = 0; i < n_vocab; i++) {
        cand.push_back(llama_token_data{ i, logits[i], 0.0f });
    }

    // Temperature
    if (temp <= 0.0f) {
        // greedy
        int best = 0;
        float best_logit = logits[0];
        for (int i = 1; i < n_vocab; i++) {
            if (logits[i] > best_logit) {
                best_logit = logits[i];
                best = i;
            }
        }
        return (llama_token)best;
    } else {
        const float inv_temp = 1.0f / temp;
        for (auto& c : cand) c.logit *= inv_temp;
    }

    // Sort by logit desc (keep top_k region)
    const int k = std::max(1, top_k);
    const int k_clamped = std::min<int>((int)cand.size(), k);

    std::partial_sort(
            cand.begin(),
            cand.begin() + k_clamped,
            cand.end(),
            [](const llama_token_data& a, const llama_token_data& b) {
                return a.logit > b.logit;
            }
    );

    // Truncate to top_k
    if (top_k > 0 && top_k < (int)cand.size()) {
        cand.resize((size_t)top_k);
    }

    // Softmax on truncated
    softmax(cand);

    // Apply top_p nucleus
    if (top_p < 1.0f) {
        std::sort(cand.begin(), cand.end(),
                [](const llama_token_data& a, const llama_token_data& b) {
                    return a.p > b.p;
                });
        float cum = 0.0f;
        size_t cut = cand.size();
        for (size_t i = 0; i < cand.size(); i++) {
            cum += cand[i].p;
            if (cum >= top_p) {
                cut = i + 1;
                break;
            }
        }
        if (cut < cand.size()) cand.resize(cut);
        // Renormalize
        softmax(cand);
    }

    // Sample from distribution
    float r = (float)rand() / (float)RAND_MAX;
    float cum = 0.0f;
    for (auto& c : cand) {
        cum += c.p;
        if (r <= cum) return (llama_token)c.id;
    }
    return (llama_token)cand.back().id;
}

static bool eval_tokens(llama_context* ctx, const std::vector<llama_token>& tokens, int& n_past) {
    if (!ctx) return false;
    if (tokens.empty()) return true;

    // llama_batch API is the most stable way to feed tokens
    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);

    for (int i = 0; i < (int)tokens.size(); i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = n_past + i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == (int)tokens.size() - 1);
    }

    batch.n_tokens = (int)tokens.size();

    const int rc = llama_decode(ctx, batch);
    llama_batch_free(batch);

    if (rc != 0) return false;

    n_past += (int)tokens.size();
    return true;
}

static int tokenize_with_retry(
        const llama_vocab* vocab,
        const std::string& text,
        std::vector<llama_token>& tokens,
        bool add_bos,
        bool parse_special
) {
    if (!vocab) return 0;

    const char* cstr = text.c_str();
    const int text_len = (int)text.size();

    int n = llama_tokenize(
            vocab,
            cstr,
            text_len,
            tokens.data(),
            (int)tokens.size(),
            add_bos,
            parse_special
    );

    if (n < 0) {
        const int need = -n;
        if (need > 0) {
            tokens.resize(need);
            n = llama_tokenize(
                    vocab,
                    cstr,
                    text_len,
                    tokens.data(),
                    (int)tokens.size(),
                    add_bos,
                    parse_special
            );
        }
    }
    return n;
}

extern "C" {

// Returns 1 on success, 0 on failure
int llamatik_llama_init_generate(const char* model_path) {
    if (!model_path) return 0;

    // If previously initialized, free first
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, mp);
    if (!g_model) return 0;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = g_n_ctx;

    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        llama_model_free(g_model);
        g_model = nullptr;
        return 0;
    }

    // Seed rand for sampling
    srand(1234);

    return 1;
}

// Real generate: returns a malloc'd UTF-8 string (caller frees with llamatik_free_string)
char* llamatik_llama_generate(const char* prompt) {
    if (!g_ctx || !g_model || !prompt) return nullptr;

    const llama_vocab* vocab = vocab_of(g_model);
    if (!vocab) return nullptr;

    std::string prompt_str(prompt);

    // Reset between runs (same as Android/JVM code style)
    // This is the API you already use elsewhere: llama_memory_clear(llama_get_memory(ctx), ...)
    llama_memory_clear(llama_get_memory(g_ctx), /*clear_kv=*/false);

    // Tokenize
    const int n_prompt_chars = (int)prompt_str.size();
    int n_max = std::max(32, n_prompt_chars + 16);

    std::vector<llama_token> prompt_tokens((size_t)n_max);

    int n_prompt = tokenize_with_retry(
            vocab,
            prompt_str,
            prompt_tokens,
            /*add_bos*/ true,
            /*parse_special*/ true
    );
    if (n_prompt <= 0) return nullptr;

    if (n_prompt > (int)prompt_tokens.size()) {
        // Shouldn't happen, but guard
        n_prompt = (int)prompt_tokens.size();
    }
    prompt_tokens.resize((size_t)n_prompt);

    int n_past = 0;

    // Evaluate prompt
    if (!eval_tokens(g_ctx, prompt_tokens, n_past)) return nullptr;

    std::string out;
    out.reserve(1024);

    std::vector<llama_token> last_tokens;
    last_tokens.reserve((size_t)g_repeat_last_n);

    // Pre-fill last_tokens with prompt tail for repetition penalty
    for (int i = std::max(0, (int)prompt_tokens.size() - g_repeat_last_n); i < (int)prompt_tokens.size(); i++) {
        last_tokens.push_back(prompt_tokens[i]);
    }

    const llama_token eos = llama_vocab_eos(vocab);
    const int n_vocab = llama_vocab_n_tokens(vocab);

    for (int i = 0; i < g_max_tokens; i++) {
        // Get logits for last token
        float* logits = llama_get_logits(g_ctx);
        if (!logits) break;

        // Apply repetition penalty
        apply_repeat_penalty(logits, n_vocab, last_tokens, g_repeat_penalty);

        // Sample next token
        llama_token next = sample_top_p_top_k(vocab, logits, g_temp, g_top_k, g_top_p);

        if (next == eos) break;

        // Append token text
        out += token_to_piece(vocab, next);

        // Evaluate token
        std::vector<llama_token> t = { next };
        if (!eval_tokens(g_ctx, t, n_past)) break;

        // Update history for repeat penalty
        last_tokens.push_back(next);
        if ((int)last_tokens.size() > g_repeat_last_n) {
            last_tokens.erase(last_tokens.begin());
        }
    }

    // Return malloc'd string
    char* res = (char*) std::malloc(out.size() + 1);
    if (!res) return nullptr;
    std::memcpy(res, out.c_str(), out.size());
    res[out.size()] = '\0';
    return res;
}

void llamatik_free_string(char* p) {
    if (p) std::free(p);
}

} // extern "C"
