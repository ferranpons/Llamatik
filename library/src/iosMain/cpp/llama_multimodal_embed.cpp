// llama_multimodal_embed.cpp
// C ABI wrapper over libmtmd for Kotlin/Native cinterop (iOS).
// Mirrors the style of llama_embed.cpp but adds multimodal image analysis.

#ifdef __APPLE__
#include <TargetConditionals.h>
#else
#define TARGET_OS_SIMULATOR 0
#endif

#include "llama_multimodal_embed.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <string>
#include <sstream>
#include <atomic>
#include <algorithm>

// ===================== Logging =====================

static void vlm_log(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    std::vfprintf(stderr, fmt, args);
    std::fprintf(stderr, "\n");
    va_end(args);
}

#define VLMI(...) vlm_log("[VLMBridge][I] " __VA_ARGS__)
#define VLME(...) vlm_log("[VLMBridge][E] " __VA_ARGS__)

// ===================== Global state =====================

static struct llama_model   *mm_model         = nullptr;
static struct llama_context *mm_ctx           = nullptr;
static struct mtmd_context  *mm_mtmd          = nullptr;
static bool                  mm_backend_inited = false;
static std::atomic<bool>     mm_cancel        {false};

// ===================== Helpers =====================

static std::string build_vlm_prompt(const char *user_prompt) {
    std::ostringstream oss;
    oss << mtmd_default_marker() << "\n";
    oss << (user_prompt && user_prompt[0] ? user_prompt : "Describe this image.");
    return oss.str();
}

static inline bool is_eog(const struct llama_vocab *vocab, llama_token tok) {
    return llama_vocab_is_eog(vocab, tok);
}

// Core streaming — runs after image tokens are prefilled via mtmd_helper_eval_chunks.
static void stream_vlm_c(
        const struct mtmd_bitmap *bitmap,
        const char               *prompt,
        vlm_on_delta              on_delta,
        vlm_on_done               on_done,
        vlm_on_error              on_error,
        void                     *user_data) {

    if (!mm_model || !mm_ctx || !mm_mtmd || !bitmap) {
        if (on_error) on_error("multimodal context not initialized", user_data);
        return;
    }

    mm_cancel.store(false, std::memory_order_relaxed);
    llama_memory_clear(llama_get_memory(mm_ctx), false);

    // --- Build prompt string with media marker ---
    std::string vlm_prompt = build_vlm_prompt(prompt);

    // --- Tokenize text + image bitmap ---
    const struct mtmd_bitmap *bitmaps[1] = { bitmap };

    mtmd_input_text input_text;
    input_text.text          = vlm_prompt.c_str();
    input_text.add_special   = true;
    input_text.parse_special = true;

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t tokenize_ret = mtmd_tokenize(mm_mtmd, chunks, &input_text, bitmaps, 1);
    if (tokenize_ret != 0) {
        mtmd_input_chunks_free(chunks);
        if (on_error) on_error("mtmd_tokenize failed", user_data);
        return;
    }

    // --- Encode image chunks + decode text chunks (prefill) ---
    llama_pos n_past  = 0;
    int32_t   n_batch = (int32_t)llama_n_batch(mm_ctx);

    int32_t eval_ret = mtmd_helper_eval_chunks(
            mm_mtmd, mm_ctx,
            chunks,
            n_past,
            /*seq_id*/ 0,
            n_batch,
            /*logits_last*/ true,
            &n_past);

    mtmd_input_chunks_free(chunks);

    if (eval_ret != 0) {
        if (on_error) on_error("mtmd_helper_eval_chunks failed", user_data);
        return;
    }

    // --- Autoregressive token sampling ---
    const struct llama_vocab *vocab    = llama_model_get_vocab(mm_model);
    const int                 n_ctx    = (int)llama_n_ctx(mm_ctx);
    const int                 max_toks = 640;

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.35f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    char piece_buf[512];
    char spec_buf[64];

    for (int i = 0; i < max_toks; ++i) {
        if (mm_cancel.load(std::memory_order_relaxed)) break;
        if (n_past >= n_ctx) break;

        llama_token tok = llama_sampler_sample(sampler, mm_ctx, -1);
        if (tok < 0 || is_eog(vocab, tok)) break;

        // Check for EOT special pieces
        int sn = llama_token_to_piece(vocab, tok, spec_buf, (int)sizeof(spec_buf), 0, 1);
        if (sn > 0) {
            spec_buf[std::min(sn, (int)sizeof(spec_buf) - 1)] = '\0';
            if (std::strcmp(spec_buf, "<end_of_turn>") == 0 ||
                std::strcmp(spec_buf, "<|eot_id|>") == 0 ||
                std::strcmp(spec_buf, "<|im_end|>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);

        int nn = llama_token_to_piece(vocab, tok, piece_buf, (int)sizeof(piece_buf), 0, 0);
        if (nn > 0 && on_delta) {
            piece_buf[std::min(nn, (int)sizeof(piece_buf) - 1)] = '\0';
            on_delta(piece_buf, user_data);
        }

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens     = 1;
        step.token[0]     = tok;
        step.pos[0]       = n_past++;
        step.n_seq_id[0]  = 1;
        step.seq_id[0][0] = 0;
        step.logits[0]    = true;

        if (llama_decode(mm_ctx, step) != 0) {
            llama_batch_free(step);
            llama_sampler_free(sampler);
            if (on_error) on_error("llama_decode failed mid-stream", user_data);
            return;
        }
        llama_batch_free(step);
    }

    llama_sampler_free(sampler);
    if (on_done) on_done(user_data);
}

// ===================== Public C API =====================

extern "C" {

bool vlm_init(const char *model_path, const char *mmproj_path) {
    // Release previously loaded resources
    if (mm_mtmd)  { mtmd_free(mm_mtmd);         mm_mtmd  = nullptr; }
    if (mm_ctx)   { llama_free(mm_ctx);          mm_ctx   = nullptr; }
    if (mm_model) { llama_model_free(mm_model);  mm_model = nullptr; }

    if (!model_path || !mmproj_path) {
        VLME("vlm_init: null path");
        return false;
    }

    if (!mm_backend_inited) {
        llama_backend_init();
        mm_backend_inited = true;
    }

    VLMI("vlm_init: loading model %s", model_path);
    llama_model_params mparams = llama_model_default_params();

#if TARGET_OS_SIMULATOR
    mparams.use_mmap     = false;
    mparams.n_gpu_layers = 0;
#endif

    mm_model = llama_model_load_from_file(model_path, mparams);
    if (!mm_model) {
        VLME("vlm_init: failed to load model");
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx      = 8192;
    cparams.embeddings = false;

    mm_ctx = llama_init_from_model(mm_model, cparams);
    if (!mm_ctx) {
        VLME("vlm_init: failed to create context");
        llama_model_free(mm_model);
        mm_model = nullptr;
        return false;
    }

    VLMI("vlm_init: loading mmproj %s", mmproj_path);
    struct mtmd_context_params mctx_params = mtmd_context_params_default();
    mctx_params.use_gpu       = true;
    mctx_params.print_timings = false;
    mctx_params.n_threads     = 4;
    mctx_params.warmup        = false;

#if TARGET_OS_SIMULATOR
    mctx_params.use_gpu = false;
#endif

    mm_mtmd = mtmd_init_from_file(mmproj_path, mm_model, mctx_params);
    if (!mm_mtmd) {
        VLME("vlm_init: failed to init mtmd context");
        llama_free(mm_ctx);   mm_ctx   = nullptr;
        llama_model_free(mm_model); mm_model = nullptr;
        return false;
    }

    VLMI("vlm_init: ready. n_ctx=%d", (int)llama_n_ctx(mm_ctx));
    return true;
}

void vlm_analyze_image_bytes_stream(
        const unsigned char *image_bytes,
        size_t               image_len,
        const char          *prompt,
        vlm_on_delta         on_delta,
        vlm_on_done          on_done,
        vlm_on_error         on_error,
        void                *user_data) {

    if (!image_bytes || image_len == 0) {
        if (on_error) on_error("no image data provided", user_data);
        return;
    }

    mtmd_bitmap *bitmap = mtmd_helper_bitmap_init_from_buf(mm_mtmd, image_bytes, image_len);
    if (!bitmap) {
        if (on_error) on_error("failed to decode image bytes", user_data);
        return;
    }

    stream_vlm_c(bitmap, prompt, on_delta, on_done, on_error, user_data);
    mtmd_bitmap_free(bitmap);
}

void vlm_cancel(void) {
    mm_cancel.store(true, std::memory_order_relaxed);
}

void vlm_release(void) {
    if (mm_mtmd)  { mtmd_free(mm_mtmd);         mm_mtmd  = nullptr; }
    if (mm_ctx)   { llama_free(mm_ctx);          mm_ctx   = nullptr; }
    if (mm_model) { llama_model_free(mm_model);  mm_model = nullptr; }
    if (mm_backend_inited) {
        llama_backend_free();
        mm_backend_inited = false;
    }
}

} // extern "C"
