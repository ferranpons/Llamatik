#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

bool llama_embed_init(const char *model_path);
float *llama_embed(const char *input_text);
int llama_embedding_size();
void llama_free_embedding(float *embedding);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_EMBED_H
