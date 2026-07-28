#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ================= Embeddings =================

bool   llama_embed_init(const char *model_path);
float *llama_embed(const char *input_text);
int    llama_embedding_size(void);
void   llama_free_embedding(float *embedding);
void   llama_embed_free(void);

// ================= Text Generation (blocking) =================

bool   llama_generate_init(const char *model_path);
char  *llama_generate(const char *prompt);
char  *llama_generate_chat(const char *system_prompt,
                            const char *context_block,
                            const char *user_prompt);
char  *llama_generate_json_schema(const char *prompt, const char *json_schema);
char  *llama_generate_chat_json_schema(const char *system_prompt,
                                        const char *context_block,
                                        const char *user_prompt,
                                        const char *json_schema);
void   llama_generate_free(void);
void   llama_free_cstr(char *p);

// ================= Text Generation (streaming) =================

typedef void (*llm_on_delta)(const char *utf8, void *user);
typedef void (*llm_on_done)(void *user);
typedef void (*llm_on_error)(const char *utf8, void *user);

void llama_generate_cancel(void);

void llama_generate_stream(const char *prompt,
        llm_on_delta on_delta, llm_on_done on_done, llm_on_error on_error,
        void *user);

void llama_generate_chat_stream(const char *system_prompt,
        const char *context_block, const char *user_prompt,
        llm_on_delta on_delta, llm_on_done on_done, llm_on_error on_error,
        void *user);

void llama_generate_json_schema_stream(const char *prompt, const char *json_schema,
        llm_on_delta on_delta, llm_on_done on_done, llm_on_error on_error,
        void *user);

void llama_generate_chat_json_schema_stream(const char *system_prompt,
        const char *context_block, const char *user_prompt, const char *json_schema,
        llm_on_delta on_delta, llm_on_done on_done, llm_on_error on_error,
        void *user);

void llama_generate_set_params(float temperature, int max_tokens,
        float top_p, int top_k, float repeat_penalty, int context_length,
        int num_threads, bool use_mmap, bool flash_attention,
        int batch_size, int gpu_layers);

// ================= KV session support =================

bool  llama_generate_session_reset(void);
bool  llama_generate_session_save(const char *path_session);
bool  llama_generate_session_load(const char *path_session);
char *llama_generate_continue(const char *prompt);

// ================= MTP (Multi-Token Prediction) =================

bool llama_mtp_init(const char *model_path, int draft_len);
void llama_mtp_shutdown(void);

// ================= Model metadata =================

char       *llama_get_model_finetune_type(void);
const char *llama_get_model_chat_template(void);
char       *llama_apply_chat_template(const char **roles,
                                       const char **contents,
                                       int n_messages,
                                       bool add_assistant_prefix);

// ================= Concurrent session API =================

int64_t llama_session_create(void);
void    llama_session_close(int64_t handle);
void    llama_session_stream(int64_t handle, const char *prompt,
                              llm_on_delta on_delta, llm_on_done on_done,
                              llm_on_error on_error, void *user);
void    llama_session_reset(int64_t handle);
void    llama_session_cancel(int64_t handle);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_EMBED_H
