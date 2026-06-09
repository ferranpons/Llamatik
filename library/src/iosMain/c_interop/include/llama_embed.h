#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#ifdef __cplusplus
extern "C" {
#else
// When compiling as C / Obj-C, make sure 'bool' and fixed-width types exist
  #include <stdbool.h>   // C99 'bool', 'true', 'false'
  #include <stdint.h>    // int64_t
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
        float repeat_penalty,
        int context_length,
        int num_threads,
        bool use_mmap,
        bool flash_attention,
        int batch_size,
        int gpu_layers);

// ===================== KV session support =====================

/** Clears KV/session state but keeps model/context loaded. */
bool llama_generate_session_reset(void);

/** Saves KV/session state to file. */
bool llama_generate_session_save(const char *path_session);

/** Loads KV/session state from file. */
bool llama_generate_session_load(const char *path_session);

/** Continues using existing KV cache; returns malloc string. */
char *llama_generate_continue(const char *prompt);

// ===================== Chat template =====================

/**
 * Returns the chat template string embedded in the loaded GGUF model.
 * The returned pointer is owned by the model — do not free it.
 * Returns NULL if the model is not loaded or has no embedded template.
 */
const char *llama_get_model_chat_template(void);

/**
 * Returns the value of the "general.finetune" GGUF metadata key.
 * Typical values: "instruct", "chat", "base" (or absent for base models).
 * Returns a newly malloc'd string the caller must free(), or NULL if the model
 * is not loaded or the key is absent.
 */
char *llama_get_model_finetune_type(void);

/**
 * Renders messages into a prompt string using the model's embedded chat template.
 * roles and contents are parallel arrays of length n_messages.
 * add_assistant_prefix: append the assistant turn opener at the end.
 * Returns a newly malloc'd string the caller must free(), or NULL on error.
 */
char *llama_apply_chat_template(
    const char **roles,
    const char **contents,
    int n_messages,
    bool add_assistant_prefix);

// ===================== MTP (Multi-Token Prediction) =====================

/**
 * Load the same GGUF as the trunk model a second time with a MTP context type.
 * Must be called AFTER llama_generate_init() because the trunk model must already
 * be loaded (MTP needs it to enable pre-norm embeddings on the trunk context).
 *
 * model_path: path to the same .gguf used for generation (MTP layers are embedded in it).
 * draft_len:  max speculative tokens per step (1-8 recommended, 0 = use default of 3).
 *
 * Returns true on success, false if the model has no MTP layers or loading fails.
 */
bool llama_mtp_init(const char *model_path, int draft_len);

/**
 * Release MTP resources.  Generation continues normally with the trunk model.
 */
void llama_mtp_shutdown(void);

// ===================== Concurrent session API =====================

/**
 * Create an independent inference session sharing the already-loaded generate model.
 * Returns a positive int64 handle on success, or -1 on failure.
 * Each session has its own KV cache and cancel flag so multiple sessions may run
 * concurrently on separate threads.
 */
int64_t llama_session_create(void);

/**
 * Release all resources for the given session handle.
 * Calling this while llama_session_stream() is active on the handle is undefined.
 */
void llama_session_close(int64_t handle);

/**
 * Stream generation for the given session handle.
 * Semantics are identical to llama_generate_stream() but isolated to this session.
 */
void llama_session_stream(int64_t handle,
        const char *prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user);

/**
 * Reset the KV cache and token history for the given session handle.
 * Subsequent llama_session_stream() calls will start a fresh context.
 */
void llama_session_reset(int64_t handle);

/**
 * Request cancellation of an in-progress llama_session_stream() for this handle.
 */
void llama_session_cancel(int64_t handle);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // LLAMA_EMBED_H