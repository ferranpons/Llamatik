#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Returns 1 on success, 0 on failure.
int32_t sd_init(const char * model_path, int32_t threads);

// Returns malloc'ed RGBA bytes (size in out_size_bytes). Free with sd_free_bytes().
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
);

// Returns malloc'ed RGBA bytes for image-to-image. initImage must be RGBA (w*h*4 bytes).
// strength controls how much the init image is preserved (0.0 = keep, 1.0 = ignore).
uint8_t * sd_img2img_rgba(
        const uint8_t * init_image_rgba,
        int32_t init_image_w,
        int32_t init_image_h,
        const char * prompt,
        const char * negative_prompt,
        int32_t width,
        int32_t height,
        int32_t steps,
        float cfg_scale,
        float strength,
        int64_t seed,
        int32_t * out_w,
        int32_t * out_h,
        int32_t * out_size_bytes
);

void sd_free_bytes(uint8_t * p);
void sd_release(void);

#ifdef __cplusplus
} // extern "C"
#endif