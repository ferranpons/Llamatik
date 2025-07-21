#ifdef __cplusplus
extern "C" {
#endif

bool llama_embed_init(const char *model_path);
float *llama_embed(const char *input_text);
int llama_embedding_size();
void llama_free_embedding(float *embedding);
bool llama_generate_init(const char *model_path);
char *llama_generate(const char *prompt);
void llama_generate_free();

#ifdef __cplusplus
}
#endif
