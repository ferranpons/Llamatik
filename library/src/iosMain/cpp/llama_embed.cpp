#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <vector>
#include <__filesystem/operations.h>

#include "../c_interop/include/llama_embed.h"
#include "llama.h"

static struct llama_model *model = nullptr;
static struct llama_context *ctx = nullptr;
static int embedding_size = 0;

extern "C" {

bool llama_embed_init(const char *model_path) {
    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    //model_params.n_ctx = 512; // or your desired context size
    //model_params.main_gpu = -1; // Disable GPU use
    //model_params.n_gpu_layers = 0; // Disable GPU layers
    //model_params.devices = nullptr; // Disable devices

    model = llama_model_load_from_file(model_path, model_params);
    if (!model) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = true;

    ctx = llama_init_from_model(model, ctx_params);

    if (!ctx) return false;

    embedding_size = llama_model_n_embd(model);

    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model) return nullptr;

    // Tokenize
    std::vector <llama_token> tokens(512);

    int tokenSize = static_cast<int>(tokens.size());

    int n_tokens = ::llama_tokenize(
            llama_model_get_vocab(model),            // llama_model *
            input,            // input string
            128,
            tokens.data(),    // output token buffer
            tokenSize,    // max number of tokens
            true,             // add_bos
            false             // allow special tokens
    );

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        fprintf(stderr, "Tokenization failed or too many tokens\n");
        return nullptr;
    }
    tokens.resize(n_tokens);

    // Create batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }

    // Decode
    if (llama_decode(ctx, batch) != 0) {
        fprintf(stderr, "llama_decode failed\n");
        llama_batch_free(batch);
        return nullptr;
    }

    int dim = llama_model_n_embd(model);

    // Get embedding
    const float *embedding = llama_get_embeddings_seq(ctx, 0);
    if (!embedding) {
        fprintf(stderr, "llama_get_embeddings returned null\n");
        llama_batch_free(batch);
        return nullptr;
    }

    auto *out = new float[dim];
    memcpy(out, embedding, sizeof(float) * dim);

    llama_batch_free(batch);
    return out;
}

int llama_embedding_size() {
    return llama_model_n_embd(model);
}

void llama_free_embedding(float *ptr) {
    if (ptr) free(ptr);
}

};
