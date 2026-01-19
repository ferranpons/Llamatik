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

// ================= Text Generation (blocking) =================

/**
 * Initialize the generation model from a given file path.
 * Returns true on success, false on failure.
 */
bool llama_generate_init(const char *model_path);

/**
 * Generate text from a given prompt.
 * Returns a newly allocated null-terminated C string,
 * or NULL on error. Free with free().
 *
 * NOTE: This function is synchronous / blocking.
 */
char *llama_generate(const char *prompt);

/**
 * Generate text from a (system, context, user) triplet.
 * Returns a newly allocated null-terminated C string,
 * or NULL on error. Free with free().
 *
 * NOTE: This function is synchronous / blocking.
 */
char *llama_generate_chat(const char *system_prompt,
        const char *context_block,
        const char *user_prompt);

/**
 * Generate JSON from a given prompt.
 *
 * If json_schema is NULL or empty, output is constrained to be valid JSON.
 * If json_schema is provided, output is constrained to match (a supported subset of) JSON Schema.
 *
 * Returns a newly allocated null-terminated C string, or NULL on error. Free with free().
 */
char *llama_generate_json_schema(const char *prompt, const char *json_schema);

/**
 * Generate JSON from a (system, context, user) triplet with optional JSON Schema constraint.
 * Returns a newly allocated null-terminated C string, or NULL on error. Free with free().
 */
char *llama_generate_chat_json_schema(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        const char *json_schema);

/**
 * Free all text generation-related resources.
 */
void llama_generate_free(void);

// ================= Text Generation (streaming) =================
//
// These APIs stream tokens/deltas via callbacks and block until completion.
// Call from a background thread/queue.

// Callback signatures
typedef void (*llm_on_delta)(const char *utf8, void *user);  // Called with each chunk (UTF-8)
typedef void (*llm_on_done)(void *user);                     // Called once when streaming finishes
typedef void (*llm_on_error)(const char *utf8, void *user);  // Called on error with message

/**
 * Request cancellation of the current streaming generation (if any).
 * The next token step will see the flag and stop early.
 */
void llama_generate_cancel(void);

/**
 * Stream generation from a single prompt.
 * - on_delta: receives incremental UTF-8 chunks (may be short tokens or small pieces)
 * - on_done:  called exactly once on successful completion
 * - on_error: called exactly once on failure (on_done will NOT be called)
 * - user:     opaque pointer passed back to each callback
 *
 * NOTE: This call is synchronous/blocking; invoke off the main thread.
 */
void llama_generate_stream(const char *prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user);

/**
 * Stream generation from (system, context, user) inputs.
 * Semantics are identical to llama_generate_stream but the prompt is constructed
 * from the three parts in a chat-style format.
 *
 * NOTE: This call is synchronous/blocking; invoke off the main thread.
 */
void llama_generate_chat_stream(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user);


/**
 * Stream JSON generation from a single prompt with optional JSON Schema constraint.
 * Semantics are identical to llama_generate_stream.
 */
void llama_generate_json_schema_stream(const char *prompt,
        const char *json_schema,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user);

/**
 * Stream JSON generation from (system, context, user) inputs with optional JSON Schema constraint.
 * Semantics are identical to llama_generate_chat_stream.
 */
void llama_generate_chat_json_schema_stream(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        const char *json_schema,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user);

void llama_generate_set_params(float temperature,
        int max_tokens,
        float top_p,
        int top_k,
        float repeat_penalty);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // LLAMA_EMBED_H