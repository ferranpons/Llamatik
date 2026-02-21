#include "llama.h"
#include <string>
#include <vector>
#include <cstdlib>
#include <cstring>

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" {

// Returns 1 on success, 0 on failure
int llamatik_llama_init_generate(const char* model_path) {
    if (!model_path) return 0;

    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, mp);
    if (!g_model) return 0;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = 4096;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        llama_model_free(g_model);
        g_model = nullptr;
        return 0;
    }
    return 1;
}

// Very simple generate: returns a malloc'd UTF-8 string (caller frees with llamatik_free_string)
char* llamatik_llama_generate(const char* prompt) {
    if (!g_ctx || !g_model || !prompt) return nullptr;

    // NOTE: This is a placeholder generation loop. You likely already have a better one in llama_embed.cpp/llama_jni.cpp.
    // Keep it simple for first compile: return echo-like output.
    std::string out = std::string("WASM engine loaded. Prompt: ") + prompt;

    char* res = (char*) std::malloc(out.size() + 1);
    if (!res) return nullptr;
    std::memcpy(res, out.c_str(), out.size() + 1);
    return res;
}

void llamatik_free_string(char* p) {
    if (p) std::free(p);
}


}