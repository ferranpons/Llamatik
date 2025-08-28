#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// Embeddings
bool   llama_embed_init(const char *model_path);
float *llama_embed(const char *input_text);
int    llama_embedding_size(void);
void   llama_free_embedding(float *embedding);
void   llama_embed_free(void);

// Generation
bool   llama_generate_init(const char *model_path);
char  *llama_generate(const char *prompt);
char  *llama_generate_with_context(const char *system_prompt,
        const char *context_block,
        const char *user_prompt);
void   llama_generate_free(void);

// Helpers / shutdown
void   llama_free_cstr(char *p);
void   llama_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_EMBED_H