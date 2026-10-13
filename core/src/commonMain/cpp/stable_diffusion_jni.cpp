#include <jni.h>

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "stable-diffusion.h"

// -----------------------------------------------------------------------------
// Minimal JNI wrapper for leejet/stable-diffusion.cpp
//
// Exposes:
//  - StableDiffusionBridge.initModel(modelPath, threads)
//  - StableDiffusionBridge.txt2img(...)
//  - StableDiffusionBridge.release()
//
// We keep a single global context (matches how LlamaBridge/WhisperBridge are used).
// -----------------------------------------------------------------------------

static sd_ctx_t * g_sd_ctx = nullptr;

static std::string jstring_to_utf8(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out = c ? c : "";
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_llamatik_core_platform_StableDiffusionBridge_initModel(
        JNIEnv * env,
        jobject,
        jstring modelPath,
        jint threads
) {
    // Clean previous context
    if (g_sd_ctx) {
        free_sd_ctx(g_sd_ctx);
        g_sd_ctx = nullptr;
    }

    const std::string path = jstring_to_utf8(env, modelPath);
    if (path.empty()) return JNI_FALSE;

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);

    params.model_path = path.c_str();
    params.n_threads  = (int) threads;

    // Reasonable defaults for mobile/desktop
    params.enable_mmap = true;

    g_sd_ctx = new_sd_ctx(&params);
    return g_sd_ctx ? JNI_TRUE : JNI_FALSE;
}

static inline void rgba_from_rgb(
        const uint8_t * rgb,
        int pixelCount,
        std::vector<uint8_t> & out
) {
    out.resize((size_t)pixelCount * 4);
    size_t si = 0;
    size_t di = 0;
    for (int i = 0; i < pixelCount; i++) {
        out[di++] = rgb[si++];
        out[di++] = rgb[si++];
        out[di++] = rgb[si++];
        out[di++] = 255;
    }
}

static inline void rgba_from_gray(
        const uint8_t * gray,
        int pixelCount,
        std::vector<uint8_t> & out
) {
    out.resize((size_t)pixelCount * 4);
    size_t si = 0;
    size_t di = 0;
    for (int i = 0; i < pixelCount; i++) {
        const uint8_t v = gray[si++];
        out[di++] = v;
        out[di++] = v;
        out[di++] = v;
        out[di++] = 255;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_llamatik_core_platform_StableDiffusionBridge_txt2img(
        JNIEnv * env,
        jobject,
        jstring prompt,
        jstring negativePrompt,
        jint width,
        jint height,
        jint steps,
        jfloat cfgScale,
        jlong seed
) {
    if (!g_sd_ctx) {
        return env->NewByteArray(0);
    }

    const std::string p = jstring_to_utf8(env, prompt);
    const std::string n = jstring_to_utf8(env, negativePrompt);
    if (p.empty()) {
        return env->NewByteArray(0);
    }

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);

    gen.prompt = p.c_str();
    gen.negative_prompt = n.c_str();
    gen.width  = (int) width;
    gen.height = (int) height;
    gen.seed   = (int64_t) seed;

    gen.sample_params.sample_steps = (int) steps;
    gen.sample_params.guidance.txt_cfg = (float) cfgScale;

    sd_image_t * images_out = nullptr;
    int num_images = 0;
    bool ok = generate_image(g_sd_ctx, &gen, &images_out, &num_images);
    if (!ok || num_images == 0 || !images_out || !images_out[0].data ||
            images_out[0].width == 0 || images_out[0].height == 0) {
        free_sd_images(images_out, num_images);
        return env->NewByteArray(0);
    }
    sd_image_t * img = &images_out[0];

    const int w = (int) img->width;
    const int h = (int) img->height;
    const int ch = (int) img->channel;
    const int pixelCount = w * h;

    std::vector<uint8_t> rgba;
    if (ch == 4) {
        rgba.assign(img->data, img->data + (size_t)pixelCount * 4);
    } else if (ch == 3) {
        rgba_from_rgb(img->data, pixelCount, rgba);
    } else if (ch == 1) {
        rgba_from_gray(img->data, pixelCount, rgba);
    } else {
        free_sd_images(images_out, num_images);
        return env->NewByteArray(0);
    }

    free_sd_images(images_out, num_images);

    jbyteArray out = env->NewByteArray((jsize) rgba.size());
    if (!out) return env->NewByteArray(0);
    env->SetByteArrayRegion(out, 0, (jsize) rgba.size(), (const jbyte*) rgba.data());
    return out;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_llamatik_core_platform_StableDiffusionBridge_img2img(
        JNIEnv * env,
        jobject,
        jbyteArray initImageRgba,
        jint initImageW,
        jint initImageH,
        jstring prompt,
        jstring negativePrompt,
        jint width,
        jint height,
        jint steps,
        jfloat cfgScale,
        jfloat strength,
        jlong seed
) {
    if (!g_sd_ctx) {
        return env->NewByteArray(0);
    }

    const std::string p = jstring_to_utf8(env, prompt);
    const std::string n = jstring_to_utf8(env, negativePrompt);
    if (p.empty()) {
        return env->NewByteArray(0);
    }

    jsize initSize = env->GetArrayLength(initImageRgba);
    jbyte * initBytes = env->GetByteArrayElements(initImageRgba, nullptr);
    if (!initBytes || initSize == 0) {
        if (initBytes) env->ReleaseByteArrayElements(initImageRgba, initBytes, JNI_ABORT);
        return env->NewByteArray(0);
    }

    const int pixelCount = (int)initImageW * (int)initImageH;
    uint8_t * initCopy = (uint8_t *) std::malloc((size_t)pixelCount * 4);
    if (!initCopy) {
        env->ReleaseByteArrayElements(initImageRgba, initBytes, JNI_ABORT);
        return env->NewByteArray(0);
    }
    std::memcpy(initCopy, initBytes, (size_t)pixelCount * 4);
    env->ReleaseByteArrayElements(initImageRgba, initBytes, JNI_ABORT);

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);

    gen.prompt = p.c_str();
    gen.negative_prompt = n.c_str();
    gen.width    = (int) width;
    gen.height   = (int) height;
    gen.seed     = (int64_t) seed;
    gen.strength = (float) strength;

    gen.sample_params.sample_steps = (int) steps;
    gen.sample_params.guidance.txt_cfg = (float) cfgScale;

    gen.init_image.data    = initCopy;
    gen.init_image.width   = (uint32_t) initImageW;
    gen.init_image.height  = (uint32_t) initImageH;
    gen.init_image.channel = 4;

    sd_image_t * images_out = nullptr;
    int num_images = 0;
    bool ok = generate_image(g_sd_ctx, &gen, &images_out, &num_images);
    std::free(initCopy);

    if (!ok || num_images == 0 || !images_out || !images_out[0].data ||
            images_out[0].width == 0 || images_out[0].height == 0) {
        free_sd_images(images_out, num_images);
        return env->NewByteArray(0);
    }
    sd_image_t * img = &images_out[0];

    const int w = (int) img->width;
    const int h = (int) img->height;
    const int ch = (int) img->channel;
    const int outPixelCount = w * h;

    std::vector<uint8_t> rgba;
    if (ch == 4) {
        rgba.assign(img->data, img->data + (size_t)outPixelCount * 4);
    } else if (ch == 3) {
        rgba_from_rgb(img->data, outPixelCount, rgba);
    } else if (ch == 1) {
        rgba_from_gray(img->data, outPixelCount, rgba);
    } else {
        free_sd_images(images_out, num_images);
        return env->NewByteArray(0);
    }

    free_sd_images(images_out, num_images);

    jbyteArray out = env->NewByteArray((jsize) rgba.size());
    if (!out) return env->NewByteArray(0);
    env->SetByteArrayRegion(out, 0, (jsize) rgba.size(), (const jbyte*) rgba.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_llamatik_core_platform_StableDiffusionBridge_release(
        JNIEnv *,
jobject
) {
if (g_sd_ctx) {
free_sd_ctx(g_sd_ctx);
g_sd_ctx = nullptr;
}
}
