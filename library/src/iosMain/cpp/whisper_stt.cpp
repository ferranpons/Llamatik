#include "whisper.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#if defined(__APPLE__)
#include <TargetConditionals.h>
#endif

static struct whisper_context *g_whisper_ctx = nullptr;

// Minimal WAV reader for PCM16 LE.
// Converts to mono float32 [-1, 1]. Returns true on success.
static bool read_wav_pcm16le_to_f32_mono(const char *path, std::vector<float> &out) {
    out.clear();

    if (!path || path[0] == '\0') return false;

    FILE *f = std::fopen(path, "rb");
    if (!f) return false;

    auto rd_u32 = [&](uint32_t &v) -> bool {
        uint8_t b[4];
        if (std::fread(b, 1, 4, f) != 4) return false;
        v = (uint32_t)b[0] | ((uint32_t)b[1] << 8) | ((uint32_t)b[2] << 16) | ((uint32_t)b[3] << 24);
        return true;
    };

    auto rd_u16 = [&](uint16_t &v) -> bool {
        uint8_t b[2];
        if (std::fread(b, 1, 2, f) != 2) return false;
        v = (uint16_t)b[0] | ((uint16_t)b[1] << 8);
        return true;
    };

    char riff[4];
    if (std::fread(riff, 1, 4, f) != 4 || std::memcmp(riff, "RIFF", 4) != 0) {
        std::fclose(f);
        return false;
    }

    uint32_t riff_size = 0;
    if (!rd_u32(riff_size)) { std::fclose(f); return false; }

    char wave[4];
    if (std::fread(wave, 1, 4, f) != 4 || std::memcmp(wave, "WAVE", 4) != 0) {
        std::fclose(f);
        return false;
    }

    bool have_fmt = false;
    bool have_data = false;

    uint16_t audio_format = 0;   // 1 = PCM
    uint16_t num_channels = 0;
    uint32_t sample_rate = 0;
    uint16_t bits_per_sample = 0;

    std::vector<uint8_t> data_bytes;

    while (!have_data) {
        char chunk_id[4];
        if (std::fread(chunk_id, 1, 4, f) != 4) break;

        uint32_t chunk_size = 0;
        if (!rd_u32(chunk_size)) break;

        if (std::memcmp(chunk_id, "fmt ", 4) == 0) {
            if (chunk_size < 16) { std::fclose(f); return false; }

            if (!rd_u16(audio_format)) { std::fclose(f); return false; }
            if (!rd_u16(num_channels)) { std::fclose(f); return false; }
            if (!rd_u32(sample_rate))  { std::fclose(f); return false; }

            uint32_t byte_rate = 0;
            uint16_t block_align = 0;
            if (!rd_u32(byte_rate))   { std::fclose(f); return false; }
            if (!rd_u16(block_align)) { std::fclose(f); return false; }

            if (!rd_u16(bits_per_sample)) { std::fclose(f); return false; }

            const uint32_t consumed = 16;
            const uint32_t extra = chunk_size > consumed ? (chunk_size - consumed) : 0;
            if (extra > 0) std::fseek(f, (long)extra, SEEK_CUR);

            have_fmt = true;
        } else if (std::memcmp(chunk_id, "data", 4) == 0) {
            data_bytes.resize(chunk_size);
            if (chunk_size > 0) {
                if (std::fread(data_bytes.data(), 1, chunk_size, f) != chunk_size) {
                    std::fclose(f);
                    return false;
                }
            }
            have_data = true;
        } else {
            std::fseek(f, (long)chunk_size, SEEK_CUR);
        }

        if (chunk_size & 1) std::fseek(f, 1, SEEK_CUR);
    }

    std::fclose(f);

    if (!have_fmt || !have_data) return false;
    if (audio_format != 1) return false;      // PCM only
    if (bits_per_sample != 16) return false;  // PCM16 only
    if (num_channels < 1 || num_channels > 2) return false;

    const size_t frame_size = (size_t)num_channels * 2;
    if (data_bytes.size() < frame_size) return false;

    const size_t n_frames = data_bytes.size() / frame_size;
    out.reserve(n_frames);

    auto s16_at = [&](size_t byte_index) -> int16_t {
        const uint8_t lo = data_bytes[byte_index + 0];
        const uint8_t hi = data_bytes[byte_index + 1];
        return (int16_t)((hi << 8) | lo);
    };

    for (size_t i = 0; i < n_frames; ++i) {
        const size_t base = i * frame_size;

        if (num_channels == 1) {
            const int16_t s = s16_at(base);
            out.push_back((float)s / 32768.0f);
        } else {
            const int16_t l = s16_at(base);
            const int16_t r = s16_at(base + 2);
            const float mono = (((float)l + (float)r) * 0.5f) / 32768.0f;
            out.push_back(mono);
        }
    }

    return !out.empty();
}

extern "C" {

// Returns 1 on success, 0 on failure.
int32_t whisper_stt_init(const char *model_path) {
    if (g_whisper_ctx) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }

    if (!model_path || model_path[0] == '\0') {
        std::fprintf(stderr, "whisper_stt_init: model_path is null/empty\n");
        return 0;
    }

    std::fprintf(stderr, "whisper_stt_init: loading model from '%s'\n", model_path);

    struct whisper_context_params cparams = whisper_context_default_params();

    // Simulator: disable GPU/flash_attn (Metal simulator can be flaky)
#if defined(__APPLE__) && defined(TARGET_OS_SIMULATOR) && TARGET_OS_SIMULATOR
    cparams.use_gpu = false;
    cparams.flash_attn = false;
#else
    cparams.use_gpu = true;
    cparams.flash_attn = true;
#endif

    g_whisper_ctx = whisper_init_from_file_with_params(model_path, cparams);
    if (!g_whisper_ctx) {
        std::fprintf(stderr, "whisper_stt_init: FAILED to load model from '%s'\n", model_path);
        return 0;
    }

    return 1;
}

// Returns malloc'ed UTF-8 string. Caller must free via whisper_stt_free_string.
char *whisper_stt_transcribe_wav(const char *wav_path, const char *language, const char *initial_prompt) {
    if (!g_whisper_ctx) {
        std::fprintf(stderr, "whisper_stt_transcribe_wav: context not initialized\n");
        return ::strdup("");
    }
    if (!wav_path || wav_path[0] == '\0') {
        std::fprintf(stderr, "whisper_stt_transcribe_wav: wav_path is null/empty\n");
        return ::strdup("");
    }

    std::vector<float> pcmf32;
    if (!read_wav_pcm16le_to_f32_mono(wav_path, pcmf32)) {
        std::fprintf(stderr, "whisper_stt_transcribe_wav: failed to read wav '%s'\n", wav_path);
        return ::strdup("");
    }

    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    if (language && language[0] != '\0') {
        params.language = language;   // "en", "es", "fr", "de", "ru", "zh", ...
        params.translate = false;
    } else {
        params.language = "auto";
        params.translate = false;
    }

    if (initial_prompt && initial_prompt[0] != '\0') {
        params.initial_prompt = initial_prompt;
    }

    const int rc = whisper_full(g_whisper_ctx, params, pcmf32.data(), (int)pcmf32.size());
    if (rc != 0) {
        std::fprintf(stderr, "whisper_stt_transcribe_wav: whisper_full failed rc=%d\n", rc);
        return ::strdup("");
    }

    std::string out;
    const int n_segments = whisper_full_n_segments(g_whisper_ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *txt = whisper_full_get_segment_text(g_whisper_ctx, i);
        if (txt) out += txt;
    }

    return ::strdup(out.c_str());
}

void whisper_stt_release(void) {
    if (g_whisper_ctx) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }
}

void whisper_stt_free_string(char *p) {
    if (p) std::free(p);
}

} // extern "C"
