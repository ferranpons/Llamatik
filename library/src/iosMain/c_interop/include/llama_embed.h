#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#ifdef __cplusplus
extern "C" {
#else
// When compiling as C / Obj-C, make sure 'bool' exists
  #include <stdbool.h>   // C99 'bool', 'true', 'false'
#endif

// ================= Embeddings =================

/**
 * Initialize the embedding model from a given file path.
 * Returns true on success, false on failure.
 */
bool llama_embed_init(const char *model_path);

/**
 * Compute embeddings for the given input text.
 * Returns a newly allocated float array of size `llama_embedding_size()`,
 * or NULL on error. Free with llama_free_embedding().
 */
float *llama_embed(const char *input);

/**
 * Returns the embedding vector size for the loaded model.
 */
int llama_embedding_size(void);

/**
 * Free an embedding array returned by llama_embed().
 */
void llama_free_embedding(float *ptr);

/**
 * Free all embedding-related resources.
 */
void llama_embed_free(void);

// ================= Text Generation =================

/**
 * Initialize the generation model from a given file path.
 * Returns true on success, false on failure.
 */
bool llama_generate_init(const char *model_path);

/**
 * Generate text from a given prompt.
 * Returns a newly allocated null-terminated C string,
 * or NULL on error. Free with free().
 */
char *llama_generate(const char *prompt);

/**
 * Free all text generation-related resources.
 */
void llama_generate_free(void);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // LLAMA_EMBED_H