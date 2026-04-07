#ifndef LLAMA_MULTIMODAL_EMBED_H
#define LLAMA_MULTIMODAL_EMBED_H

#ifdef __cplusplus
extern "C" {
#else
#include <stdbool.h>
#endif

#include <stddef.h>
#include <stdint.h>

typedef void (*vlm_on_delta)(const char *utf8, void *user);
typedef void (*vlm_on_done)(void *user);
typedef void (*vlm_on_error)(const char *utf8, void *user);

/**
 * Initialize the text/vision model and its multimodal projector.
 * @param model_path   Absolute path to the GGUF text/vision model file.
 * @param mmproj_path  Absolute path to the GGUF mmproj file.
 * @return true on success.
 */
bool vlm_init(const char *model_path, const char *mmproj_path);

/**
 * Analyze an image from memory (JPEG/PNG/BMP raw file bytes), streaming tokens.
 * Blocks until streaming completes. Call from a background thread.
 */
void vlm_analyze_image_bytes_stream(
        const unsigned char *image_bytes,
        size_t               image_len,
        const char          *prompt,
        vlm_on_delta         on_delta,
        vlm_on_done          on_done,
        vlm_on_error         on_error,
        void                *user_data);

/** Cancel an in-progress vlm_analyze_image_bytes_stream call. */
void vlm_cancel(void);

/** Free all multimodal native resources. */
void vlm_release(void);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // LLAMA_MULTIMODAL_EMBED_H
