#pragma once

#ifdef __cplusplus
extern "C" {
#endif

int whisper_stt_init(const char* model_path);
const char* whisper_stt_transcribe_wav(const char* wav_path, const char* language);
void whisper_stt_release(void);

#ifdef __cplusplus
}
#endif