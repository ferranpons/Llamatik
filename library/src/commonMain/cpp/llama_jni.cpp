#include <jni.h>
#include "llama.h"
#include "llama_jni.h"

#include "json-schema-to-grammar.h"
#include "nlohmann/json.hpp"

#include <string>
#include <sstream>
#include <algorithm>
#include <cstring>   // strlen, memcpy
#include <cctype>    // tolower, isalpha, isdigit
#include <cstdlib>   // malloc, free
#include <string_view>
#include <vector>
#include <atomic>
#include <cstdio>
#include <cstdarg>   // va_list, va_start, va_end

// ===================================================================================
//                              PLATFORM LOGGING
// ===================================================================================
//
// Android uses logcat; Desktop uses stderr.
// This file is shared for Android + Desktop builds.

#if defined(__ANDROID__)
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "LlamaBridge", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "LlamaBridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaBridge", __VA_ARGS__)
#else
static void log_stderr(const char* level, const char* fmt, ...) {
    std::fprintf(stderr, "[LlamaBridge][%s] ", level);
    va_list args;
    va_start(args, fmt);
    std::vfprintf(stderr, fmt, args);
    va_end(args);
    std::fprintf(stderr, "\n");
    std::fflush(stderr);
}
#define LOGI(...) log_stderr("I", __VA_ARGS__)
#define LOGD(...) log_stderr("D", __VA_ARGS__)
#define LOGW(...) log_stderr("W", __VA_ARGS__)
#define LOGE(...) log_stderr("E", __VA_ARGS__)
#endif

// ===================================================================================
//                              GLOBAL STATE (this TU)
// ===================================================================================

// Embeddings
static struct llama_model *emb_model = nullptr;
static struct llama_context *emb_ctx = nullptr;
static int emb_dim = 0;

// Text generation
static struct llama_model *gen_model = nullptr;
static struct llama_context *gen_ctx = nullptr;

// Backend lifetime
static bool g_backend_inited = false;

// Streaming cancel flag (for generateStream)
static std::atomic<bool> g_cancel_requested{false};

static std::atomic<float> g_temperature = 0.55f;
static std::atomic<float> g_top_p = 0.80f;
static std::atomic<int> g_top_k = 20;
static std::atomic<float> g_repeat_penalty = 1.10f;
static std::atomic<int> g_max_new_tokens = 640;

// ===================================================================================
//                              SMALL HELPERS
// ===================================================================================

static inline std::string trim(const std::string &s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}

static inline std::string to_lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c) {
        return (char) std::tolower(c);
    });
    return s;
}

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

// ---------- Sanitizer (strong, used by non-streaming only) ----------
static void drop_lines_with_prefix(std::string &s, const char *prefix_lc) {
    std::string out;
    out.reserve(s.size());
    size_t i = 0, line_start = 0;
    while (i <= s.size()) {
        if (i == s.size() || s[i] == '\n') {
            std::string_view line(s.data() + line_start, i - line_start);
            std::string line_lc = to_lower(std::string(line));
            if (!(line_lc.rfind(prefix_lc, 0) == 0)) {
                out.append(s.data() + line_start, i - line_start);
                if (i != s.size()) out.push_back('\n');
            }
            line_start = i + 1;
        }
        ++i;
    }
    s.swap(out);
}

// returns cleaned answer; fallback if too short or no alpha
static std::string sanitize_generation(std::string s) {
    if (s.empty()) return s;

    for (const char *stop: {"<end_of_turn>", "<|eot_id|>", "</s>"}) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); }
    }
    drop_lines_with_prefix(s, "<start_of_turn>");
    drop_lines_with_prefix(s, "<|start_header_id|>");
    drop_lines_with_prefix(s, "<|end_header_id|>");

    {
        std::string sl = to_lower(s);
        size_t qpos = sl.find("question:");
        if (qpos != std::string::npos) s = s.substr(0, qpos);
    }
    {
        std::string sl = to_lower(s);
        size_t cpos = sl.find("context:");
        if (cpos != std::string::npos) s = s.substr(0, cpos);
    }

    auto slice_after_tag = [&](const char *tag) -> bool {
        std::string low = to_lower(s);
        std::string t = to_lower(std::string(tag));
        size_t p = low.find(t);
        if (p != std::string::npos) {
            s = s.substr(p + std::strlen(tag));
            s = trim(s);
            return true;
        }
        return false;
    };
    (void) (slice_after_tag("ANSWER:") || slice_after_tag("FINAL_ANSWER:"));

    s = trim(s);

    auto strip_leading_noise = [](std::string &t) {
        auto ltrim_str = [&](const char *prefix) -> bool {
            size_t n = std::strlen(prefix);
            if (t.size() >= n && std::memcmp(t.data(), prefix, n) == 0) {
                t.erase(0, n);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                return true;
            }
            return false;
        };

        bool changed = true;
        while (changed) {
            changed = false;
            changed |= ltrim_str("• ");
            changed |= ltrim_str("- ");
            changed |= ltrim_str("* ");
            changed |= ltrim_str("> ");
            changed |= ltrim_str(u8"—");
            changed |= ltrim_str(u8"–");

            if (!t.empty() && (t[0] == ':' || t[0] == '-')) {
                t.erase(0, 1);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            }

            if (t.size() >= 2 && std::isdigit(static_cast<unsigned char>(t[0])) &&
                    (t[1] == '.' || t[1] == ')')) {
                t.erase(0, 2);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            } else if (t.size() >= 2 && std::isalpha(static_cast<unsigned char>(t[0])) &&
                    (t[1] == '.' || t[1] == ')')) {
                t.erase(0, 2);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            }
        }

        size_t k = 0;
        while (k < t.size() && !std::isalnum(static_cast<unsigned char>(t[k]))) ++k;
        if (k > 0 && k < t.size()) t.erase(0, k);
    };

    strip_leading_noise(s);
    s = trim(s);

    bool has_alpha = std::any_of(s.begin(), s.end(), [](unsigned char c) {
        return std::isalpha(c);
    });
    if (!has_alpha || s.size() < 12) {
        return "I don't have enough information in my sources.";
    }

    {
        std::string low = to_lower(s);
        const char *fragments[] = {
                "answer only from the provided context",
                "do not repeat the context",
                "respond exactly: \"i don't have enough information in my sources",
                "instructions:",
                "begin your answer",
                "start your response",
                "do not include anything else",
                "reply with only the answer text"
        };
        for (const char *f: fragments) {
            size_t p = low.find(f);
            if (p != std::string::npos) {
                s = trim(s.substr(0, p));
                break;
            }
        }
    }

    return s;
}

static bool build_json_grammar(const char *json_schema, std::string &out_grammar, std::string &out_err) {
    try {
        const std::string schema_str = (json_schema && json_schema[0]) ? std::string(json_schema) : std::string("{}");
        nlohmann::ordered_json schema = nlohmann::ordered_json::parse(schema_str);
        out_grammar = json_schema_to_grammar(schema, /*force_gbnf=*/false);
        return !out_grammar.empty();
    } catch (const std::exception &e) {
        out_err = e.what();
        return false;
    }
}

static std::string build_json_prompt_single(const char *prompt) {
    std::string p = prompt ? prompt : "";
    p += "\n\nReturn ONLY JSON. No markdown, no prose.";
    return p;
}

static std::string build_json_prompt_chat(const std::string &system, const std::string &ctx, const std::string &user, bool has_schema) {
    (void)system; // not used by this simplified prompt builder (kept for signature compatibility)
    std::string p;
    if (!ctx.empty()) {
        p += "Context:\n";
        p += ctx;
        p += "\n\n";
    }
    p += "Request:\n";
    p += user;
    p += "\n\n";
    if (has_schema) {
        p += "Return ONLY JSON matching the provided JSON Schema. No markdown, no prose.";
    } else {
        p += "Return ONLY valid JSON. No markdown, no prose.";
    }
    return p;
}

// ---------- Chat templating ----------
static std::string build_user_with_context(const std::string &context_block,
        const std::string &user_question) {
    auto t = [](const std::string &x) { return trim(x); };
    if (t(context_block).empty()) return "QUESTION:\n" + user_question;
    std::ostringstream oss;
    oss << "CONTEXT:\n" << context_block << "\n\nQUESTION:\n" << user_question;
    return oss.str();
}

static std::string build_chat_prompt_gemma(const std::string &system_msg,
        const std::string &user_msg) {
    std::ostringstream oss;
    const std::string sys = (system_msg.empty()
            ? "You are a careful assistant. Answer ONLY from the provided context. "
              "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
              "Write 2–5 short sentences in plain text. Do not use bullets or numbering."
            : system_msg + " Write 2–5 short sentences in plain text. Do not use bullets or numbering.");

    oss << "<start_of_turn>system\n"
        << sys
        << "\n<end_of_turn>\n"
        << "<start_of_turn>user\n"
        << user_msg
        << "\n<end_of_turn>\n"
        << "<start_of_turn>model\n"
        << "ANSWER: ";
    return oss.str();
}

// ===================================================================================
//                                   EMBEDDINGS
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initModel (embed): %s", path ? path : "(null)");

    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params mparams = llama_model_default_params();
    emb_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!emb_model) {
        LOGE("embed model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings = true;
    cparams.n_ctx = 2048;

    emb_ctx = llama_init_from_model(emb_model, cparams);
    if (!emb_ctx) {
        llama_model_free(emb_model);
        emb_model = nullptr;
        return JNI_FALSE;
    }

    emb_dim = llama_model_n_embd(emb_model);
    LOGI("Embed context ready. dim=%d", emb_dim);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_library_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    if (!emb_ctx || !emb_model) {
        LOGE("embed: ctx/model null");
        return nullptr;
    }

    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    if (!inputStr) {
        LOGE("embed: input null");
        return nullptr;
    }

    std::vector<llama_token> tokens(1024);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(emb_model),
            inputStr, tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);
    env->ReleaseStringUTFChars(input, inputStr);

    if (n_tokens <= 0 || n_tokens > (int)llama_n_ctx(emb_ctx)) {
        LOGW("embed tokenize fail/too long. n=%d ctx=%u", n_tokens, (unsigned)llama_n_ctx(emb_ctx));
        return nullptr;
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }

    if (llama_decode(emb_ctx, batch) != 0) {
        LOGE("embed: llama_decode failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *e = llama_get_embeddings_seq(emb_ctx, 0);
    if (!e) {
        LOGE("embed: embeddings null");
        llama_batch_free(batch);
        return nullptr;
    }

    const int dim = llama_model_n_embd(emb_model);
    jfloatArray result = env->NewFloatArray(dim);
    if (!result) {
        llama_batch_free(batch);
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, dim, e);
    llama_batch_free(batch);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_shutdown(JNIEnv *, jobject) {
    if (emb_ctx) llama_free(emb_ctx);
    if (emb_model) llama_model_free(emb_model);
    emb_ctx = nullptr;
    emb_model = nullptr;

    if (gen_ctx) llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx = nullptr;
    gen_model = nullptr;

    if (g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// ===================================================================================
//                               TEXT GENERATION
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initGenerateModel: %s", path ? path : "(null)");

    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params mparams = llama_model_default_params();
    gen_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!gen_model) {
        LOGE("gen model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings = false;
    cparams.n_ctx = 4096;

    gen_ctx = llama_init_from_model(gen_model, cparams);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Gen context ready. n_ctx=%u", (unsigned)llama_n_ctx(gen_ctx));
    return JNI_TRUE;
}

static std::string generate_with_optional_grammar(const char *prompt, const char *grammar, bool sanitize) {
    if (!gen_ctx || !gen_model || !prompt) return "";

    llama_memory_clear(llama_get_memory(gen_ctx), false);

    const llama_vocab *vocab = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(vocab, prompt, tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) return "";
    tokens.resize(n_tokens);

    const int n_ctx = (int) llama_n_ctx(gen_ctx);
    if ((int) tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    batch.n_tokens = (int) tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        return "";
    }

    float temperature = g_temperature.load();
    float top_p = g_top_p.load();
    int top_k = g_top_k.load();
    float repeat_penalty = g_repeat_penalty.load();
    int max_new_tokens = g_max_new_tokens.load();

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (grammar && grammar[0]) {
        // Hard constraint first
        llama_sampler_chain_add(sampler, llama_sampler_init_grammar(vocab, grammar, "root"));
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int cur_pos = batch.n_tokens;
    std::string output;
    char buf[8192];
    char sp[64];

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (tok == llama_vocab_eos(vocab)) break;

        // early stop on chat EOT tokens if they appear
        int sn = llama_token_to_piece(vocab, tok, sp, (int) sizeof(sp), 0, /*special*/ 1);
        if (sn > 0) {
            sp[std::min(sn, (int) sizeof(sp) - 1)] = '\0';
            if (std::strcmp(sp, "<end_of_turn>") == 0 || std::strcmp(sp, "<|eot_id|>") == 0 || std::strcmp(sp, "<start_of_turn>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);

        int nn = llama_token_to_piece(vocab, tok, buf, (int) sizeof(buf), 0, /*special*/ 0);
        if (nn > 0) output.append(buf, nn);

        if (cur_pos >= n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens = 1;
        step.token[0] = tok;
        step.pos[0] = cur_pos++;
        step.n_seq_id[0] = 1;
        step.seq_id[0][0] = 0;
        step.logits[0] = true;

        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            break;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    if (sanitize) {
        return sanitize_generation(output);
    }
    return trim(output);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
    if (!gen_ctx || !gen_model) {
        LOGE("generate: ctx/model null");
        return nullptr;
    }

    const char *prompt = env->GetStringUTFChars(input, nullptr);
    if (!prompt) {
        LOGE("generate: prompt null");
        return nullptr;
    }

    llama_memory_clear(llama_get_memory(gen_ctx), false);

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
            prompt, tokens,
            /*add_bos*/ true,
            /*parse_special*/ true);
    env->ReleaseStringUTFChars(input, prompt);

    if (n_tokens <= 0) {
        LOGE("tokenize failed");
        return nullptr;
    }
    tokens.resize(n_tokens);

    const int n_ctx = (int)llama_n_ctx(gen_ctx);
    if ((int)tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    batch.n_tokens = (int) tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == batch.n_tokens - 1);
    }
    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        LOGE("decode failed on prompt");
        return nullptr;
    }

    // NOTE: one-shot generate currently uses fixed sampler params (same as before).
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const int max_new_tokens = 640;
    int cur_pos = batch.n_tokens;

    std::string output;
    char buf[8192];

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (tok == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

        // early stop on chat EOT
        char sp[64];
        int sn = llama_token_to_piece(llama_model_get_vocab(gen_model), tok, sp, (int) sizeof(sp), 0, 1);
        if (sn > 0) {
            sp[std::min(sn, (int) sizeof(sp) - 1)] = '\0';
            if (std::strcmp(sp, "<end_of_turn>") == 0 || std::strcmp(sp, "<|eot_id|>") == 0) break;
        }

        llama_sampler_accept(sampler, tok);

        int nn = llama_token_to_piece(llama_model_get_vocab(gen_model), tok, buf, (int) sizeof(buf), 0, 0);
        if (nn > 0) output.append(buf, nn);

        if (cur_pos >= n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens = 1;
        step.token[0] = tok;
        step.pos[0] = cur_pos++;
        step.n_seq_id[0] = 1;
        step.seq_id[0][0] = 0;
        step.logits[0] = true;

        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            break;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    std::string clean = sanitize_generation(output);
    return env->NewStringUTF(clean.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generateWithContext(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser) {

    const char *psys = jSystem ? env->GetStringUTFChars(jSystem, nullptr) : nullptr;
    const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pusr = env->GetStringUTFChars(jUser, nullptr);

    std::string system = psys ? psys : "";
    std::string ctx = pctx ? pctx : "";
    std::string user = pusr ? pusr : "";

    if (jSystem) env->ReleaseStringUTFChars(jSystem, psys);
    if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
    if (jUser) env->ReleaseStringUTFChars(jUser, pusr);

    if (trim(system).empty()) {
        system = "You are a careful assistant. Answer ONLY from the provided context. "
                 "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
                 "Write 2–5 short sentences in plain text. Do not use bullets or numbering.";
    }

    std::string user_turn = build_user_with_context(ctx, user);
    std::string prompt = build_chat_prompt_gemma(system, user_turn);
    jstring jp = env->NewStringUTF(prompt.c_str());
    jstring r = Java_com_llamatik_library_platform_LlamaBridge_generate(env, nullptr, jp);
    env->DeleteLocalRef(jp);
    return r;
}

// ---------------- JSON constrained (non-streaming) ----------------

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generateJson(
        JNIEnv *env, jobject, jstring jPrompt, jstring jSchema) {

    const char *pprompt = jPrompt ? env->GetStringUTFChars(jPrompt, nullptr) : nullptr;
    const char *pschema = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    if (!pprompt) {
        if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);
        return nullptr;
    }

    std::string grammar;
    std::string err;
    if (!build_json_grammar(pschema, grammar, err)) {
        env->ReleaseStringUTFChars(jPrompt, pprompt);
        if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);
        return env->NewStringUTF("");
    }

    std::string prompt = build_json_prompt_single(pprompt);

    env->ReleaseStringUTFChars(jPrompt, pprompt);
    if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);

    std::string out = generate_with_optional_grammar(prompt.c_str(), grammar.c_str(), /*sanitize=*/false);
    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generateJsonWithContext(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser, jstring jSchema) {

    const char *psys = jSystem ? env->GetStringUTFChars(jSystem, nullptr) : nullptr;
    const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pusr = jUser ? env->GetStringUTFChars(jUser, nullptr) : nullptr;
    const char *pschema = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    std::string system = psys ? psys : "";
    std::string ctx = pctx ? pctx : "";
    std::string user = pusr ? pusr : "";

    if (jSystem) env->ReleaseStringUTFChars(jSystem, psys);
    if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
    if (jUser) env->ReleaseStringUTFChars(jUser, pusr);

    std::string grammar;
    std::string err;
    if (!build_json_grammar(pschema, grammar, err)) {
        if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);
        return env->NewStringUTF("");
    }
    const bool has_schema = pschema && pschema[0];

    if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);

    std::string prompt = build_json_prompt_chat(system, ctx, user, has_schema);
    std::string out = generate_with_optional_grammar(prompt.c_str(), grammar.c_str(), /*sanitize=*/false);
    return env->NewStringUTF(out.c_str());
}

// ===================================================================================
//                        REAL TOKEN STREAMING (JNI CALLBACKS)
// ===================================================================================

struct StreamMethods {
    jmethodID onDelta;
    jmethodID onComplete;
    jmethodID onError;
};

static bool resolve_stream_methods(JNIEnv *env, jobject cb, StreamMethods &m) {
    jclass cls = env->GetObjectClass(cb);
    if (!cls) return false;
    m.onDelta = env->GetMethodID(cls, "onDelta", "(Ljava/lang/String;)V");
    m.onComplete = env->GetMethodID(cls, "onComplete", "()V");
    m.onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
    return m.onDelta && m.onComplete && m.onError;
}

static inline bool is_eot_piece(const char *s) {
    return std::strcmp(s, "<end_of_turn>") == 0 || std::strcmp(s, "<|eot_id|>") == 0;
}

// Streams tokens from a prepared prompt string.
// Optional: pass a GBNF grammar string to hard-constrain decoding (JSON / JSON schema).
static void stream_from_prompt(
        JNIEnv *env,
        const char *prompt,
        jobject jCallback,
        const StreamMethods &m,
        const char *grammar_gbnf = nullptr) {

    if (!gen_ctx || !gen_model) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("model not initialized"));
        return;
    }

    // Reset cancel flag at the start of each stream
    g_cancel_requested.store(false, std::memory_order_relaxed);
    llama_memory_clear(llama_get_memory(gen_ctx), false);

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
            prompt, tokens,
            /*add_bos*/ true,
            /*parse_special*/ true);
    if (n_tokens <= 0) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("tokenization failed"));
        return;
    }
    tokens.resize(n_tokens);

    const int n_ctx = (int)llama_n_ctx(gen_ctx);
    if ((int)tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

    llama_batch batch = llama_batch_init((int) tokens.size(), 0, 1);
    batch.n_tokens = (int) tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == batch.n_tokens - 1);
    }
    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed on prompt"));
        return;
    }

    float temperature    = g_temperature.load();
    float top_p          = g_top_p.load();
    int   top_k          = g_top_k.load();
    float repeat_penalty = g_repeat_penalty.load();
    int   max_new_tokens = g_max_new_tokens.load();

    const llama_vocab *vocab = llama_model_get_vocab(gen_model);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // IMPORTANT: grammar must be first in the chain so it can veto invalid tokens.
    if (grammar_gbnf && grammar_gbnf[0]) {
        llama_sampler_chain_add(sampler, llama_sampler_init_grammar(vocab, grammar_gbnf, "root"));
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int cur_pos = batch.n_tokens;

    char piece_buf[768];
    char spec_buf[64];

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (tok == llama_vocab_eos(vocab)) break;

        int sn = llama_token_to_piece(vocab,
                tok, spec_buf, (int) sizeof(spec_buf),
                /* lstrip */ 0, /* special */ 1);
        if (sn > 0) {
            spec_buf[std::min(sn, (int) sizeof(spec_buf) - 1)] = '\0';
            if (is_eot_piece(spec_buf) || std::strcmp(spec_buf, "<start_of_turn>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);

        int nn = llama_token_to_piece(vocab,
                tok, piece_buf, (int) sizeof(piece_buf),
                /* lstrip */ 0, /* special */ 0);
        if (nn > 0) {
            piece_buf[std::min(nn, (int) sizeof(piece_buf) - 1)] = '\0';
            jstring delta = env->NewStringUTF(piece_buf);
            if (delta) {
                env->CallVoidMethod(jCallback, m.onDelta, delta);
                env->DeleteLocalRef(delta);
            }
        }

        if (cur_pos >= n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens = 1;
        step.token[0] = tok;
        step.pos[0] = cur_pos++;
        step.n_seq_id[0] = 1;
        step.seq_id[0][0] = 0;
        step.logits[0] = true;

        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed mid-stream"));
            llama_sampler_free(sampler);
            llama_batch_free(batch);
            return;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    // Always signal completion – Kotlin side will ignore if it has nulled activeRequestId
    env->CallVoidMethod(jCallback, m.onComplete);
}

// JNI: stream(prompt, callback)
extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateStream(
        JNIEnv *env, jobject /*thiz*/, jstring jPrompt, jobject jCallback) {

    if (!jPrompt || !jCallback) return;

    StreamMethods m{};
    if (!resolve_stream_methods(env, jCallback, m)) {
        LOGE("nativeGenerateStream: failed to resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("prompt decode failed"));
        return;
    }

    stream_from_prompt(env, prompt, jCallback, m);
    env->ReleaseStringUTFChars(jPrompt, prompt);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateJsonStream(
        JNIEnv *env, jobject /*thiz*/,
        jstring jPrompt, jstring jSchema,
        jobject jCallback) {

    if (!jPrompt || !jCallback) return;

    StreamMethods m{};
    if (!resolve_stream_methods(env, jCallback, m)) {
        LOGE("nativeGenerateJsonStream: failed to resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    const char *schema = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    if (!prompt) {
        if (jSchema) env->ReleaseStringUTFChars(jSchema, schema);
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("prompt decode failed"));
        return;
    }

    std::string grammar;
    std::string err;
    if (!build_json_grammar(schema, grammar, err)) {
        env->ReleaseStringUTFChars(jPrompt, prompt);
        if (jSchema) env->ReleaseStringUTFChars(jSchema, schema);
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF(err.c_str()));
        return;
    }

    std::string wrapped = build_json_prompt_single(prompt);

    env->ReleaseStringUTFChars(jPrompt, prompt);
    if (jSchema) env->ReleaseStringUTFChars(jSchema, schema);

    // FIX: correct argument order + actually use grammar in streaming
    stream_from_prompt(env, wrapped.c_str(), jCallback, m, grammar.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateJsonWithContextStream(
        JNIEnv *env, jobject /*thiz*/,
        jstring jSystem, jstring jContext,
        jstring jUser, jstring jSchema,
        jobject jCallback) {

    if (!jCallback) return;

    StreamMethods m{};
    if (!resolve_stream_methods(env, jCallback, m)) {
        LOGE("nativeGenerateJsonWithContextStream: failed to resolve callback methods");
        return;
    }

    const char *psys = jSystem ? env->GetStringUTFChars(jSystem, nullptr) : nullptr;
    const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pusr = jUser ? env->GetStringUTFChars(jUser, nullptr) : nullptr;
    const char *pschema = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    std::string system = psys ? psys : "";
    std::string ctx = pctx ? pctx : "";
    std::string user = pusr ? pusr : "";

    if (jSystem) env->ReleaseStringUTFChars(jSystem, psys);
    if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
    if (jUser) env->ReleaseStringUTFChars(jUser, pusr);

    std::string grammar;
    std::string err;
    if (!build_json_grammar(pschema, grammar, err)) {
        if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF(err.c_str()));
        return;
    }
    const bool has_schema = pschema && pschema[0];

    if (jSchema) env->ReleaseStringUTFChars(jSchema, pschema);

    std::string prompt = build_json_prompt_chat(system, ctx, user, has_schema);

    // FIX: correct argument order + actually use grammar in streaming
    stream_from_prompt(env, prompt.c_str(), jCallback, m, grammar.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeCancelGenerate(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    LOGI("nativeCancelGenerate: cancel requested");
    g_cancel_requested.store(true, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateWithContextStream(
        JNIEnv *env, jobject /*thiz*/,
        jstring jSystem, jstring jContext, jstring jUser, jobject jCallback) {

    if (!jCallback) return;

    StreamMethods m{};
    if (!resolve_stream_methods(env, jCallback, m)) {
        LOGE("nativeGenerateWithContextStream: failed to resolve callback methods");
        return;
    }

    const char *psys = jSystem ? env->GetStringUTFChars(jSystem, nullptr) : nullptr;
    const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pusr = jUser ? env->GetStringUTFChars(jUser, nullptr) : nullptr;

    std::string system = psys ? psys : "";
    std::string ctx = pctx ? pctx : "";
    std::string user = pusr ? pusr : "";

    if (jSystem) env->ReleaseStringUTFChars(jSystem, psys);
    if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
    if (jUser) env->ReleaseStringUTFChars(jUser, pusr);

    if (trim(system).empty()) {
        system = "You are a careful assistant. Answer ONLY from the provided context. "
                 "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
                 "Write 2–5 short sentences in plain text. Do not use bullets or numbering.";
    }

    std::string user_turn = build_user_with_context(ctx, user);
    std::string prompt = build_chat_prompt_gemma(system, user_turn);

    stream_from_prompt(env, prompt.c_str(), jCallback, m);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeUpdateGenerationParams(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jfloat temperature,
        jint maxTokens,
        jfloat topP,
        jint topK,
        jfloat repeatPenalty) {

    g_temperature     = temperature;
    g_top_p           = topP;
    g_top_k           = topK;
    g_repeat_penalty  = repeatPenalty;
    g_max_new_tokens  = (int)maxTokens;
}