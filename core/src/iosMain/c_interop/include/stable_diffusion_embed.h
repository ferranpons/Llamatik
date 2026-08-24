#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t sd_init(const char * model_path, int32_t threads);

// Returns malloc'ed RGBA buffer (size = out_size_bytes). Free with sd_free_bytes().
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

void sd_free_bytes(uint8_t * p);
void sd_release(void);

#ifdef __cplusplus
}
#endif