#include "whisper_stt.h"
#include <string>
#include <mutex>

#include "whisper.h" // from whisper.cpp

static std::mutex g_mu;
static whisper_context* g_ctx = nullptr;
static std::string g_last;

int whisper_stt_init(const char* model_path) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx) return 1;
    g_ctx = whisper_init_from_file(model_path);
    return g_ctx ? 1 : 0;
}

// Minimal WAV loader: only PCM16 mono 16k
// (keep strict first; add resample later)
static bool load_wav_pcm16_mono_16k(const char* path, std::vector<float>& out) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;

    auto read_u32 = [&](uint32_t& v){ return fread(&v, 4, 1, f) == 1; };
    auto read_u16 = [&](uint16_t& v){ return fread(&v, 2, 1, f) == 1; };

    char riff[4]; if (fread(riff,1,4,f)!=4) { fclose(f); return false; }
    uint32_t riffSize; if (!read_u32(riffSize)) { fclose(f); return false; }
    char wave[4]; if (fread(wave,1,4,f)!=4) { fclose(f); return false; }

    bool fmtFound=false, dataFound=false;
    uint16_t audioFormat=0, numChannels=0, bitsPerSample=0;
    uint32_t sampleRate=0, dataSize=0;
    long dataPos=0;

    while (!fmtFound || !dataFound) {
        char id[4];
        if (fread(id,1,4,f)!=4) break;
        uint32_t size; if (!read_u32(size)) break;

        if (memcmp(id,"fmt ",4)==0) {
            fmtFound=true;
            read_u16(audioFormat);
            read_u16(numChannels);
            read_u32(sampleRate);
            uint32_t byteRate; read_u32(byteRate);
            uint16_t blockAlign; read_u16(blockAlign);
            read_u16(bitsPerSample);
            fseek(f, (long)size - 16, SEEK_CUR);
        } else if (memcmp(id,"data",4)==0) {
            dataFound=true;
            dataSize=size;
            dataPos=ftell(f);
            fseek(f, (long)size, SEEK_CUR);
        } else {
            fseek(f, (long)size, SEEK_CUR);
        }
    }

    if (!fmtFound || !dataFound) { fclose(f); return false; }
    if (audioFormat != 1) { fclose(f); return false; }
    if (numChannels != 1) { fclose(f); return false; }
    if (sampleRate != 16000) { fclose(f); return false; }
    if (bitsPerSample != 16) { fclose(f); return false; }

    fseek(f, dataPos, SEEK_SET);
    const int n = (int)(dataSize / 2);
    out.resize(n);
    for (int i=0;i<n;i++){
        int16_t s;
        if (fread(&s,2,1,f)!=1){ fclose(f); return false; }
        out[i] = (float)s / 32768.0f;
    }
    fclose(f);
    return true;
}

const char* whisper_stt_transcribe_wav(const char* wav_path, const char* language) {
    std::lock_guard<std::mutex> lock(g_mu);
    g_last.clear();
    if (!g_ctx) { g_last = "ERROR: Whisper not initialized"; return g_last.c_str(); }

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

    if (whisper_full(g_ctx, params, pcmf.data(), (int)pcmf.size()) != 0) {
        g_last = "ERROR: whisper_full failed";
        return g_last.c_str();
    }

    const int n = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n; i++) {
        g_last += whisper_full_get_segment_text(g_ctx, i);
    }
    return g_last.c_str();
}

void whisper_stt_release(void) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (g_ctx) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }
}
