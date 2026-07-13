#pragma once

#ifdef __cplusplus
extern "C" {
#endif

int whisper_stt_init(const char* model_path);
char* whisper_stt_transcribe_wav(const char* wav_path, const char* language, const char* initial_prompt);
char* whisper_stt_transcribe_wav_segments(const char* wav_path, const char* language, const char* initial_prompt, int translate, int diarize);
void whisper_stt_release(void);
void whisper_stt_free_string(char* p);

#ifdef __cplusplus
}
#endif