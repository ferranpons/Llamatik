#include <jni.h>
#include "llama.h"
#include "llama_jni.h"
#include <libgen.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <algorithm>

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

// ---------- Output sanitization ----------
// Remove stray headers, clip on stops, prefer content after assistant header,
// and if the model echoed the user block (CONTEXT/QUESTION), extract answer or fall back.
static std::string sanitize_generation(std::string s) {
    if (s.empty()) return s;

    // Clip on common stop markers
    const char* stops[] = {"<end_of_turn>", "<|eot_id|>", "</s>"};
    for (const char* stop : stops) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); break; }
    }

    // Prefer content after assistant header if present
    const char* asst_headers[] = {"<start_of_turn>model", "<|start_header_id|>assistant<|end_header_id|>"};
    for (const char* ah : asst_headers) {
        size_t p = s.rfind(ah);
        if (p != std::string::npos) {
            size_t nl = s.find('\n', p);
            s = (nl != std::string::npos) ? s.substr(nl + 1) : s.substr(p + std::strlen(ah));
            break;
        }
    }

    // Drop any leading user/system headers that slipped in
    const char* noisy_prefixes[] = {
            "<start_of_turn>user", "<start_of_turn>system",
            "<|start_header_id|>user<|end_header_id|>",
            "<|start_header_id|>system<|end_header_id|>"
    };
    bool stripped = true;
    while (stripped) {
        stripped = false;
        for (const char* pr : noisy_prefixes) {
            if (s.compare(0, std::strlen(pr), pr) == 0) {
                size_t nl = s.find('\n');
                s = (nl != std::string::npos) ? s.substr(nl + 1) : std::string();
                stripped = true;
            }
        }
    }

    // If the model echoed the user block, try to extract answer after "QUESTION:"
    // e.g. "CONTEXT:\n...\n\nQUESTION:\n<answer>"
    std::string low = to_lower(s);
    size_t qpos = low.find("question:");
    if (qpos != std::string::npos) {
        size_t nl = s.find('\n', qpos);
        if (nl != std::string::npos) {
            s = s.substr(nl + 1);
        } else {
            s = s.substr(qpos + std::string("question:").size());
        }
    }

    // Remove any residual explicit markers from the echo
    const char* residuals[] = {"CONTEXT:", "Context:", "QUESTION:", "Question:", "<end_of_turn>user"};
    for (const char* r : residuals) {
        // If the whole thing is just the residuals+echoed text, strip lines starting with them
        size_t start = 0;
        while (true) {
            size_t line_start = s.find(r, start);
            if (line_start == std::string::npos) break;
            // only strip if at line start
            size_t prev_nl = (line_start == 0) ? 0 : s.rfind('\n', line_start - 1);
            if (line_start == 0 || (prev_nl != std::string::npos && prev_nl + 1 == line_start)) {
                size_t next_nl = s.find('\n', line_start);
                if (next_nl == std::string::npos) {
                    s.erase(line_start);
                } else {
                    s.erase(line_start, next_nl - line_start + 1);
                }
                start = 0; // restart scan after mutation
            } else {
                start = line_start + 1;
            }
        }
    }

    s = trim(s);
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
    bool success = llama_embed_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_library_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    if (!inputStr) { LOGE("embed: inputStr is null"); return nullptr; }
    float *vec = llama_embed(inputStr);
    LOGD("Vector: %p", vec);
    if (!vec) { LOGE("embed: llama_embed returned null"); env->ReleaseStringUTFChars(input, inputStr); return nullptr; }
    LOGD("Input: %s", inputStr);
    env->ReleaseStringUTFChars(input, inputStr);

    int size = llama_embedding_size();
    LOGD("Size: %d", size);
    if (size <= 0) { LOGE("embed: invalid embedding size %d", size); llama_free_embedding(vec); return nullptr; }

    jfloatArray result = env->NewFloatArray(size);
    LOGD("Result: %p", result);
    if (!result) { LOGE("embed: failed to allocate jfloatArray"); llama_free_embedding(vec); return nullptr; }

    env->SetFloatArrayRegion(result, 0, size, vec);
    llama_free_embedding(vec);
    return result;
}

// =======================================================
// ===============  Generation API  ======================
// =======================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    bool success = llama_generate_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
    const char *prompt = env->GetStringUTFChars(input, nullptr);
    if (!prompt) { LOGE("generate: prompt is null"); return nullptr; }
    const int max_log = 600;
    size_t plen = strlen(prompt);
    LOGD("Generate Prompt (<=%d chars): %.*s", max_log, (int)std::min(plen, (size_t)max_log), prompt);

    char *raw = llama_generate(prompt);
    env->ReleaseStringUTFChars(input, prompt);
    if (!raw) { LOGE("generate: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    llama_generate_free();

    const std::string fallback = "I don't have enough information in my sources.";
    // If it still looks like an echo or is empty/very short, fall back.
    std::string low = to_lower(out);
    if (out.empty() ||
            low.find("context:") == 0 ||
            low.find("<start_of_turn>") != std::string::npos ||
            low.find("<end_of_turn>user") != std::string::npos) {
        out = fallback;
    }
    if (to_lower(out).find(to_lower(fallback)) != std::string::npos) out = fallback;

    LOGD("Generate Cleaned: %s", out.c_str());
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

    // Strong, single source of truth for RAG behavior:
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

    const int max_log = 600;
    LOGD("Generate Prompt (<=%d chars): %.*s",
            max_log, (int)std::min(prompt.size(), (size_t)max_log), prompt.c_str());

    char *raw = llama_generate(prompt.c_str());
    if (!raw) { LOGE("generateWithContext: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    llama_generate_free();

    const std::string fallback = "I don't have enough information in my sources.";

    // If the model still echoed user content or left only headers, fall back.
    std::string low = to_lower(out);
    if (out.empty() ||
            low.find("context:") == 0 ||
            low.find("<start_of_turn>") != std::string::npos ||
            low.find("<end_of_turn>user") != std::string::npos) {
        out = fallback;
    }
    if (low.find(to_lower(fallback)) != std::string::npos) out = fallback;

    LOGD("GenerateWithContext Cleaned: %s", out.c_str());
    return env->NewStringUTF(out.c_str());
}
