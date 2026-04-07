#include <jni.h>
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <string>
#include <sstream>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <cstdarg>

// ===================================================================================
//                              PLATFORM LOGGING
// ===================================================================================

#if defined(__ANDROID__)
#include <android/log.h>
#define VLMI(...) __android_log_print(ANDROID_LOG_INFO,  "VLMBridge", __VA_ARGS__)
#define VLME(...) __android_log_print(ANDROID_LOG_ERROR, "VLMBridge", __VA_ARGS__)
#else
static void vlm_log_stderr(const char *level, const char *fmt, ...) {
    std::fprintf(stderr, "[VLMBridge][%s] ", level);
    va_list args;
    va_start(args, fmt);
    std::vfprintf(stderr, fmt, args);
    va_end(args);
    std::fprintf(stderr, "\n");
    std::fflush(stderr);
}
#define VLMI(...) vlm_log_stderr("I", __VA_ARGS__)
#define VLME(...) vlm_log_stderr("E", __VA_ARGS__)
#endif

// ===================================================================================
//                              GLOBAL STATE
// ===================================================================================

static struct llama_model   *mm_model         = nullptr;
static struct llama_context *mm_ctx           = nullptr;
static struct mtmd_context  *mm_mtmd          = nullptr;
static bool                  mm_backend_inited = false;
static std::atomic<bool>     mm_cancel        {false};

// ===================================================================================
//                              HELPERS
// ===================================================================================

// Re-use the StreamMethods / resolve helper pattern from llama_jni.cpp
struct VlmStreamMethods {
    jmethodID onDelta;
    jmethodID onComplete;
    jmethodID onError;
};

static bool resolve_vlm_stream_methods(JNIEnv *env, jobject cb, VlmStreamMethods &m) {
    jclass cls = env->GetObjectClass(cb);
    if (!cls) return false;
    m.onDelta    = env->GetMethodID(cls, "onDelta",    "(Ljava/lang/String;)V");
    m.onComplete = env->GetMethodID(cls, "onComplete", "()V");
    m.onError    = env->GetMethodID(cls, "onError",    "(Ljava/lang/String;)V");
    return m.onDelta && m.onComplete && m.onError;
}

static std::string build_vlm_prompt(const char *user_prompt) {
    std::ostringstream oss;
    oss << mtmd_default_marker() << "\n";
    oss << (user_prompt && user_prompt[0] ? user_prompt : "Describe this image.");
    return oss.str();
}

// Core streaming after image tokens are prefilled.
static void stream_vlm(
        JNIEnv                *env,
        const mtmd_bitmap     *bitmap,
        const char            *prompt,
        jobject                jCallback,
        const VlmStreamMethods &m) {

    if (!mm_model || !mm_ctx || !mm_mtmd || !bitmap) {
        env->CallVoidMethod(jCallback, m.onError,
                env->NewStringUTF("multimodal context not initialized"));
        return;
    }

    mm_cancel.store(false, std::memory_order_relaxed);
    llama_memory_clear(llama_get_memory(mm_ctx), false);

    // Build prompt with media marker
    std::string vlm_prompt = build_vlm_prompt(prompt);

    // Tokenize text + image
    const mtmd_bitmap *bitmaps[1] = { bitmap };

    mtmd_input_text input_text;
    input_text.text          = vlm_prompt.c_str();
    input_text.add_special   = true;
    input_text.parse_special = true;

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t tokenize_ret = mtmd_tokenize(mm_mtmd, chunks, &input_text, bitmaps, 1);
    if (tokenize_ret != 0) {
        mtmd_input_chunks_free(chunks);
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("mtmd_tokenize failed"));
        return;
    }

    // Encode image + decode all chunks (prefill)
    llama_pos n_past  = 0;
    int32_t   n_batch = (int32_t)llama_n_batch(mm_ctx);

    int32_t eval_ret = mtmd_helper_eval_chunks(
            mm_mtmd, mm_ctx,
            chunks,
            n_past,
            /*seq_id*/ 0,
            n_batch,
            /*logits_last*/ true,
            &n_past);

    mtmd_input_chunks_free(chunks);

    if (eval_ret != 0) {
        env->CallVoidMethod(jCallback, m.onError,
                env->NewStringUTF("mtmd_helper_eval_chunks failed"));
        return;
    }

    // Autoregressive token sampling
    const llama_vocab *vocab    = llama_model_get_vocab(mm_model);
    const int          n_ctx    = (int)llama_n_ctx(mm_ctx);
    const int          max_toks = 640;

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.35f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    char piece_buf[512];
    char spec_buf[64];

    for (int i = 0; i < max_toks; ++i) {
        if (mm_cancel.load(std::memory_order_relaxed)) break;
        if (n_past >= n_ctx) break;

        llama_token tok = llama_sampler_sample(sampler, mm_ctx, -1);
        if (tok < 0 || llama_vocab_is_eog(vocab, tok)) break;

        // Check for EOT special pieces
        int sn = llama_token_to_piece(vocab, tok, spec_buf, (int)sizeof(spec_buf), 0, 1);
        if (sn > 0) {
            spec_buf[std::min(sn, (int)sizeof(spec_buf) - 1)] = '\0';
            if (std::strcmp(spec_buf, "<end_of_turn>") == 0 ||
                std::strcmp(spec_buf, "<|eot_id|>") == 0 ||
                std::strcmp(spec_buf, "<|im_end|>") == 0) {
                break;
            }
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

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens     = 1;
        step.token[0]     = tok;
        step.pos[0]       = n_past++;
        step.n_seq_id[0]  = 1;
        step.seq_id[0][0] = 0;
        step.logits[0]    = true;

        if (llama_decode(mm_ctx, step) != 0) {
            llama_batch_free(step);
            llama_sampler_free(sampler);
            env->CallVoidMethod(jCallback, m.onError,
                    env->NewStringUTF("llama_decode failed mid-stream"));
            return;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    env->CallVoidMethod(jCallback, m.onComplete);
}

// ===================================================================================
//                              JNI ENTRY POINTS
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_MultimodalBridge_initModel(
        JNIEnv *env, jobject,
        jstring jModelPath, jstring jMmprojPath) {

    // Release previously loaded resources
    if (mm_mtmd)  { mtmd_free(mm_mtmd);         mm_mtmd  = nullptr; }
    if (mm_ctx)   { llama_free(mm_ctx);          mm_ctx   = nullptr; }
    if (mm_model) { llama_model_free(mm_model);  mm_model = nullptr; }

    if (!jModelPath || !jMmprojPath) {
        VLME("initModel: null path argument");
        return JNI_FALSE;
    }

    if (!mm_backend_inited) {
        llama_backend_init();
        mm_backend_inited = true;
    }

    const char *modelPath  = env->GetStringUTFChars(jModelPath,  nullptr);
    const char *mmprojPath = env->GetStringUTFChars(jMmprojPath, nullptr);

    VLMI("initModel: loading model %s", modelPath ? modelPath : "(null)");

    llama_model_params mparams = llama_model_default_params();
    mm_model = llama_model_load_from_file(modelPath, mparams);

    if (!mm_model) {
        VLME("initModel: failed to load model");
        env->ReleaseStringUTFChars(jModelPath,  modelPath);
        env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = 8192;
    cparams.embeddings = false;

    mm_ctx = llama_init_from_model(mm_model, cparams);
    if (!mm_ctx) {
        VLME("initModel: failed to create llama_context");
        llama_model_free(mm_model);
        mm_model = nullptr;
        env->ReleaseStringUTFChars(jModelPath,  modelPath);
        env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);
        return JNI_FALSE;
    }

    VLMI("initModel: loading mmproj %s", mmprojPath ? mmprojPath : "(null)");

    struct mtmd_context_params mctx_params = mtmd_context_params_default();
    mctx_params.use_gpu       = true;
    mctx_params.print_timings = false;
    mctx_params.n_threads     = 4;
    mctx_params.warmup        = false;

    mm_mtmd = mtmd_init_from_file(mmprojPath, mm_model, mctx_params);

    env->ReleaseStringUTFChars(jModelPath,  modelPath);
    env->ReleaseStringUTFChars(jMmprojPath, mmprojPath);

    if (!mm_mtmd) {
        VLME("initModel: failed to init mtmd context");
        llama_free(mm_ctx);          mm_ctx   = nullptr;
        llama_model_free(mm_model);  mm_model = nullptr;
        return JNI_FALSE;
    }

    VLMI("initModel: ready. n_ctx=%u", (unsigned)llama_n_ctx(mm_ctx));
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_MultimodalBridge_nativeAnalyzeImageBytesStream(
        JNIEnv *env, jobject,
        jbyteArray jImageBytes, jstring jPrompt, jobject jCallback) {

    if (!jCallback) return;

    VlmStreamMethods m{};
    if (!resolve_vlm_stream_methods(env, jCallback, m)) {
        VLME("nativeAnalyzeImageBytesStream: failed to resolve callback methods");
        return;
    }

    if (!jImageBytes || !jPrompt) {
        env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("null image or prompt"));
        return;
    }

    jsize  len   = env->GetArrayLength(jImageBytes);
    jbyte *bytes = env->GetByteArrayElements(jImageBytes, nullptr);

    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(
            mm_mtmd,
            reinterpret_cast<const unsigned char *>(bytes),
            static_cast<size_t>(len));

    env->ReleaseByteArrayElements(jImageBytes, bytes, JNI_ABORT);

    if (!bitmap) {
        env->CallVoidMethod(jCallback, m.onError,
                env->NewStringUTF("failed to decode image bytes"));
        return;
    }

    const char *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    stream_vlm(env, bitmap, prompt, jCallback, m);
    env->ReleaseStringUTFChars(jPrompt, prompt);
    mtmd_bitmap_free(bitmap);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_MultimodalBridge_cancelAnalysis(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    VLMI("cancelAnalysis: cancel requested");
    mm_cancel.store(true, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_MultimodalBridge_release(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    if (mm_mtmd)  { mtmd_free(mm_mtmd);         mm_mtmd  = nullptr; }
    if (mm_ctx)   { llama_free(mm_ctx);          mm_ctx   = nullptr; }
    if (mm_model) { llama_model_free(mm_model);  mm_model = nullptr; }
    if (mm_backend_inited) {
        llama_backend_free();
        mm_backend_inited = false;
    }
}
