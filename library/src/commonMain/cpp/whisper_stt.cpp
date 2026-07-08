#include "whisper_stt.h"

#include <string>
#include <mutex>

#include <vector>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "whisper.h"

static std::mutex g_mu;
static whisper_context* g_ctx = nullptr;
static std::string g_last;
static std::string g_segments_json;

int whisper_stt_init(const char* model_path) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx) return 1;

    whisper_context_params cparams = whisper_context_default_params();
    // Keep defaults; you can customize later (GPU/Metal/etc)

    g_ctx = whisper_init_from_file_with_params(model_path, cparams);
    return g_ctx ? 1 : 0;
}

// Minimal WAV loader: only PCM16 mono 16kHz
static bool load_wav_pcm16_mono_16k(const char* path, std::vector<float>& out) {
    FILE* f = std::fopen(path, "rb");
    if (!f) return false;

    auto read_u32 = [&](uint32_t& v) {
        return std::fread(&v, 4, 1, f) == 1;
    };
    auto read_u16 = [&](uint16_t& v) {
        return std::fread(&v, 2, 1, f) == 1;
    };

    char riff[4];
    if (std::fread(riff, 1, 4, f) != 4) { std::fclose(f); return false; }
    uint32_t riffSize;
    if (!read_u32(riffSize)) { std::fclose(f); return false; }
    char wave[4];
    if (std::fread(wave, 1, 4, f) != 4) { std::fclose(f); return false; }

    if (std::memcmp(riff, "RIFF", 4) != 0 || std::memcmp(wave, "WAVE", 4) != 0) {
        std::fclose(f);
        return false;
    }

    bool fmtFound = false, dataFound = false;
    uint16_t audioFormat = 0, numChannels = 0, bitsPerSample = 0;
    uint32_t sampleRate = 0, dataSize = 0;
    long dataPos = 0;

    while (!fmtFound || !dataFound) {
        char id[4];
        if (std::fread(id, 1, 4, f) != 4) break;

        uint32_t size;
        if (!read_u32(size)) break;

        if (std::memcmp(id, "fmt ", 4) == 0) {
            fmtFound = true;

            read_u16(audioFormat);
            read_u16(numChannels);
            read_u32(sampleRate);

            uint32_t byteRate;
            read_u32(byteRate);

            uint16_t blockAlign;
            read_u16(blockAlign);

            read_u16(bitsPerSample);

            // Skip any extra fmt bytes
            if (size > 16) {
                std::fseek(f, (long)(size - 16), SEEK_CUR);
            }
        } else if (std::memcmp(id, "data", 4) == 0) {
            dataFound = true;
            dataSize = size;
            dataPos = std::ftell(f);
            std::fseek(f, (long)size, SEEK_CUR);
        } else {
            std::fseek(f, (long)size, SEEK_CUR);
        }
    }

    if (!fmtFound || !dataFound) { std::fclose(f); return false; }

    // PCM = 1, mono, 16kHz, 16-bit
    if (audioFormat != 1) { std::fclose(f); return false; }
    if (numChannels != 1) { std::fclose(f); return false; }
    if (sampleRate != 16000) { std::fclose(f); return false; }
    if (bitsPerSample != 16) { std::fclose(f); return false; }

    std::fseek(f, dataPos, SEEK_SET);

    const int n = (int)(dataSize / 2);
    out.resize(n);

    for (int i = 0; i < n; i++) {
        int16_t s;
        if (std::fread(&s, 2, 1, f) != 1) { std::fclose(f); return false; }
        out[i] = (float)s / 32768.0f;
    }

    std::fclose(f);
    return true;
}

const char* whisper_stt_transcribe_wav(const char* wav_path, const char* language, const char* initial_prompt) {
    std::lock_guard<std::mutex> lock(g_mu);
    g_last.clear();

    if (!g_ctx) {
        g_last = "ERROR: Whisper not initialized";
        return g_last.c_str();
    }

    std::vector<float> pcmf;
    if (!load_wav_pcm16_mono_16k(wav_path, pcmf)) {
        g_last = "ERROR: WAV must be PCM16 mono 16kHz";
        return g_last.c_str();
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;

    if (language && language[0]) {
        params.language = language;
    }

    if (initial_prompt && initial_prompt[0]) {
        params.initial_prompt = initial_prompt;
    }

    if (whisper_full(g_ctx, params, pcmf.data(), (int)pcmf.size()) != 0) {
        g_last = "ERROR: whisper_full failed";
        return g_last.c_str();
    }

    const int n = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n; i++) {
        const char* seg = whisper_full_get_segment_text(g_ctx, i);
        if (seg) g_last += seg;
    }

    return g_last.c_str();
}

// Minimal JSON string-content escaper (appends escaped [s] into [out]).
static void json_escape(const char* s, std::string& out) {
    if (!s) return;
    for (const char* p = s; *p; ++p) {
        const unsigned char c = static_cast<unsigned char>(*p);
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
}

// Segment-aware transcription. Same whisper_full call as
// whisper_stt_transcribe_wav (translate=false — language-PRESERVING), but returns
// a JSON document exposing per-segment text + timestamps (ms) + the tinydiarize
// speaker-turn flag, plus the whole-audio detected language code. This is the
// surface FR-20 (language-aware original + code-switch structuring) and FR-21
// (diarization / conversation view) consume — the flat transcribeWav concatenates
// segments and drops all of this.
//
// Shape:
//   {"language":"de","segments":[
//       {"text":"Guten Morgen.","t0":0,"t1":1200,"speaker_turn_next":false}, ...
//   ]}
// t0/t1 are milliseconds. speaker_turn_next is meaningful only with a tinydiarize
// (…-tdrz) model; it is always false for regular models (never throws).
const char* whisper_stt_transcribe_wav_segments(const char* wav_path, const char* language, const char* initial_prompt) {
    std::lock_guard<std::mutex> lock(g_mu);
    g_segments_json.clear();

    if (!g_ctx) {
        g_segments_json = "{\"error\":\"Whisper not initialized\"}";
        return g_segments_json.c_str();
    }

    std::vector<float> pcmf;
    if (!load_wav_pcm16_mono_16k(wav_path, pcmf)) {
        g_segments_json = "{\"error\":\"WAV must be PCM16 mono 16kHz\"}";
        return g_segments_json.c_str();
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;

    if (language && language[0]) {
        params.language = language;
    }
    if (initial_prompt && initial_prompt[0]) {
        params.initial_prompt = initial_prompt;
    }

    if (whisper_full(g_ctx, params, pcmf.data(), (int)pcmf.size()) != 0) {
        g_segments_json = "{\"error\":\"whisper_full failed\"}";
        return g_segments_json.c_str();
    }

    const int lang_id = whisper_full_lang_id(g_ctx);
    const char* lang_str = (lang_id >= 0) ? whisper_lang_str(lang_id) : "";

    g_segments_json = "{\"language\":\"";
    json_escape(lang_str ? lang_str : "", g_segments_json);
    g_segments_json += "\",\"segments\":[";

    const int n = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n; i++) {
        if (i > 0) g_segments_json += ",";
        const char* seg = whisper_full_get_segment_text(g_ctx, i);
        const long long t0 = static_cast<long long>(whisper_full_get_segment_t0(g_ctx, i)) * 10; // csec → msec
        const long long t1 = static_cast<long long>(whisper_full_get_segment_t1(g_ctx, i)) * 10;
        const bool turn = whisper_full_get_segment_speaker_turn_next(g_ctx, i);

        g_segments_json += "{\"text\":\"";
        json_escape(seg ? seg : "", g_segments_json);
        char buf[96];
        std::snprintf(buf, sizeof(buf),
                      "\",\"t0\":%lld,\"t1\":%lld,\"speaker_turn_next\":%s}",
                      t0, t1, turn ? "true" : "false");
        g_segments_json += buf;
    }
    g_segments_json += "]}";

    return g_segments_json.c_str();
}

void whisper_stt_release(void) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}
