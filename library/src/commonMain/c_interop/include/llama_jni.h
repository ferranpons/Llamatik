#ifdef __cplusplus
extern "C" {
#endif

bool llama_embed_init(const char *model_path);
float *llama_embed(const char *input_text);
int llama_embedding_size();
void llama_free_embedding(float *embedding);
void llama_embed_free();

bool llama_generate_init(const char *model_path);
char *llama_generate(const char *prompt);
void llama_generate_free();
void llama_free_cstr(char *p);

// ===================== KV session support =====================
bool llama_generate_session_reset(void);
bool llama_generate_session_save(const char *path_session);
bool llama_generate_session_load(const char *path_session);
char *llama_generate_continue(const char *prompt);

#ifdef __cplusplus
}
#endif
