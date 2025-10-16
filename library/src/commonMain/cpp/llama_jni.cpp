#include <jni.h>
#include "llama.h"
#include "llama_jni.h"
#include <libgen.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <algorithm>
#include <cstring>   // strlen, memcpy
#include <cctype>    // tolower, isalpha, isdigit
#include <cstdlib>   // malloc, free
#include <string_view>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "LlamaBridge", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  "LlamaBridge", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "LlamaBridge", __VA_ARGS__)

// ===================================================================================
//                              GLOBAL STATE (this TU)
// ===================================================================================

// Embeddings
static struct llama_model   *emb_model   = nullptr;
static struct llama_context *emb_ctx     = nullptr;
static int                   emb_dim     = 0;

// Text generation
static struct llama_model   *gen_model   = nullptr;
static struct llama_context *gen_ctx     = nullptr;

// Backend lifetime
static bool g_backend_inited = false;

// ===================================================================================
//                              SMALL HELPERS
// ===================================================================================

static inline std::string trim(const std::string &s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}
static inline std::string to_lower(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c){ return (char)std::tolower(c); });
    return s;
}
static inline bool starts_with(const std::string &s, const char *pfx) {
    const size_t n = std::strlen(pfx);
    return s.size() >= n && std::memcmp(s.data(), pfx, n) == 0;
}

static int tokenize_with_retry(const llama_vocab *vocab,
        const char *text,
        std::vector<llama_token> &tokens,
        bool add_bos,
        bool parse_special) {
    if (!text) return 0;
    const int text_len = (int) std::strlen(text);
    int n = llama_tokenize(vocab, text, text_len,
            tokens.data(),
            (int) tokens.size(),
            add_bos, parse_special);
    if (n < 0) {
        const int need = -n;
        if (need > 0) {
            tokens.resize(need);
            n = llama_tokenize(vocab, text, text_len,
                    tokens.data(),
                    (int) tokens.size(),
                    add_bos, parse_special);
        }
    }
    return n;
}

static void truncate_to_ctx(std::vector<llama_token> &tokens, int n_ctx, int reserve_tail) {
    if ((int)tokens.size() <= n_ctx - reserve_tail) return;
    const int keep = n_ctx - reserve_tail;
    std::vector<llama_token> out;
    out.reserve(keep);
    out.insert(out.end(), tokens.end() - keep, tokens.end());
    tokens.swap(out);
}

// ---------- Sanitizer (strong, used by non-streaming only) ----------
static void drop_lines_with_prefix(std::string &s, const char *prefix_lc) {
    std::string out; out.reserve(s.size());
    size_t i = 0, line_start = 0;
    while (i <= s.size()) {
        if (i == s.size() || s[i] == '\n') {
            std::string_view line(s.data() + line_start, i - line_start);
            std::string line_lc = to_lower(std::string(line));
            if (!(line_lc.rfind(prefix_lc, 0) == 0)) {
                out.append(s.data() + line_start, i - line_start);
                if (i != s.size()) out.push_back('\n');
            }
            line_start = i + 1;
        }
        ++i;
    }
    s.swap(out);
}

// returns cleaned answer; fallback if too short or no alpha
static std::string sanitize_generation(std::string s) {
    if (s.empty()) return s;

    for (const char* stop : { "<end_of_turn>", "<|eot_id|>", "</s>" }) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); }
    }
    drop_lines_with_prefix(s, "<start_of_turn>");
    drop_lines_with_prefix(s, "<|start_header_id|>");
    drop_lines_with_prefix(s, "<|end_header_id|>");

    {
        std::string sl = to_lower(s);
        size_t qpos = sl.find("question:");
        if (qpos != std::string::npos) s = s.substr(0, qpos);
    }
    {
        std::string sl = to_lower(s);
        size_t cpos = sl.find("context:");
        if (cpos != std::string::npos) s = s.substr(0, cpos);
    }

    auto slice_after_tag = [&](const char* tag) -> bool {
        std::string low = to_lower(s);
        std::string t = to_lower(std::string(tag));
        size_t p = low.find(t);
        if (p != std::string::npos) {
            s = s.substr(p + std::strlen(tag));
            s = trim(s);
            return true;
        }
        return false;
    };
    (void)(slice_after_tag("ANSWER:") || slice_after_tag("FINAL_ANSWER:"));

    s = trim(s);
    auto strip_leading_noise = [](std::string &t) {
        auto ltrim_str = [&](const char* prefix) -> bool {
            size_t n = std::strlen(prefix);
            if (t.size() >= n && std::memcmp(t.data(), prefix, n) == 0) {
                t.erase(0, n);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                return true;
            }
            return false;
        };

        bool changed = true;
        while (changed) {
            changed = false;

            changed |= ltrim_str("• ");
            changed |= ltrim_str("- ");
            changed |= ltrim_str("* ");
            changed |= ltrim_str("> ");
            changed |= ltrim_str(u8"—");
            changed |= ltrim_str(u8"–");

            if (!t.empty() && (t[0] == ':' || t[0] == '-')) {
                t.erase(0, 1);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            }

            if (t.size() >= 2 && std::isdigit(static_cast<unsigned char>(t[0])) &&
                    (t[1] == '.' || t[1] == ')')) {
                t.erase(0, 2);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            } else if (t.size() >= 2 && std::isalpha(static_cast<unsigned char>(t[0])) &&
                    (t[1] == '.' || t[1] == ')')) {
                t.erase(0, 2);
                if (!t.empty() && t[0] == ' ') t.erase(0, 1);
                changed = true;
            }
        }

        size_t k = 0;
        while (k < t.size() && !std::isalnum(static_cast<unsigned char>(t[k]))) ++k;
        if (k > 0 && k < t.size()) t.erase(0, k);
    };
    strip_leading_noise(s);
    s = trim(s);

    bool has_alpha = std::any_of(s.begin(), s.end(), [](unsigned char c){ return std::isalpha(c); });
    if (!has_alpha || s.size() < 12) {
        return "I don't have enough information in my sources.";
    }

    {
        std::string low = to_lower(s);
        const char* fragments[] = {
                "answer only from the provided context",
                "do not repeat the context",
                "respond exactly: \"i don't have enough information in my sources",
                "instructions:",
                "begin your answer",
                "start your response",
                "do not include anything else",
                "reply with only the answer text"
        };
        for (const char* f : fragments) {
            size_t p = low.find(f);
            if (p != std::string::npos) {
                s = trim(s.substr(0, p));
                break;
            }
        }
    }

    return s;
}

// ---------- Chat templating ----------
static std::string build_user_with_context(const std::string &context_block,
        const std::string &user_question) {
    auto t = [](const std::string& x){ return trim(x); };
    if (t(context_block).empty()) return "QUESTION:\n" + user_question;
    std::ostringstream oss;
    oss << "CONTEXT:\n" << context_block << "\n\nQUESTION:\n" << user_question;
    return oss.str();
}

static std::string build_chat_prompt_gemma(const std::string &system_msg,
        const std::string &user_msg) {
    std::ostringstream oss;
    const std::string sys = (system_msg.empty()
            ? "You are a careful assistant. Answer ONLY from the provided context. "
              "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
              "Write 2–5 short sentences in plain text. Do not use bullets or numbering."
            : system_msg + " Write 2–5 short sentences in plain text. Do not use bullets or numbering.");

    oss << "<start_of_turn>system\n"
        << sys
        << "\n<end_of_turn>\n"
        << "<start_of_turn>user\n"
        << user_msg
        << "\n<end_of_turn>\n"
        << "<start_of_turn>model\n"
        << "ANSWER: ";
    return oss.str();
}

// ===================================================================================
//                                   EMBEDDINGS
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_LlamaBridge_initModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("initModel (embed): %s", path ? path : "(null)");

    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    llama_model_params mparams = llama_model_default_params();
    emb_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!emb_model) { LOGE("embed model load failed"); return JNI_FALSE; }

    llama_context_params cparams = llama_context_default_params();
    cparams.embeddings = true;
    cparams.n_ctx      = 2048;
    emb_ctx = llama_init_from_model(emb_model, cparams);
    if (!emb_ctx) { llama_model_free(emb_model); emb_model = nullptr; return JNI_FALSE; }

    emb_dim = llama_model_n_embd(emb_model);
    LOGI("Embed context ready. dim=%d", emb_dim);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_llamatik_library_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    if (!emb_ctx || !emb_model) { LOGE("embed: ctx/model null"); return nullptr; }

    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    if (!inputStr) { LOGE("embed: input null"); return nullptr; }

    std::vector<llama_token> tokens(1024);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(emb_model),
            inputStr, tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);
    env->ReleaseStringUTFChars(input, inputStr);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(emb_ctx)) {
        LOGW("embed tokenize fail/too long. n=%d ctx=%d", n_tokens, llama_n_ctx(emb_ctx));
        return nullptr;
    }
    tokens.resize(n_tokens);

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = false;
    }

    if (llama_decode(emb_ctx, batch) != 0) {
        LOGE("embed: llama_decode failed");
        llama_batch_free(batch);
        return nullptr;
    }

    const float *e = llama_get_embeddings_seq(emb_ctx, 0);
    if (!e) { LOGE("embed: embeddings null"); llama_batch_free(batch); return nullptr; }

    const int dim = llama_model_n_embd(emb_model);
    jfloatArray result = env->NewFloatArray(dim);
    if (!result) { llama_batch_free(batch); return nullptr; }
    env->SetFloatArrayRegion(result, 0, dim, e);
    llama_batch_free(batch);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_shutdown(JNIEnv *, jobject) {
if (emb_ctx)   llama_free(emb_ctx);
if (emb_model) llama_model_free(emb_model);
emb_ctx = nullptr; emb_model = nullptr;

if (gen_ctx)   llama_free(gen_ctx);
if (gen_model) llama_model_free(gen_model);
gen_ctx = nullptr; gen_model = nullptr;

if (g_backend_inited) {
llama_backend_free();
g_backend_inited = false;
}
}

// ===================================================================================
//                               TEXT GENERATION
// ===================================================================================

extern "C"
JNIEXPORT jboolean JNICALL
        Java_com_llamatik_library_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
const char *path = env->GetStringUTFChars(modelPath, nullptr);
LOGI("initGenerateModel: %s", path ? path : "(null)");

if (!g_backend_inited) {
llama_backend_init();
g_backend_inited = true;
}

llama_model_params mparams = llama_model_default_params();
gen_model = llama_model_load_from_file(path, mparams);
env->ReleaseStringUTFChars(modelPath, path);
if (!gen_model) { LOGE("gen model load failed"); return JNI_FALSE; }

llama_context_params cparams = llama_context_default_params();
cparams.embeddings = false;
cparams.n_ctx      = 4096;
gen_ctx = llama_init_from_model(gen_model, cparams);
if (!gen_ctx) { llama_model_free(gen_model); gen_model = nullptr; return JNI_FALSE; }

LOGI("Gen context ready. n_ctx=%d", llama_n_ctx(gen_ctx));
return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
        Java_com_llamatik_library_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
if (!gen_ctx || !gen_model) { LOGE("generate: ctx/model null"); return nullptr; }

const char *prompt = env->GetStringUTFChars(input, nullptr);
if (!prompt) { LOGE("generate: prompt null"); return nullptr; }

// ---- one-shot generate using the same loop as streaming but buffering output ----
llama_memory_clear(llama_get_memory(gen_ctx), false);

std::vector<llama_token> tokens(2048);
int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
        prompt, tokens, /*add_bos*/ true, /*parse_special*/ true);
env->ReleaseStringUTFChars(input, prompt);
if (n_tokens <= 0) { LOGE("tokenize failed"); return nullptr; }
tokens.resize(n_tokens);

const int n_ctx = llama_n_ctx(gen_ctx);
if ((int)tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
batch.n_tokens = (int)tokens.size();
for (int i = 0; i < batch.n_tokens; ++i) {
batch.token[i]     = tokens[i];
batch.pos[i]       = i;
batch.n_seq_id[i]  = 1;
batch.seq_id[i][0] = 0;
batch.logits[i]    = (i == batch.n_tokens - 1);
}
if (llama_decode(gen_ctx, batch) != 0) {
llama_batch_free(batch);
LOGE("decode failed on prompt");
return nullptr;
}

llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

const int max_new_tokens = 640;
int cur_pos = batch.n_tokens;

std::string output;
char buf[8192];

for (int i = 0; i < max_new_tokens; ++i) {
llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
if (tok < 0) break;
if (tok == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

// early stop on chat EOT
char sp[64];
int sn = llama_token_to_piece(llama_model_get_vocab(gen_model), tok, sp, (int)sizeof(sp), 0, 1);
if (sn > 0) {
sp[std::min(sn, (int)sizeof(sp)-1)] = '\0';
if (std::strcmp(sp, "<end_of_turn>") == 0 || std::strcmp(sp, "<|eot_id|>") == 0) break;
}

llama_sampler_accept(sampler, tok);
int nn = llama_token_to_piece(llama_model_get_vocab(gen_model), tok, buf, (int)sizeof(buf), 0, 0);
if (nn > 0) output.append(buf, nn);

if (cur_pos >= n_ctx) break;

llama_batch step = llama_batch_init(1, 0, 1);
step.n_tokens = 1;
step.token[0] = tok;
step.pos[0]   = cur_pos++;
step.n_seq_id[0]  = 1;
step.seq_id[0][0] = 0;
step.logits[0]    = true;
if (llama_decode(gen_ctx, step) != 0) { llama_batch_free(step); break; }
llama_batch_free(step);
}

llama_sampler_free(sampler);
llama_batch_free(batch);

std::string clean = sanitize_generation(output);
return env->NewStringUTF(clean.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
        Java_com_llamatik_library_platform_LlamaBridge_generateWithContext(
        JNIEnv *env, jobject, jstring jSystem, jstring jContext, jstring jUser) {

const char *psys = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
const char *pusr =            env->GetStringUTFChars(jUser,    nullptr);

std::string system = psys ? psys : "";
std::string ctx    = pctx ? pctx : "";
std::string user   = pusr ? pusr : "";

if (jSystem)  env->ReleaseStringUTFChars(jSystem,  psys);
if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
if (jUser)    env->ReleaseStringUTFChars(jUser,    pusr);

if (trim(system).empty()) {
system = "You are a careful assistant. Answer ONLY from the provided context. "
         "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
         "Write 2–5 short sentences in plain text. Do not use bullets or numbering.";
}

std::string user_turn = build_user_with_context(ctx, user);
std::string prompt    = build_chat_prompt_gemma(system, user_turn);
jstring jp = env->NewStringUTF(prompt.c_str());
jstring r  = Java_com_llamatik_library_platform_LlamaBridge_generate(env, nullptr, jp);
env->DeleteLocalRef(jp);
return r;
}

// ===================================================================================
//                        REAL TOKEN STREAMING (JNI CALLBACKS)
// ===================================================================================

struct StreamMethods {
    jmethodID onDelta;
    jmethodID onComplete;
    jmethodID onError;
};

static bool resolve_stream_methods(JNIEnv* env, jobject cb, StreamMethods& m) {
jclass cls = env->GetObjectClass(cb);
if (!cls) return false;
m.onDelta    = env->GetMethodID(cls, "onDelta", "(Ljava/lang/String;)V");
m.onComplete = env->GetMethodID(cls, "onComplete", "()V");
m.onError    = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
return m.onDelta && m.onComplete && m.onError;
}

// Light, streaming-friendly cut (no fallback)
static inline bool is_eot_piece(const char* s) {
    return std::strcmp(s, "<end_of_turn>") == 0 || std::strcmp(s, "<|eot_id|>") == 0;
}

// Streams tokens from a prepared prompt string
static void stream_from_prompt(JNIEnv* env, const char* prompt, jobject jCallback, const StreamMethods& m) {
if (!gen_ctx || !gen_model) {
env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("model not initialized"));
return;
}

llama_memory_clear(llama_get_memory(gen_ctx), false);

std::vector<llama_token> tokens(2048);
int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
        prompt, tokens, /*add_bos*/ true, /*parse_special*/ true);
if (n_tokens <= 0) {
env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("tokenization failed"));
return;
}
tokens.resize(n_tokens);

const int n_ctx = llama_n_ctx(gen_ctx);
if ((int)tokens.size() > n_ctx - 8) truncate_to_ctx(tokens, n_ctx, 8);

llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
batch.n_tokens = (int)tokens.size();
for (int i = 0; i < batch.n_tokens; ++i) {
batch.token[i]     = tokens[i];
batch.pos[i]       = i;
batch.n_seq_id[i]  = 1;
batch.seq_id[i][0] = 0;
batch.logits[i]    = (i == batch.n_tokens - 1);
}
if (llama_decode(gen_ctx, batch) != 0) {
llama_batch_free(batch);
env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed on prompt"));
return;
}

llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

const int max_new_tokens = 640;
int cur_pos = batch.n_tokens;

char piece_buf[768];
char spec_buf[64];

for (int i = 0; i < max_new_tokens; ++i) {
llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
if (tok < 0) break;
if (tok == llama_vocab_eos(llama_model_get_vocab(gen_model))) break;

int sn = llama_token_to_piece(llama_model_get_vocab(gen_model),
        tok, spec_buf, (int)sizeof(spec_buf),
        /* lstrip */ 0, /* special */ 1);
if (sn > 0) {
spec_buf[std::min(sn, (int)sizeof(spec_buf)-1)] = '\0';
if (is_eot_piece(spec_buf) || std::strcmp(spec_buf, "<start_of_turn>") == 0) {
break;
}
}

llama_sampler_accept(sampler, tok);

int nn = llama_token_to_piece(llama_model_get_vocab(gen_model),
        tok, piece_buf, (int)sizeof(piece_buf),
        /* lstrip */ 0, /* special */ 0);
if (nn > 0) {
piece_buf[std::min(nn, (int)sizeof(piece_buf)-1)] = '\0';
jstring delta = env->NewStringUTF(piece_buf);
if (delta) {
env->CallVoidMethod(jCallback, m.onDelta, delta);
env->DeleteLocalRef(delta);
}
}

if (cur_pos >= n_ctx) break;

llama_batch step = llama_batch_init(1, 0, 1);
step.n_tokens = 1;
step.token[0] = tok;
step.pos[0]   = cur_pos++;
step.n_seq_id[0]  = 1;
step.seq_id[0][0] = 0;
step.logits[0]    = true;

if (llama_decode(gen_ctx, step) != 0) {
llama_batch_free(step);
env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("llama_decode failed mid-stream"));
break;
}
llama_batch_free(step);
}

llama_sampler_free(sampler);
llama_batch_free(batch);

env->CallVoidMethod(jCallback, m.onComplete);
}

// JNI: stream(prompt, callback)
extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateStream(
        JNIEnv* env, jobject /*thiz*/, jstring jPrompt, jobject jCallback) {
if (!jPrompt || !jCallback) return;

StreamMethods m{};
if (!resolve_stream_methods(env, jCallback, m)) {
LOGE("nativeGenerateStream: failed to resolve callback methods");
return;
}

const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);
if (!prompt) {
env->CallVoidMethod(jCallback, m.onError, env->NewStringUTF("prompt decode failed"));
return;
}
stream_from_prompt(env, prompt, jCallback, m);
env->ReleaseStringUTFChars(jPrompt, prompt);
}

// Optional: streamWithContext(system, context, user, callback)
extern "C"
JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_LlamaBridge_nativeGenerateWithContextStream(
        JNIEnv* env, jobject /*thiz*/,
jstring jSystem, jstring jContext, jstring jUser, jobject jCallback) {
if (!jCallback) return;

StreamMethods m{};
if (!resolve_stream_methods(env, jCallback, m)) {
LOGE("nativeGenerateWithContextStream: failed to resolve callback methods");
return;
}

const char *psys = jSystem  ? env->GetStringUTFChars(jSystem,  nullptr) : nullptr;
const char *pctx = jContext ? env->GetStringUTFChars(jContext, nullptr) : nullptr;
const char *pusr = jUser    ? env->GetStringUTFChars(jUser,    nullptr) : nullptr;

std::string system = psys ? psys : "";
std::string ctx    = pctx ? pctx : "";
std::string user   = pusr ? pusr : "";

if (jSystem)  env->ReleaseStringUTFChars(jSystem,  psys);
if (jContext) env->ReleaseStringUTFChars(jContext, pctx);
if (jUser)    env->ReleaseStringUTFChars(jUser,    pusr);

if (trim(system).empty()) {
system = "You are a careful assistant. Answer ONLY from the provided context. "
         "If the context is insufficient, respond exactly: \"I don't have enough information in my sources.\" "
         "Write 2–5 short sentences in plain text. Do not use bullets or numbering.";
}
std::string user_turn = build_user_with_context(ctx, user);
std::string prompt    = build_chat_prompt_gemma(system, user_turn);

stream_from_prompt(env, prompt.c_str(), jCallback, m);
}
