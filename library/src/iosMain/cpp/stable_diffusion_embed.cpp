#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "stable-diffusion.h"

// iOS wrapper similar to your whisper_stt.cpp / llama_embed.cpp style.
//
// Exposes a very small C ABI for Kotlin/Native cinterop:
//
//  - sd_init(model_path, threads) -> 1 success, 0 fail
//  - sd_txt2img_rgba(prompt, negative, w,h, steps, cfgScale, seed,
//                    outW, outH, outSize) -> malloc'd RGBA bytes (outSize bytes)
//  - sd_free_bytes(ptr)
//  - sd_release()

static sd_ctx_t * g_sd_ctx = nullptr;

extern "C" {

// Returns 1 on success, 0 on failure.
int32_t sd_init(const char * model_path, int32_t threads) {
    if (g_sd_ctx) {
        free_sd_ctx(g_sd_ctx);
        g_sd_ctx = nullptr;
    }

    if (!model_path || model_path[0] == '\0') {
        std::fprintf(stderr, "sd_init: model_path is null/empty\n");
        return 0;
    }

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);

    params.model_path = model_path;
    params.n_threads  = (int) threads;

    // Reasonable defaults (same intent as your JNI wrapper)
    params.enable_mmap = true;
    params.offload_params_to_cpu = true;
    params.free_params_immediately = true;

    g_sd_ctx = new_sd_ctx(&params);
    if (!g_sd_ctx) {
        std::fprintf(stderr, "sd_init: FAILED to create sd context for '%s'\n", model_path);
        return 0;
    }

    return 1;
}

static inline void rgba_from_rgb(const uint8_t * rgb, int pixelCount, std::vector<uint8_t> & out) {
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

static inline void rgba_from_gray(const uint8_t * gray, int pixelCount, std::vector<uint8_t> & out) {
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

// Returns a malloc'ed RGBA buffer and sets output sizes.
// Caller MUST free via sd_free_bytes().
uint8_t * sd_txt2img_rgba(
        const char * prompt,
        const char * negative_prompt,
        int32_t width,
        int32_t height,
        int32_t steps,
        float cfg_scale,
        int64_t seed,
        int32_t * out_w,
        int32_t * out_h,
        int32_t * out_size_bytes
) {
    if (out_w) *out_w = 0;
    if (out_h) *out_h = 0;
    if (out_size_bytes) *out_size_bytes = 0;

    if (!g_sd_ctx) {
        std::fprintf(stderr, "sd_txt2img_rgba: ctx not initialized\n");
        return nullptr;
    }
    if (!prompt || prompt[0] == '\0') {
        std::fprintf(stderr, "sd_txt2img_rgba: prompt null/empty\n");
        return nullptr;
    }

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);

    gen.prompt = prompt;
    gen.negative_prompt = (negative_prompt && negative_prompt[0]) ? negative_prompt : "";

    gen.width  = (int) width;
    gen.height = (int) height;
    gen.seed   = (int64_t) seed;

    gen.sample_params.sample_steps = (int) steps;
    gen.sample_params.guidance.txt_cfg = (float) cfg_scale;

    sd_image_t * img = generate_image(g_sd_ctx, &gen);
    if (!img || !img->data || img->width == 0 || img->height == 0) {
        std::fprintf(stderr, "sd_txt2img_rgba: generate_image failed\n");
        if (img) {
            if (img->data) std::free(img->data);
            std::free(img);
        }
        return nullptr;
    }

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
        std::fprintf(stderr, "sd_txt2img_rgba: unknown channel count=%d\n", ch);
        std::free(img->data);
        std::free(img);
        return nullptr;
    }

    std::free(img->data);
    std::free(img);

    uint8_t * out = (uint8_t *) std::malloc(rgba.size());
    if (!out) return nullptr;

    std::memcpy(out, rgba.data(), rgba.size());

    if (out_w) *out_w = w;
    if (out_h) *out_h = h;
    if (out_size_bytes) *out_size_bytes = (int32_t) rgba.size();

    return out;
}

void sd_free_bytes(uint8_t * p) {
    if (p) std::free(p);
}

void sd_release(void) {
    if (g_sd_ctx) {
        free_sd_ctx(g_sd_ctx);
        g_sd_ctx = nullptr;
    }
}

} // extern "C"
