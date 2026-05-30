#include <jni.h>
#include "llama.h"
#include "llama_jni.h"
#include "llama-ext.h"

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
#include <mutex>
#include <unordered_map>
#include <cinttypes>   // PRId64

#if defined(__APPLE__)
#include <cstdlib>   // setenv
#endif

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

static struct llama_model *emb_model = nullptr;     // legacy (unused now)
static struct llama_context *emb_ctx = nullptr;     // legacy (unused now)
static int emb_dim = 0;                             // legacy (unused now)

// Text generation (USED by streaming only)
static struct llama_model *gen_model = nullptr;
static struct llama_context *gen_ctx = nullptr;

// Backend lifetime (USED by streaming only; helpers manage their own backend state)
static bool g_backend_inited = false;

// Streaming cancel flag (for generateStream)
static std::atomic<bool> g_cancel_requested{false};

// ===================================================================================
//  JNI_OnLoad — runs immediately when System.load() brings in the native library.
//  On macOS: disable bf16 Metal pipelines before llama_backend_init() can touch Metal.
//  Models with bf16 tensors (Gemma 4, etc.) crash the JVM on macOS desktop because
//  older GPUs lack the kernel_mul_mm_bf16_f32 pipeline; GGML_METAL_BF16_DISABLE=1
//  makes ggml-metal fall back to f32, avoiding the crash at the cost of some speed.
//  The env var is only set when it has not already been set by the caller, so the user
//  can still override it (e.g. GGML_METAL_BF16_DISABLE=0 to re-enable if their GPU
//  supports it and they need the performance).
// ===================================================================================
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * /*vm*/, void * /*reserved*/) {
#if defined(__APPLE__) && !defined(__ANDROID__)
    if (getenv("GGML_METAL_BF16_DISABLE") == nullptr) {
        setenv("GGML_METAL_BF16_DISABLE", "1", /*overwrite=*/0);
    }
#endif
    return JNI_VERSION_1_6;
}

static std::atomic<float> g_temperature = 0.55f;
static std::atomic<float> g_top_p = 0.80f;
static std::atomic<int> g_top_k = 20;
static std::atomic<float> g_repeat_penalty = 1.10f;
static std::atomic<int> g_max_new_tokens = 640;
static std::atomic<int> g_context_length = 4096;
static std::atomic<int> g_num_threads = 4;
static std::atomic<bool> g_use_mmap = true;
static std::atomic<bool> g_flash_attention = false;
static std::atomic<int>  g_batch_size = 512;
// 0 = CPU only, -1 = all layers on GPU (Metal/CUDA)
static std::atomic<int>  g_gpu_layers = 0;

// ===================================================================================
//                              MTP (Multi-Token Prediction) STATE
// ===================================================================================
//
// The same GGUF is loaded twice: once as the trunk (gen_model/gen_ctx above) and
// once as the MTP head (g_mtp_model/g_mtp_ctx below).  The MTP head context uses
// LLAMA_CONTEXT_TYPE_MTP and a smaller n_ctx (just enough for one speculative step).
// Both contexts have pre-norm embeddings enabled via llama_set_embeddings_pre_norm().
//
// Speculative loop (per-step):
//   1. Trunk produces a token T0 via normal sampling.
//   2. MTP head receives (h_trunk, T0) and produces a draft token D1.
//   3. Trunk verifies D1: decode (T0, D1) in a 2-token batch with logits on both.
//   4. If trunk agrees with D1, emit it; repeat from step 2 with D1 as input.
//   5. Otherwise emit T0 and restart.
// This gives up to g_mtp_draft_len extra tokens per trunk forward pass.

static struct llama_model   *g_mtp_model = nullptr;
static struct llama_context *g_mtp_ctx   = nullptr;
static std::atomic<int>      g_mtp_draft_len{3};   // max draft tokens per step

// Session / KV bookkeeping (for nativeSessionReset/Save/Load/GenerateContinue)
static std::vector<llama_token> g_session_tokens;
static int g_n_past = 0;

static void session_clear_state() {
    g_session_tokens.clear();
    g_n_past = 0;
}

static void session_hard_reset() {
    if (gen_ctx) llama_memory_clear(llama_get_memory(gen_ctx), false);
    session_clear_state();
}

static bool session_is_active() {
    return gen_ctx != nullptr && g_n_past > 0 && !g_session_tokens.empty();
}

// ===================================================================================
//                         PER-SESSION CONCURRENT STATE
//
// Each LlamaSession handle is a unique int64 key. The session owns its own
// llama_context (and shares the already-loaded gen_model). Inference on separate
// sessions can run concurrently because each has its own KV cache.
// ===================================================================================

struct SessionState {
    llama_context *ctx   = nullptr;
    std::atomic<bool> cancel{false};
    std::vector<llama_token> session_tokens;
    int n_past = 0;
    // Held by nativeSessionStream for its entire duration so nativeCloseSession
    // can block until inference finishes before calling llama_free.
    std::mutex run_mutex;
};

static std::mutex              g_sessions_mutex;
static std::unordered_map<int64_t, SessionState *> g_sessions;
static std::atomic<int64_t>    g_next_session_id{1};

static SessionState *session_get(int64_t handle) {
    std::lock_guard<std::mutex> lk(g_sessions_mutex);
    auto it = g_sessions.find(handle);
    return it != g_sessions.end() ? it->second : nullptr;
}

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

static bool decode_prompt_batched(llama_context *ctx,
        const std::vector<llama_token> &tokens,
        int n_batch_size,
        int pos_offset = 0) {
    if (n_batch_size <= 0) n_batch_size = 512;
    const int total = (int)tokens.size();
    for (int start = 0; start < total; start += n_batch_size) {
        const int end   = std::min(start + n_batch_size, total);
        const int chunk = end - start;
        llama_batch b   = llama_batch_init(chunk, 0, 1);
        b.n_tokens      = chunk;
        for (int i = 0; i < chunk; ++i) {
            b.token[i]     = tokens[start + i];
            b.pos[i]       = pos_offset + start + i;
            b.n_seq_id[i]  = 1;
            b.seq_id[i][0] = 0;
            b.logits[i]    = (start + i == total - 1);
        }
        const int rc = llama_decode(ctx, b);
        llama_batch_free(b);
        if (rc != 0) return false;
    }
    return true;
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

    for (const char *stop: {"<end_of_turn>", "<|eot_id|>", "</s>", "<turn|>"}) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); }
    }
    drop_lines_with_prefix(s, "<start_of_turn>");
    drop_lines_with_prefix(s, "<|turn>");
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

static jfloatArray make_empty_float_array(JNIEnv* env) {
    return env->NewFloatArray(0);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initEmbedModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initModel (embed) -> llama_embed_init: %s", path ? path : "(null)");

    const bool ok = llama_embed_init(path);

    env->ReleaseStringUTFChars(modelPath, path);

    // legacy state is not used anymore
    emb_model = nullptr;
    emb_ctx   = nullptr;
    emb_dim   = 0;

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_library_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    if (!input) {
        LOGE("embed: input null");
        return make_empty_float_array(env);
    }

    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    if (!inputStr) {
        LOGE("embed: GetStringUTFChars failed");
        return make_empty_float_array(env);
    }

    float *emb = llama_embed(inputStr);

    env->ReleaseStringUTFChars(input, inputStr);

    if (!emb) {
        LOGE("embed: llama_embed returned null");
        return make_empty_float_array(env);
    }

    const int dim = llama_embedding_size();
    if (dim <= 0) {
        LOGE("embed: llama_embedding_size invalid: %d", dim);
        llama_free_embedding(emb);
        return make_empty_float_array(env);
    }

    jfloatArray result = env->NewFloatArray(dim);
    if (!result) {
        LOGE("embed: NewFloatArray(%d) failed", dim);
        llama_free_embedding(emb);
        return make_empty_float_array(env);
    }

    env->SetFloatArrayRegion(result, 0, dim, emb);
    llama_free_embedding(emb);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_shutdown(JNIEnv *, jobject) {
    // Close all concurrent sessions
    {
        std::lock_guard<std::mutex> lk(g_sessions_mutex);
        for (auto &kv : g_sessions) {
            if (kv.second->ctx) llama_free(kv.second->ctx);
            delete kv.second;
        }
        g_sessions.clear();
    }

    // Embeddings/generation helper state
    llama_embed_free();
    llama_generate_free();

    // MTP state
    if (g_mtp_ctx)   { llama_free(g_mtp_ctx);        g_mtp_ctx   = nullptr; }
    if (g_mtp_model) { llama_model_free(g_mtp_model); g_mtp_model = nullptr; }

    // Streaming state (owned in this TU)
    if (gen_ctx) llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx = nullptr;
    gen_model = nullptr;

    // legacy unused state (just in case)
    if (emb_ctx) llama_free(emb_ctx);
    if (emb_model) llama_model_free(emb_model);
    emb_ctx = nullptr;
    emb_model = nullptr;
    emb_dim = 0;

    // Backend used by streaming path
    if (g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// ===================================================================================
//                         MTP INIT / SHUTDOWN
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeInitMtp(
        JNIEnv *env, jobject /*thiz*/, jstring jModelPath, jint draftLen) {

    if (!gen_model) {
        LOGE("nativeInitMtp: trunk model not loaded — call initGenerateModel first");
        return JNI_FALSE;
    }

    const char *path = jModelPath ? env->GetStringUTFChars(jModelPath, nullptr) : nullptr;
    if (!path) {
        LOGE("nativeInitMtp: path is null");
        return JNI_FALSE;
    }

    // Free any previous MTP resources
    if (g_mtp_ctx)   { llama_free(g_mtp_ctx);        g_mtp_ctx   = nullptr; }
    if (g_mtp_model) { llama_model_free(g_mtp_model); g_mtp_model = nullptr; }

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap     = g_use_mmap.load(std::memory_order_relaxed);
    mparams.n_gpu_layers = g_gpu_layers.load(std::memory_order_relaxed);
    g_mtp_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jModelPath, path);

    if (!g_mtp_model) {
        LOGE("nativeInitMtp: model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.ctx_type      = LLAMA_CONTEXT_TYPE_MTP;
    cparams.n_ctx         = (uint32_t)g_context_length.load(std::memory_order_relaxed);
    cparams.n_threads     = g_num_threads.load(std::memory_order_relaxed);
    cparams.n_batch       = (uint32_t)g_batch_size.load(std::memory_order_relaxed);
    cparams.flash_attn_type = g_flash_attention.load(std::memory_order_relaxed)
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED
        : LLAMA_FLASH_ATTN_TYPE_AUTO;

    g_mtp_ctx = llama_init_from_model(g_mtp_model, cparams);
    if (!g_mtp_ctx) {
        LOGE("nativeInitMtp: context creation failed (model may not have MTP layers)");
        llama_model_free(g_mtp_model);
        g_mtp_model = nullptr;
        return JNI_FALSE;
    }

    // Enable pre-norm embedding extraction on both trunk and MTP contexts
    llama_set_embeddings_pre_norm(gen_ctx,   true, /*masked*/ false);
    llama_set_embeddings_pre_norm(g_mtp_ctx, true, /*masked*/ true);

    if (draftLen > 0) g_mtp_draft_len.store((int)draftLen, std::memory_order_relaxed);

    LOGI("nativeInitMtp: ready. draft_len=%d", g_mtp_draft_len.load());
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeShutdownMtp(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_mtp_ctx)   { llama_free(g_mtp_ctx);        g_mtp_ctx   = nullptr; }
    if (g_mtp_model) { llama_model_free(g_mtp_model); g_mtp_model = nullptr; }
    // Disable pre-norm extraction on trunk if still alive
    if (gen_ctx) llama_set_embeddings_pre_norm(gen_ctx, false, false);
    LOGI("nativeShutdownMtp: done");
}

// ===================================================================================
//                               TEXT GENERATION
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initGenerateModel: %s", path ? path : "(null)");

    if (!path) {
        LOGE("initGenerateModel: path is null");
        return JNI_FALSE;
    }

    bool helper_ok = llama_generate_init(path);
    if (!helper_ok) {
        LOGE("initGenerateModel: llama_generate_init failed");
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap    = g_use_mmap.load(std::memory_order_relaxed);
    mparams.n_gpu_layers = g_gpu_layers.load(std::memory_order_relaxed);
    gen_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!gen_model) {
        LOGE("gen model load failed");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings   = false;
    cparams.n_ctx        = (uint32_t)g_context_length.load(std::memory_order_relaxed);
    cparams.n_threads    = g_num_threads.load(std::memory_order_relaxed);
    cparams.n_batch      = (uint32_t)g_batch_size.load(std::memory_order_relaxed);
    cparams.flash_attn_type = g_flash_attention.load(std::memory_order_relaxed)
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED
        : LLAMA_FLASH_ATTN_TYPE_AUTO;
    gen_ctx = llama_init_from_model(gen_model, cparams);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return JNI_FALSE;
    }

    session_clear_state();
    LOGI("Gen context ready. n_ctx=%u threads=%d mmap=%d flash_attn=%d gpu_layers=%d",
         (unsigned)llama_n_ctx(gen_ctx),
         cparams.n_threads,
         (int)mparams.use_mmap,
         (int)g_flash_attention.load(std::memory_order_relaxed),
         (int)g_gpu_layers.load(std::memory_order_relaxed));
    return JNI_TRUE;
}

// Helper for JSON constrained non-streaming
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

    const int n_batch_gram = (int)llama_n_batch(gen_ctx);
    if (!decode_prompt_batched(gen_ctx, tokens, n_batch_gram)) {
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
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(-1, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int cur_pos = (int)tokens.size();
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
            if (std::strcmp(sp, "<end_of_turn>") == 0 || std::strcmp(sp, "<|eot_id|>") == 0 || std::strcmp(sp, "<start_of_turn>") == 0 || std::strcmp(sp, "<turn|>") == 0 || std::strcmp(sp, "<|turn>") == 0) {
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

    if (sanitize) {
        return sanitize_generation(output);
    }
    return trim(output);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
    if (!input) {
        LOGE("generate: input null");
        return nullptr;
    }

    const char *prompt = env->GetStringUTFChars(input, nullptr);
    if (!prompt) {
        LOGE("generate: GetStringUTFChars failed");
        return nullptr;
    }

    char *res = llama_generate(prompt);

    env->ReleaseStringUTFChars(input, prompt);

    if (!res) {
        LOGE("generate: llama_generate returned null");
        return env->NewStringUTF("");
    }

    jstring out = env->NewStringUTF(res);
    std::free(res); // returned by malloc in llama_embed.cpp
    return out;
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
    return std::strcmp(s, "<end_of_turn>") == 0 || std::strcmp(s, "<|eot_id|>") == 0
        || std::strcmp(s, "<turn|>") == 0;
}

// Streams tokens from a prepared prompt string.
// Optional: pass a GBNF grammar string to hard-constrain decoding (JSON / JSON schema).
static void stream_from_prompt(
        JNIEnv *env,
        const char *prompt,
        jobject jCallback,
        const StreamMethods &m,
        const char *grammar_gbnf /*= nullptr*/) {

    if (!gen_ctx || !gen_model) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("model not initialized"));
        return;
    }

    // Reset cancel flag at the start of each stream
    g_cancel_requested.store(false, std::memory_order_relaxed);
    llama_memory_clear(llama_get_memory(gen_ctx), false);
    session_clear_state();

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

    const int n_batch_stream = (int)llama_n_batch(gen_ctx);
    if (!decode_prompt_batched(gen_ctx, tokens, n_batch_stream)) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed on prompt"));
        return;
    }
    g_session_tokens = tokens;
    g_n_past = (int)tokens.size();

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

    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(-1, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int cur_pos = (int)tokens.size();

    char piece_buf[768];
    char spec_buf[64];

    // Helper: emit a token text to the JNI callback; returns false on stop token.
    auto emit_token = [&](llama_token tok) -> bool {
        int sn = llama_token_to_piece(vocab, tok, spec_buf, (int)sizeof(spec_buf), 0, 1);
        if (sn > 0) {
            spec_buf[std::min(sn, (int)sizeof(spec_buf)-1)] = '\0';
            if (is_eot_piece(spec_buf) || std::strcmp(spec_buf, "<start_of_turn>") == 0 || std::strcmp(spec_buf, "<|turn>") == 0) return false;
        }
        int nn = llama_token_to_piece(vocab, tok, piece_buf, (int)sizeof(piece_buf), 0, 0);
        if (nn > 0) {
            piece_buf[std::min(nn, (int)sizeof(piece_buf)-1)] = '\0';
            jstring delta = env->NewStringUTF(piece_buf);
            if (delta) { env->CallVoidMethod(jCallback, m.onDelta, delta); env->DeleteLocalRef(delta); }
        }
        g_session_tokens.push_back(tok);
        ++g_n_past;
        return true;
    };

    const bool use_mtp = (g_mtp_ctx != nullptr && g_mtp_model != nullptr);
    const int  n_embd  = use_mtp ? llama_model_n_embd(gen_model) : 0;
    const int  draft_len = use_mtp ? g_mtp_draft_len.load(std::memory_order_relaxed) : 0;

    // MTP needs a separate sampler for the draft context (greedy from top-k=10)
    llama_sampler *mtp_sampler = nullptr;
    if (use_mtp) {
        const llama_vocab *mtp_vocab = llama_model_get_vocab(g_mtp_model);
        mtp_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(mtp_sampler, llama_sampler_init_top_k(10));
        llama_sampler_chain_add(mtp_sampler, llama_sampler_init_greedy());
        (void)mtp_vocab;
    }

    int tokens_generated = 0;
    bool error_flag = false;

    while (tokens_generated < max_new_tokens) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) break;

        // ── Step 1: sample next token from trunk ──────────────────────────────
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0 || tok == llama_vocab_eos(vocab) || tok == llama_vocab_eot(vocab)) break;
        llama_sampler_accept(sampler, tok);

        if (!emit_token(tok)) break;
        ++tokens_generated;
        if (tokens_generated >= max_new_tokens) break;

        if (!use_mtp) {
            // Standard single-token decode
            if (cur_pos >= n_ctx) break;
            llama_batch step = llama_batch_init(1, 0, 1);
            step.n_tokens    = 1;
            step.token[0]    = tok;
            step.pos[0]      = cur_pos++;
            step.n_seq_id[0] = 1; step.seq_id[0][0] = 0;
            step.logits[0]   = true;
            const int rc = llama_decode(gen_ctx, step);
            llama_batch_free(step);
            if (rc != 0) { error_flag = true; break; }
            continue;
        }

        // ── MTP speculative decode ────────────────────────────────────────────
        // We have just emitted tok (the trunk-verified token at position cur_pos-1).
        // Feed tok to the trunk to advance KV and get its pre-norm hidden state.
        if (cur_pos >= n_ctx) break;
        {
            llama_batch step = llama_batch_init(1, 0, 1);
            step.n_tokens    = 1;
            step.token[0]    = tok;
            step.pos[0]      = cur_pos++;
            step.n_seq_id[0] = 1; step.seq_id[0][0] = 0;
            step.logits[0]   = true;
            const int rc = llama_decode(gen_ctx, step);
            llama_batch_free(step);
            if (rc != 0) { error_flag = true; break; }
        }

        // Draft up to draft_len tokens using the MTP head
        std::vector<llama_token> drafts;
        drafts.reserve(draft_len);

        {
            const float *h_row = llama_get_embeddings_pre_norm(gen_ctx);
            if (h_row) {
                for (int d = 0; d < draft_len; ++d) {
                    if (g_cancel_requested.load(std::memory_order_relaxed)) break;

                    llama_token prev_tok = drafts.empty() ? tok : drafts.back();
                    llama_batch mtp_b = llama_batch_init(1, n_embd, 1);
                    mtp_b.n_tokens    = 1;
                    std::memcpy(mtp_b.embd, h_row, (size_t)n_embd * sizeof(float));
                    mtp_b.token[0]    = prev_tok;
                    mtp_b.pos[0]      = (llama_pos)(cur_pos - 1 + d);
                    mtp_b.n_seq_id[0] = 1; mtp_b.seq_id[0][0] = 0;
                    mtp_b.logits[0]   = true;

                    const int rc = llama_decode(g_mtp_ctx, mtp_b);
                    llama_batch_free(mtp_b);
                    if (rc != 0) break;

                    const llama_token draft_tok = llama_sampler_sample(mtp_sampler, g_mtp_ctx, -1);
                    if (draft_tok < 0 || draft_tok == llama_vocab_eos(vocab) || draft_tok == llama_vocab_eot(vocab)) break;

                    drafts.push_back(draft_tok);
                    h_row = llama_get_embeddings_pre_norm(g_mtp_ctx);
                    if (!h_row) break;
                }
            }
        }

        if (drafts.empty()) continue;   // no drafts produced; trunk already advanced

        // ── Verify draft tokens with the trunk ───────────────────────────────
        // Build a batch containing all draft tokens so trunk can verify them
        // in one forward pass.
        {
            const int nd = (int)drafts.size();
            llama_batch vbatch = llama_batch_init(nd, 0, 1);
            vbatch.n_tokens = nd;
            for (int i = 0; i < nd; ++i) {
                vbatch.token[i]     = drafts[i];
                vbatch.pos[i]       = cur_pos + i;
                vbatch.n_seq_id[i]  = 1; vbatch.seq_id[i][0] = 0;
                vbatch.logits[i]    = true;
            }
            const int rc = llama_decode(gen_ctx, vbatch);
            llama_batch_free(vbatch);
            if (rc != 0) { error_flag = true; break; }

            // Accept consecutive matching tokens
            int n_accepted = 0;
            for (int i = 0; i < nd && tokens_generated < max_new_tokens; ++i) {
                if (g_cancel_requested.load(std::memory_order_relaxed)) goto stream_done;

                // Sample what the trunk would pick at this position
                const llama_token verified = llama_sampler_sample(sampler, gen_ctx, i);
                if (verified != drafts[i]) {
                    // Mismatch: emit trunk's choice and stop accepting
                    if (verified == llama_vocab_eos(vocab) || verified == llama_vocab_eot(vocab)) goto stream_done;
                    llama_sampler_accept(sampler, verified);
                    if (!emit_token(verified)) goto stream_done;
                    ++tokens_generated;
                    cur_pos++;
                    break;
                }

                // Draft accepted
                llama_sampler_accept(sampler, verified);
                if (!emit_token(verified)) goto stream_done;
                ++tokens_generated;
                cur_pos++;
                ++n_accepted;
            }

            if (n_accepted == nd && tokens_generated < max_new_tokens) {
                // All drafts accepted — sample one more bonus token from the last trunk logits
                const llama_token bonus = llama_sampler_sample(sampler, gen_ctx, nd - 1);
                if (bonus == llama_vocab_eos(vocab) || bonus == llama_vocab_eot(vocab)) break;
                llama_sampler_accept(sampler, bonus);
                if (!emit_token(bonus)) break;
                ++tokens_generated;

                // Advance trunk KV for the bonus token
                if (cur_pos < n_ctx) {
                    llama_batch step = llama_batch_init(1, 0, 1);
                    step.n_tokens    = 1;
                    step.token[0]    = bonus;
                    step.pos[0]      = cur_pos++;
                    step.n_seq_id[0] = 1; step.seq_id[0][0] = 0;
                    step.logits[0]   = true;
                    const int rc2 = llama_decode(gen_ctx, step);
                    llama_batch_free(step);
                    if (rc2 != 0) { error_flag = true; break; }
                }
            }
        }

        if (cur_pos >= n_ctx) break;
    }

    stream_done:
    if (mtp_sampler) llama_sampler_free(mtp_sampler);
    llama_sampler_free(sampler);

    if (error_flag) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed mid-stream"));
        return;
    }

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

    stream_from_prompt(env, prompt, jCallback, m, /*grammar_gbnf*/ nullptr);
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

    // ✅ FIX: use grammar in streaming (and correct argument order)
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

    // ✅ FIX: use grammar in streaming (and correct argument order)
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

    stream_from_prompt(env, prompt.c_str(), jCallback, m, /*grammar_gbnf*/ nullptr);
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
        jfloat repeatPenalty,
        jint contextLength,
        jint numThreads,
        jboolean useMmap,
        jboolean flashAttention,
        jint batchSize,
        jint gpuLayers) {

    g_temperature     = temperature;
    g_top_p           = topP;
    g_top_k           = topK;
    g_repeat_penalty  = repeatPenalty;
    g_max_new_tokens  = (int)maxTokens;
    g_context_length  = (int)contextLength;
    g_num_threads     = (int)numThreads;
    g_use_mmap        = (bool)useMmap;
    g_flash_attention = (bool)flashAttention;
    g_batch_size      = (int)batchSize;
    g_gpu_layers      = (int)gpuLayers;
}

// ===================================================================================
//                            KV SESSION (nativeSession* / nativeGenerateContinue)
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeSessionReset(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (!gen_ctx) return JNI_FALSE;
    session_hard_reset();
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeSessionSave(JNIEnv *env, jobject /*thiz*/, jstring jPath) {
    if (!gen_ctx || !jPath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) return JNI_FALSE;
    const bool ok = llama_state_save_file(
        gen_ctx,
        path,
        g_session_tokens.empty() ? nullptr : g_session_tokens.data(),
        g_session_tokens.size()
    );
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeSessionLoad(JNIEnv *env, jobject /*thiz*/, jstring jPath) {
    if (!gen_ctx || !jPath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    if (!path) return JNI_FALSE;
    const int cap = std::max(1, (int)llama_n_ctx(gen_ctx));
    g_session_tokens.resize(cap);
    size_t n_loaded = 0;
    const bool ok = llama_state_load_file(
        gen_ctx,
        path,
        g_session_tokens.data(),
        g_session_tokens.size(),
        &n_loaded
    );
    env->ReleaseStringUTFChars(jPath, path);
    if (!ok) {
        session_clear_state();
        return JNI_FALSE;
    }
    g_session_tokens.resize((int)n_loaded);
    g_n_past = (int)n_loaded;
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateContinue(JNIEnv *env, jobject /*thiz*/, jstring jPrompt) {
    if (!gen_ctx || !gen_model || !jPrompt) return env->NewStringUTF("");
    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) return env->NewStringUTF("");

    if (!session_is_active()) {
        // No active session — fall back to a fresh generate via stream_from_prompt is not ideal here;
        // do a simple one-shot decode instead.
        env->ReleaseStringUTFChars(jPrompt, prompt);
        return env->NewStringUTF("[generateContinue: no active session, use generate() instead]");
    }

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_new_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);

    const llama_vocab *vocab = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(vocab, prompt, tokens, /*add_bos*/ false, /*parse_special*/ true);
    env->ReleaseStringUTFChars(jPrompt, prompt);
    if (n_tokens <= 0) return env->NewStringUTF("");
    tokens.resize(n_tokens);

    const int n_ctx   = (int)llama_n_ctx(gen_ctx);
    const int safety  = 16;
    if (g_n_past + (int)tokens.size() >= n_ctx - safety) {
        session_hard_reset();
        return env->NewStringUTF("[context full, session reset]");
    }

    const int n_batch_cont = (int)llama_n_batch(gen_ctx);
    if (!decode_prompt_batched(gen_ctx, tokens, n_batch_cont, g_n_past)) {
        return env->NewStringUTF("");
    }
    g_session_tokens.insert(g_session_tokens.end(), tokens.begin(), tokens.end());
    g_n_past += (int)tokens.size();

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(-1, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string result;
    int cur_pos = g_n_past;
    char piece_buf[256];

    for (int i = 0; i < max_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) break;
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0 || tok == llama_vocab_eos(vocab)) break;
        llama_sampler_accept(sampler, tok);
        int nn = llama_token_to_piece(vocab, tok, piece_buf, (int)sizeof(piece_buf), 0, 0);
        if (nn > 0) {
            piece_buf[std::min(nn, (int)sizeof(piece_buf) - 1)] = '\0';
            result += piece_buf;
        }
        g_session_tokens.push_back(tok);
        ++g_n_past;
        ++cur_pos;
        if (cur_pos >= n_ctx) break;
        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens = 1;
        step.token[0] = tok;
        step.pos[0]   = cur_pos - 1;
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
    return env->NewStringUTF(result.c_str());
}

// ===================================================================================
//                        CONCURRENT SESSION JNI API
// ===================================================================================

extern "C"
JNIEXPORT jlong JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeCreateSession(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    if (!gen_model) {
        LOGE("nativeCreateSession: gen_model not loaded");
        return -1L;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings   = false;
    cparams.n_ctx        = (uint32_t)g_context_length.load(std::memory_order_relaxed);
    cparams.n_threads    = g_num_threads.load(std::memory_order_relaxed);
    cparams.n_batch      = (uint32_t)g_batch_size.load(std::memory_order_relaxed);
    cparams.flash_attn_type = g_flash_attention.load(std::memory_order_relaxed)
        ? LLAMA_FLASH_ATTN_TYPE_ENABLED
        : LLAMA_FLASH_ATTN_TYPE_AUTO;

    llama_context *ctx = llama_init_from_model(gen_model, cparams);
    if (!ctx) {
        LOGE("nativeCreateSession: llama_init_from_model failed");
        return -1L;
    }

    int64_t handle = g_next_session_id.fetch_add(1, std::memory_order_relaxed);
    auto *ss = new SessionState();
    ss->ctx = ctx;

    {
        std::lock_guard<std::mutex> lk(g_sessions_mutex);
        g_sessions[handle] = ss;
    }

    LOGI("nativeCreateSession: handle=%" PRId64 " n_ctx=%u", handle, (unsigned)llama_n_ctx(ctx));
    return handle;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeCloseSession(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    SessionState *ss = nullptr;
    {
        std::lock_guard<std::mutex> lk(g_sessions_mutex);
        auto it = g_sessions.find((int64_t)handle);
        if (it == g_sessions.end()) return;
        ss = it->second;
        g_sessions.erase(it);
    }
    if (ss) {
        // Signal cancel, then wait for any running nativeSessionStream to finish
        // before freeing the context (prevents SIGSEGV in OpenMP worker threads).
        ss->cancel.store(true, std::memory_order_relaxed);
        std::lock_guard<std::mutex> run_lk(ss->run_mutex);
        if (ss->ctx) llama_free(ss->ctx);
        delete ss;
    }
    LOGI("nativeCloseSession: handle=%" PRId64, (int64_t)handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeSessionStream(
        JNIEnv *env, jobject /*thiz*/, jlong handle, jstring jPrompt, jobject jCallback) {

    if (!jPrompt || !jCallback) return;

    SessionState *ss = session_get((int64_t)handle);
    if (!ss || !ss->ctx || !gen_model) {
        StreamMethods m{};
        if (resolve_stream_methods(env, jCallback, m)) {
            env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("session not valid"));
        }
        return;
    }

    StreamMethods m{};
    if (!resolve_stream_methods(env, jCallback, m)) {
        LOGE("nativeSessionStream: failed to resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    if (!prompt) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("prompt decode failed"));
        return;
    }

    // Hold run_mutex for the entire inference so nativeCloseSession blocks until we exit.
    std::lock_guard<std::mutex> run_lk(ss->run_mutex);

    ss->cancel.store(false, std::memory_order_relaxed);
    llama_memory_clear(llama_get_memory(ss->ctx), false);

    const llama_vocab *vocab = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(vocab, prompt, tokens, /*add_bos*/ true, /*parse_special*/ true);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    if (n_tokens <= 0) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("tokenization failed"));
        return;
    }
    tokens.resize(n_tokens);

    const int n_ctx = (int)llama_n_ctx(ss->ctx);
    if ((int)tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

    const int n_batch_sess = (int)llama_n_batch(ss->ctx);
    if (!decode_prompt_batched(ss->ctx, tokens, n_batch_sess)) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed on prompt"));
        return;
    }

    float temperature    = g_temperature.load();
    float top_p          = g_top_p.load();
    int   top_k          = g_top_k.load();
    float repeat_penalty = g_repeat_penalty.load();
    int   max_new_tokens = g_max_new_tokens.load();

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(-1, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int cur_pos = (int)tokens.size();
    char piece_buf[768];
    char spec_buf[64];

    for (int i = 0; i < max_new_tokens; ++i) {
        if (ss->cancel.load(std::memory_order_relaxed)) break;

        llama_token tok = llama_sampler_sample(sampler, ss->ctx, -1);
        if (tok < 0) break;
        if (tok == llama_vocab_eos(vocab)) break;
        if (tok == llama_vocab_eot(vocab)) break;

        int sn = llama_token_to_piece(vocab, tok, spec_buf, (int)sizeof(spec_buf), 0, 1);
        if (sn > 0) {
            spec_buf[std::min(sn, (int)sizeof(spec_buf) - 1)] = '\0';
            if (is_eot_piece(spec_buf) || std::strcmp(spec_buf, "<start_of_turn>") == 0 || std::strcmp(spec_buf, "<|turn>") == 0) break;
        }

        llama_sampler_accept(sampler, tok);

        int nn = llama_token_to_piece(vocab, tok, piece_buf, (int)sizeof(piece_buf), 0, 0);
        if (nn > 0) {
            piece_buf[std::min(nn, (int)sizeof(piece_buf) - 1)] = '\0';
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
        step.pos[0]   = cur_pos++;
        step.n_seq_id[0] = 1;
        step.seq_id[0][0] = 0;
        step.logits[0] = true;

        if (llama_decode(ss->ctx, step) != 0) {
            llama_batch_free(step);
            env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed mid-stream"));
            llama_sampler_free(sampler);
            return;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    env->CallVoidMethod(jCallback, m.onComplete);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeSessionCancel(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    SessionState *ss = session_get((int64_t)handle);
    if (ss) ss->cancel.store(true, std::memory_order_relaxed);
}

// ===================================================================================
//                            CHAT TEMPLATE (llama_chat_apply_template)
// ===================================================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_getModelChatTemplate(
        JNIEnv *env, jobject /*thiz*/) {
    if (!gen_model) return nullptr;
    const char *tmpl = llama_model_chat_template(gen_model, /*name*/ nullptr);
    if (!tmpl) return nullptr;
    return env->NewStringUTF(tmpl);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_getModelFinetuneType(
        JNIEnv *env, jobject /*thiz*/) {
    if (!gen_model) return nullptr;
    char buf[256];
    int32_t len = llama_model_meta_val_str(gen_model, "general.finetune", buf, sizeof(buf));
    if (len < 0) return nullptr;
    return env->NewStringUTF(buf);
}

// Fallback formatter for Gemma 4 — used when llama_chat_apply_template returns -1
// because llama.cpp doesn't recognise the <|turn>/<turn|> template yet.
static bool is_gemma4_template(const char *tmpl) {
    if (!tmpl) return false;
    return std::strstr(tmpl, "<|turn>") != nullptr && std::strstr(tmpl, "<turn|>") != nullptr;
}

static std::string apply_gemma4_template(
        const std::vector<llama_chat_message> &chat,
        bool add_ass) {
    std::string out;
    for (const auto &msg : chat) {
        std::string role = msg.role;
        if (role == "assistant") role = "model";
        out += "<|turn>" + role + "\n";
        out += trim(std::string(msg.content));
        out += "<turn|>\n";
    }
    if (add_ass) out += "<|turn>model\n";
    return out;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeApplyChatTemplate(
        JNIEnv *env, jobject /*thiz*/,
        jstring jTemplate,
        jobjectArray jRoles,
        jobjectArray jContents,
        jboolean addAssistantPrefix) {

    if (!jRoles || !jContents) return nullptr;
    const jsize n = env->GetArrayLength(jRoles);
    if (n != env->GetArrayLength(jContents)) return nullptr;

    // Retrieve template string (may be null — llama_chat_apply_template accepts null)
    const char *tmpl_cstr = nullptr;
    if (jTemplate) {
        tmpl_cstr = env->GetStringUTFChars(jTemplate, nullptr);
    }

    // Build message array — keep Java strings alive until we're done
    std::vector<const char *> roles_cstr(n), contents_cstr(n);
    std::vector<jstring> role_jstrs(n), content_jstrs(n);
    for (jsize i = 0; i < n; ++i) {
        role_jstrs[i]    = (jstring) env->GetObjectArrayElement(jRoles,    i);
        content_jstrs[i] = (jstring) env->GetObjectArrayElement(jContents, i);
        roles_cstr[i]    = env->GetStringUTFChars(role_jstrs[i],    nullptr);
        contents_cstr[i] = env->GetStringUTFChars(content_jstrs[i], nullptr);
    }

    std::vector<llama_chat_message> chat(n);
    for (jsize i = 0; i < n; ++i) {
        chat[i] = { roles_cstr[i], contents_cstr[i] };
    }

    // Two-pass sizing: first call with buf=nullptr and length=-1
    int32_t needed = llama_chat_apply_template(
            tmpl_cstr, chat.data(), (size_t) n,
            (bool) addAssistantPrefix,
            nullptr, -1);

    jstring result = nullptr;
    if (needed >= 0) {
        std::string buf(needed, '\0');
        llama_chat_apply_template(
                tmpl_cstr, chat.data(), (size_t) n,
                (bool) addAssistantPrefix,
                buf.data(), needed);
        result = env->NewStringUTF(buf.c_str());
    } else if (is_gemma4_template(tmpl_cstr)) {
        std::string buf = apply_gemma4_template(chat, (bool) addAssistantPrefix);
        result = env->NewStringUTF(buf.c_str());
    }

    // Release Java strings
    for (jsize i = 0; i < n; ++i) {
        env->ReleaseStringUTFChars(role_jstrs[i],    roles_cstr[i]);
        env->ReleaseStringUTFChars(content_jstrs[i], contents_cstr[i]);
        env->DeleteLocalRef(role_jstrs[i]);
        env->DeleteLocalRef(content_jstrs[i]);
    }
    if (jTemplate && tmpl_cstr) {
        env->ReleaseStringUTFChars(jTemplate, tmpl_cstr);
    }

    return result;
}