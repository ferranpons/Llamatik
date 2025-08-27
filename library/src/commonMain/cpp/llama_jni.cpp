#include <jni.h>
#include "llama.h"
#include "llama_jni.h"
#include <libgen.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <algorithm>
#include <cstring>   // std::strlen
#include <cctype>    // std::tolower
#include <cstdlib>   // std::free

// Log tags
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "LlamaBridge", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "LlamaBridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaBridge", __VA_ARGS__)

// ---------- Small helpers ----------
static inline std::string trim(const std::string &s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}
static inline std::string to_lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c){ return (char)std::tolower(c); });
    return s;
}
static inline bool starts_with(const std::string &s, const char *pfx) {
    const size_t n = std::strlen(pfx);
    return s.size() >= n && std::memcmp(s.data(), pfx, n) == 0;
}

// ---------- Output sanitization (conservative) ----------
static std::string sanitize_generation(std::string s) {
    if (s.empty()) return s;

    // Clip on common stop markers
    const char* stops[] = {"<end_of_turn>", "<|eot_id|>", "</s>"};
    for (const char* stop : stops) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); break; }
    }

    // Prefer content after assistant header if present (keep conservative)
    const char* asst_headers[] = {
            "<start_of_turn>model",
            "<|start_header_id|>assistant<|end_header_id|>"
    };
    for (const char* ah : asst_headers) {
        size_t p = s.rfind(ah);
        if (p != std::string::npos) {
            size_t nl = s.find('\n', p);
            s = (nl != std::string::npos) ? s.substr(nl + 1) : s.substr(p + std::strlen(ah));
            break;
        }
    }

    // If the whole thing begins with noisy user/system headers, trim only at the very start
    const char* noisy_prefixes[] = {
            "<start_of_turn>user", "<start_of_turn>system",
            "<|start_header_id|>user<|end_header_id|>",
            "<|start_header_id|>system<|end_header_id|>"
    };
    bool stripped = true;
    while (stripped) {
        stripped = false;
        for (const char* pr : noisy_prefixes) {
            if (starts_with(s, pr)) {
                size_t nl = s.find('\n');
                s = (nl != std::string::npos) ? s.substr(nl + 1) : std::string();
                stripped = true;
            }
        }
    }

    // Final tidy
    s = trim(s);

    // If it's still basically empty or just repeated headers, leave as-is; outer layer will decide fallback.
    return s;
}

// ---------- Chat templating ----------
static std::string build_chat_prompt_gemma(const std::string &system_msg,
        const std::string &user_msg) {
    std::ostringstream oss;
    oss << "<start_of_turn>system\n"
        << (system_msg.empty() ? "You are a helpful assistant." : system_msg)
        << "\n<end_of_turn>\n"
        << "<start_of_turn>user\n" << user_msg << "\n<end_of_turn>\n"
        // Anchor the model to answer, and not re-open a user turn.
        << "<start_of_turn>model\n";
    return oss.str();
}

static std::string build_user_with_context(const std::string &context_block,
        const std::string &user_question) {
    if (trim(context_block).empty()) return "QUESTION:\n" + user_question;
    std::ostringstream oss;
    oss << "CONTEXT:\n" << context_block << "\n\nQUESTION:\n" << user_question;
    return oss.str();
}

// =======================================================
// ===============  Embedding API  =======================
// =======================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initModel (embed): %s", path ? path : "(null)");
    bool success = llama_embed_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("initModel (embed) -> %s", success ? "ok" : "FAIL");
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_library_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    if (!inputStr) { LOGE("embed: inputStr is null"); return nullptr; }
    LOGD("embed() text (first 256 chars): %.256s", inputStr);

    float *vec = llama_embed(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);

    if (!vec) { LOGE("embed: llama_embed returned null"); return nullptr; }

    int size = llama_embedding_size();
    if (size <= 0) { LOGE("embed: invalid embedding size %d", size); llama_free_embedding(vec); return nullptr; }

    jfloatArray result = env->NewFloatArray(size);
    if (!result) { LOGE("embed: failed to allocate jfloatArray"); llama_free_embedding(vec); return nullptr; }

    env->SetFloatArrayRegion(result, 0, size, vec);
    llama_free_embedding(vec);
    LOGD("embed() -> %d dims", size);
    return result;
}

// =======================================================
// ===============  Generation API  ======================
// =======================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initGenerateModel: %s", path ? path : "(null)");
    bool success = llama_generate_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    LOGI("initGenerateModel -> %s", success ? "ok" : "FAIL");
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
    const char *prompt = env->GetStringUTFChars(input, nullptr);
    if (!prompt) { LOGE("generate: prompt is null"); return nullptr; }

    const int max_log = 900;
    size_t plen = std::strlen(prompt);
    LOGD("Generate() prompt (<=%d chars): %.*s", max_log, (int)std::min(plen, (size_t)max_log), prompt);

    char *raw = llama_generate(prompt);
    env->ReleaseStringUTFChars(input, prompt);

    if (!raw) { LOGE("generate: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    std::free(raw);                 // <<< DO NOT tear down the model here
    // llama_generate_free();       // <<< removed (keeps model loaded between turns)

    const std::string fallback = "I don't have enough information in my sources.";
    if (trim(out).empty()) out = fallback;

    LOGD("Generate() cleaned: %.400s", out.c_str());
    return env->NewStringUTF(out.c_str());
}

// =======================================================
// === Chat-style overload with strict RAG instructions ===
// =======================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generateWithContext(
        JNIEnv *env, jobject,
        jstring jSystem, jstring jContext, jstring jUser) {

    const char *psys = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
    const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pusr =            env->GetStringUTFChars(jUser,    nullptr);

    std::string system = psys ? psys : "";
    std::string ctx    = pctx ? pctx : "";
    std::string user   = pusr ? pusr : "";

    if (jSystem)  env->ReleaseStringUTFChars(jSystem,  psys);
    if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
    if (jUser)    env->ReleaseStringUTFChars(jUser,    pusr);

    std::string strict_system =
            (trim(system).empty()
                    ? "You are a careful assistant. Answer ONLY from the provided context. "
                      "If the context is insufficient, answer exactly: \"I don't have enough information in my sources.\" "
                      "Do NOT repeat the context or any headers; provide only the answer."
                    : system + "\n\nAnswer ONLY from the provided context. "
                               "If the context is insufficient, answer exactly: \"I don't have enough information in my sources.\" "
                               "Do NOT repeat the context or any headers; provide only the answer.");

    std::string user_turn = build_user_with_context(ctx, user);
    std::string prompt = build_chat_prompt_gemma(strict_system, user_turn);

    LOGD("GenerateWithContext() ctx_len=%d, user_len=%d", (int)ctx.size(), (int)user.size());
    const int max_log = 900;
    LOGD("GWC prompt (<=%d chars): %.*s",
            max_log, (int)std::min(prompt.size(), (size_t)max_log), prompt.c_str());

    char *raw = llama_generate(prompt.c_str());
    if (!raw) { LOGE("generateWithContext: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    std::free(raw);                 // <<< keep model loaded
    // llama_generate_free();       // <<< removed

    const std::string fallback = "I don't have enough information in my sources.";
    if (trim(out).empty()) out = fallback;

    LOGD("GenerateWithContext() cleaned: %.400s", out.c_str());
    return env->NewStringUTF(out.c_str());
}