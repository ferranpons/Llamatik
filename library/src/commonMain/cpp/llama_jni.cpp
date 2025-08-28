#include <jni.h>
#include "llama.h"
#include "llama_jni.h"
#include <libgen.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <algorithm>
#include <cstring>   // strlen
#include <cctype>    // tolower, isalpha, isdigit
#include <cstdlib>   // free
#include <string_view>

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

// ---------- Sanitizer (strong) ----------
static void drop_lines_with_prefix(std::string &s, const char *prefix_lc) {
    std::string out; out.reserve(s.size());
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

    // 1) Remove known stop markers and any model/chat headers
    for (const char* stop : { "<end_of_turn>", "<|eot_id|>", "</s>" }) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); }
    }
    drop_lines_with_prefix(s, "<start_of_turn>");
    drop_lines_with_prefix(s, "<|start_header_id|>");
    drop_lines_with_prefix(s, "<|end_header_id|>");

    // 2) If the model echoed sections, cut them off
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

    // 3) Prefer content after "ANSWER:" or "FINAL_ANSWER:"
    auto slice_after_tag = [&](const char* tag) -> bool {
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
    (void)(slice_after_tag("ANSWER:") || slice_after_tag("FINAL_ANSWER:"));

    // 4) Trim and strip leading list markers / numbering / stray punctuation
    s = trim(s);
    auto strip_leading_noise = [](std::string &t) {
        auto ltrim_str = [&](const char* prefix) -> bool {
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

    // 5) If it's still basically empty or non-alpha (e.g., "4"), fallback
    bool has_alpha = std::any_of(s.begin(), s.end(), [](unsigned char c){ return std::isalpha(c); });
    if (!has_alpha || s.size() < 12) {
        return "I don't have enough information in my sources.";
    }

    // 6) Remove any residual instruction echoes mid-string
    {
        std::string low = to_lower(s);
        const char* fragments[] = {
                "answer only from the provided context",
                "do not repeat the context",
                "respond exactly: \"i don't have enough information in my sources",
                "instructions:",
                "begin your answer",
                "start your response",
                "do not include anything else",
                "reply with only the answer text"
        };
        for (const char* f : fragments) {
            size_t p = low.find(f);
            if (p != std::string::npos) {
                s = trim(s.substr(0, p));
                break;
            }
        }
    }

    return s;
}

// ---------- Chat templating ----------
static std::string build_user_with_context(const std::string &context_block,
        const std::string &user_question) {
    auto t = [](const std::string& x){ return trim(x); };
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

    char *raw = llama_generate(prompt);
    env->ReleaseStringUTFChars(input, prompt);

    if (!raw) { LOGE("generate: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    std::free(raw);

    return env->NewStringUTF(out.c_str());
}

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

    if (trim(system).empty()) {
        system = "You are a careful assistant. Answer ONLY from the provided context. "
                 "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
                 "Write 2–5 short sentences in plain text. Do not use bullets or numbering.";
    }

    std::string user_turn = build_user_with_context(ctx, user);
    std::string prompt = build_chat_prompt_gemma(system, user_turn);

    char *raw = llama_generate(prompt.c_str());
    if (!raw) { LOGE("generateWithContext: llama_generate returned null"); return nullptr; }

    std::string out = sanitize_generation(raw);
    std::free(raw);

    return env->NewStringUTF(out.c_str());
}

extern "C"
void llama_free_cstr(char *p) {
    if (p) std::free(p);
}