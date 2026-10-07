#include <jni.h>
#include "llama_embed.h"

#include <cstring>
#include <cstdlib>
#include <cstdarg>
#include <string>
#include <vector>
#include <cinttypes>

// Returns the number of bytes in the longest valid UTF-8 prefix of [data, data+len).
// Any incomplete multi-byte sequence at the end is excluded.
static size_t utf8_complete_prefix_len(const char *data, size_t len) {
    if (len == 0) return 0;
    size_t i = 0;
    while (i < len) {
        unsigned char c = (unsigned char)data[i];
        int seq;
        if      (c < 0x80)                    seq = 1;
        else if ((c & 0xE0) == 0xC0)          seq = 2;
        else if ((c & 0xF0) == 0xE0)          seq = 3;
        else if ((c & 0xF8) == 0xF0)          seq = 4;
        else { ++i; continue; }  // invalid lead byte — skip
        if (i + (size_t)seq > len) break;     // incomplete sequence at end
        i += seq;
    }
    return i;
}

#if defined(__APPLE__)
#include <cstdlib>
#endif

#if defined(__ANDROID__)
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "LlamaBridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaBridge", __VA_ARGS__)
#else
static void log_stderr(const char *level, const char *fmt, ...) {
    std::fprintf(stderr, "[LlamaBridge][%s] ", level);
    va_list args; va_start(args, fmt);
    std::vfprintf(stderr, fmt, args); va_end(args);
    std::fprintf(stderr, "\n"); std::fflush(stderr);
}
#define LOGI(...) log_stderr("I", __VA_ARGS__)
#define LOGE(...) log_stderr("E", __VA_ARGS__)
#endif

// =============================================================================
//  JNI_OnLoad — On macOS, disable bf16 Metal pipelines before backend init.
// =============================================================================
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * /*vm*/, void * /*reserved*/) {
#if defined(__APPLE__) && !defined(__ANDROID__)
    if (getenv("GGML_METAL_BF16_DISABLE") == nullptr) {
        setenv("GGML_METAL_BF16_DISABLE", "1", /*overwrite=*/0);
    }
#endif
    return JNI_VERSION_1_6;
}

// =============================================================================
//  JNI callback bridging helpers
// =============================================================================

struct JniStreamCtx {
    JNIEnv   *env;
    jobject   callback;
    jmethodID onDelta;
    jmethodID onComplete;
    jmethodID onError;
    std::string utf8_buf;  // incomplete multi-byte UTF-8 tail from the previous chunk
};

static bool resolve_stream_methods(JNIEnv *env, jobject cb,
        jmethodID &onDelta, jmethodID &onComplete, jmethodID &onError) {
    jclass cls = env->GetObjectClass(cb);
    if (!cls) return false;
    onDelta    = env->GetMethodID(cls, "onDelta",    "(Ljava/lang/String;)V");
    onComplete = env->GetMethodID(cls, "onComplete", "()V");
    onError    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");
    return onDelta && onComplete && onError;
}

static void jni_flush_utf8(JniStreamCtx *c) {
    if (c->utf8_buf.empty()) return;
    size_t safe = utf8_complete_prefix_len(c->utf8_buf.data(), c->utf8_buf.size());
    if (safe == 0) return;
    std::string to_send(c->utf8_buf.data(), safe);
    c->utf8_buf.erase(0, safe);
    jstring js = c->env->NewStringUTF(to_send.c_str());
    if (js) { c->env->CallVoidMethod(c->callback, c->onDelta, js); c->env->DeleteLocalRef(js); }
}

static void jni_on_delta(const char *text, void *user) {
    auto *c = static_cast<JniStreamCtx *>(user);
    if (!text || !c->env) return;
    c->utf8_buf.append(text);
    jni_flush_utf8(c);
}

static void jni_on_done(void *user) {
    auto *c = static_cast<JniStreamCtx *>(user);
    jni_flush_utf8(c);  // flush any complete UTF-8 bytes still in the buffer
    c->env->CallVoidMethod(c->callback, c->onComplete);
}

static void jni_on_error(const char *msg, void *user) {
    auto *c = static_cast<JniStreamCtx *>(user);
    jstring js = c->env->NewStringUTF(msg ? msg : "unknown error");
    if (js) { c->env->CallVoidMethod(c->callback, c->onError, js); c->env->DeleteLocalRef(js); }
}

static jfloatArray make_empty_float_array(JNIEnv *env) { return env->NewFloatArray(0); }

// =============================================================================
//  Embeddings
// =============================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_initEmbedModel(
        JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    const bool ok = llama_embed_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_core_platform_LlamaBridge_embed(
        JNIEnv *env, jobject, jstring input) {
    if (!input) return make_empty_float_array(env);
    const char *s = env->GetStringUTFChars(input, nullptr);
    if (!s) return make_empty_float_array(env);

    float *emb = llama_embed(s);
    env->ReleaseStringUTFChars(input, s);
    if (!emb) return make_empty_float_array(env);

    const int dim = llama_embedding_size();
    if (dim <= 0) { llama_free_embedding(emb); return make_empty_float_array(env); }

    jfloatArray result = env->NewFloatArray(dim);
    if (!result) { llama_free_embedding(emb); return make_empty_float_array(env); }
    env->SetFloatArrayRegion(result, 0, dim, emb);
    llama_free_embedding(emb);
    return result;
}

// =============================================================================
//  MTP
// =============================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeInitMtp(
        JNIEnv *env, jobject, jstring jPath, jint draftLen) {
    const char *path = jPath ? env->GetStringUTFChars(jPath, nullptr) : nullptr;
    if (!path) return JNI_FALSE;
    const bool ok = llama_mtp_init(path, (int)draftLen);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeShutdownMtp(
        JNIEnv * /*env*/, jobject) {
    llama_mtp_shutdown();
}

// =============================================================================
//  Text generation (blocking)
// =============================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_initGenerateModel(
        JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    const bool ok = llama_generate_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_generate(
        JNIEnv *env, jobject, jstring jPrompt) {
    const char *p = jPrompt ? env->GetStringUTFChars(jPrompt, nullptr) : nullptr;
    char *r = llama_generate(p);
    if (p) env->ReleaseStringUTFChars(jPrompt, p);
    jstring js = env->NewStringUTF(r ? r : "");
    if (r) std::free(r);
    return js;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_generateWithContext(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser) {
    const char *ps = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
    const char *pc = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pu = jUser    ? env->GetStringUTFChars(jUser,    nullptr) : nullptr;

    char *r = llama_generate_chat(ps, pc, pu);

    if (jSystem)  env->ReleaseStringUTFChars(jSystem,  ps);
    if (jContext) env->ReleaseStringUTFChars(jContext, pc);
    if (jUser)    env->ReleaseStringUTFChars(jUser,    pu);

    jstring js = env->NewStringUTF(r ? r : "");
    if (r) std::free(r);
    return js;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_generateJson(
        JNIEnv *env, jobject, jstring jPrompt, jstring jSchema) {
    const char *pp = jPrompt ? env->GetStringUTFChars(jPrompt, nullptr) : nullptr;
    const char *ps = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    char *r = llama_generate_json_schema(pp, ps);

    if (jPrompt) env->ReleaseStringUTFChars(jPrompt, pp);
    if (jSchema) env->ReleaseStringUTFChars(jSchema, ps);

    jstring js = env->NewStringUTF(r ? r : "");
    if (r) std::free(r);
    return js;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_generateJsonWithContext(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser, jstring jSchema) {
    const char *ps = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
    const char *pc = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pu = jUser    ? env->GetStringUTFChars(jUser,    nullptr) : nullptr;
    const char *psc= jSchema  ? env->GetStringUTFChars(jSchema,  nullptr) : nullptr;

    char *r = llama_generate_chat_json_schema(ps, pc, pu, psc);

    if (jSystem)  env->ReleaseStringUTFChars(jSystem,  ps);
    if (jContext) env->ReleaseStringUTFChars(jContext, pc);
    if (jUser)    env->ReleaseStringUTFChars(jUser,    pu);
    if (jSchema)  env->ReleaseStringUTFChars(jSchema,  psc);

    jstring js = env->NewStringUTF(r ? r : "");
    if (r) std::free(r);
    return js;
}

// =============================================================================
//  Streaming
// =============================================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateStream(
        JNIEnv *env, jobject, jstring jPrompt, jobject jCb) {
    if (!jPrompt || !jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) {
        LOGE("nativeGenerateStream: cannot resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_generate_stream(prompt, jni_on_delta, jni_on_done, jni_on_error, &ctx);
    env->ReleaseStringUTFChars(jPrompt, prompt);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateWithContextStream(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser, jobject jCb) {
    if (!jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) return;

    const char *ps = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
    const char *pc = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pu = jUser    ? env->GetStringUTFChars(jUser,    nullptr) : nullptr;

    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_generate_chat_stream(ps, pc, pu, jni_on_delta, jni_on_done, jni_on_error, &ctx);

    if (jSystem)  env->ReleaseStringUTFChars(jSystem,  ps);
    if (jContext) env->ReleaseStringUTFChars(jContext, pc);
    if (jUser)    env->ReleaseStringUTFChars(jUser,    pu);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateJsonStream(
        JNIEnv *env, jobject, jstring jPrompt, jstring jSchema, jobject jCb) {
    if (!jPrompt || !jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) return;

    const char *pp = env->GetStringUTFChars(jPrompt, nullptr);
    const char *ps = jSchema ? env->GetStringUTFChars(jSchema, nullptr) : nullptr;

    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_generate_json_schema_stream(pp, ps, jni_on_delta, jni_on_done, jni_on_error, &ctx);

    env->ReleaseStringUTFChars(jPrompt, pp);
    if (jSchema) env->ReleaseStringUTFChars(jSchema, ps);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateJsonWithContextStream(
        JNIEnv *env, jobject,
        jstring jSystem, jstring jContext, jstring jUser, jstring jSchema, jobject jCb) {
    if (!jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) return;

    const char *ps  = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
    const char *pc  = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
    const char *pu  = jUser    ? env->GetStringUTFChars(jUser,    nullptr) : nullptr;
    const char *psc = jSchema  ? env->GetStringUTFChars(jSchema,  nullptr) : nullptr;

    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_generate_chat_json_schema_stream(ps, pc, pu, psc, jni_on_delta, jni_on_done, jni_on_error, &ctx);

    if (jSystem)  env->ReleaseStringUTFChars(jSystem,  ps);
    if (jContext) env->ReleaseStringUTFChars(jContext, pc);
    if (jUser)    env->ReleaseStringUTFChars(jUser,    pu);
    if (jSchema)  env->ReleaseStringUTFChars(jSchema,  psc);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeCancelGenerate(
        JNIEnv * /*env*/, jobject) {
    llama_generate_cancel();
}

// =============================================================================
//  Generation parameters
// =============================================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeUpdateGenerationParams(
        JNIEnv * /*env*/, jobject,
        jfloat temperature, jint maxTokens, jfloat topP, jint topK, jfloat repeatPenalty,
        jint contextLength, jint numThreads, jboolean useMmap,
        jboolean flashAttention, jint batchSize, jint gpuLayers) {
    llama_generate_set_params((float)temperature, (int)maxTokens, (float)topP, (int)topK,
            (float)repeatPenalty, (int)contextLength, (int)numThreads,
            (bool)useMmap, (bool)flashAttention, (int)batchSize, (int)gpuLayers);
}

// =============================================================================
//  KV session (global)
// =============================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionReset(
        JNIEnv * /*env*/, jobject) {
    return llama_generate_session_reset() ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionSave(
        JNIEnv *env, jobject, jstring jPath) {
    if (!jPath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    const bool ok = llama_generate_session_save(path);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionLoad(
        JNIEnv *env, jobject, jstring jPath) {
    if (!jPath) return JNI_FALSE;
    const char *path = env->GetStringUTFChars(jPath, nullptr);
    const bool ok = llama_generate_session_load(path);
    env->ReleaseStringUTFChars(jPath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateContinue(
        JNIEnv *env, jobject, jstring jPrompt) {
    const char *p = jPrompt ? env->GetStringUTFChars(jPrompt, nullptr) : nullptr;
    char *r = llama_generate_continue(p);
    if (p) env->ReleaseStringUTFChars(jPrompt, p);
    jstring js = env->NewStringUTF(r ? r : "");
    if (r) std::free(r);
    return js;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeGenerateContinueStream(
        JNIEnv *env, jobject, jstring jPrompt, jobject jCb) {
    if (!jPrompt || !jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) {
        LOGE("nativeGenerateContinueStream: cannot resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_generate_continue_stream(prompt, jni_on_delta, jni_on_done, jni_on_error, &ctx);
    env->ReleaseStringUTFChars(jPrompt, prompt);
}

// =============================================================================
//  Concurrent sessions
// =============================================================================

extern "C"
JNIEXPORT jlong JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeCreateSession(
        JNIEnv * /*env*/, jobject) {
    return (jlong)llama_session_create();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeCloseSession(
        JNIEnv * /*env*/, jobject, jlong handle) {
    llama_session_close((int64_t)handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionStream(
        JNIEnv *env, jobject, jlong handle, jstring jPrompt, jobject jCb) {
    if (!jPrompt || !jCb) return;

    jmethodID onDelta, onComplete, onError;
    if (!resolve_stream_methods(env, jCb, onDelta, onComplete, onError)) {
        LOGE("nativeSessionStream: cannot resolve callback methods");
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    JniStreamCtx ctx{env, jCb, onDelta, onComplete, onError};
    llama_session_stream((int64_t)handle, prompt, jni_on_delta, jni_on_done, jni_on_error, &ctx);
    env->ReleaseStringUTFChars(jPrompt, prompt);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionClearKv(
        JNIEnv * /*env*/, jobject, jlong handle) {
    llama_session_reset((int64_t)handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeSessionCancel(
        JNIEnv * /*env*/, jobject, jlong handle) {
    llama_session_cancel((int64_t)handle);
}

// =============================================================================
//  Chat template / model metadata
// =============================================================================

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_getModelChatTemplate(
        JNIEnv *env, jobject) {
    const char *t = llama_get_model_chat_template();
    return t ? env->NewStringUTF(t) : nullptr;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_getModelFinetuneType(
        JNIEnv *env, jobject) {
    char *t = llama_get_model_finetune_type();
    if (!t) return nullptr;
    jstring js = env->NewStringUTF(t);
    std::free(t);
    return js;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_llamatik_core_platform_LlamaBridge_nativeApplyChatTemplate(
        JNIEnv *env, jobject,
        jstring jTemplate,
        jobjectArray jRoles,
        jobjectArray jContents,
        jboolean addAssistantPrefix) {
    if (!jRoles || !jContents) return nullptr;
    const jsize n = env->GetArrayLength(jRoles);
    if (n != env->GetArrayLength(jContents)) return nullptr;

    std::vector<const char *> roles_c(n), contents_c(n);
    std::vector<jstring> role_js(n), content_js(n);
    for (jsize i = 0; i < n; ++i) {
        role_js[i]    = (jstring)env->GetObjectArrayElement(jRoles,    i);
        content_js[i] = (jstring)env->GetObjectArrayElement(jContents, i);
        roles_c[i]    = env->GetStringUTFChars(role_js[i],    nullptr);
        contents_c[i] = env->GetStringUTFChars(content_js[i], nullptr);
    }

    char *r = llama_apply_chat_template(roles_c.data(), contents_c.data(), (int)n, (bool)addAssistantPrefix);

    for (jsize i = 0; i < n; ++i) {
        env->ReleaseStringUTFChars(role_js[i],    roles_c[i]);
        env->ReleaseStringUTFChars(content_js[i], contents_c[i]);
        env->DeleteLocalRef(role_js[i]);
        env->DeleteLocalRef(content_js[i]);
    }
    (void)jTemplate; // template lookup is done inside the C API

    jstring js = r ? env->NewStringUTF(r) : nullptr;
    if (r) std::free(r);
    return js;
}

// =============================================================================
//  Shutdown
// =============================================================================

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_LlamaBridge_shutdown(
        JNIEnv * /*env*/, jobject) {
    llama_embed_free();
    llama_generate_free();
}
