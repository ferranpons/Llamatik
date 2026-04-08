#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Returns 1 on success, 0 on failure.
int32_t whisper_stt_init(const char *model_path);

// Returns a malloc'ed UTF-8 C string (caller must free via whisper_stt_free_string).
// language can be NULL or "" to auto-detect.
char *whisper_stt_transcribe_wav(const char *wav_path, const char *language, const char *initial_prompt);

void whisper_stt_release(void);

// Frees a string allocated by whisper_stt_transcribe_wav.
void whisper_stt_free_string(char *p);

#ifdef __cplusplus
}
#endif