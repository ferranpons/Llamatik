#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <vector>
#include <string>
#include <cstdint>
#include <algorithm>
#include <cctype>   // tolower, isalpha

#ifdef __APPLE__
#include <TargetConditionals.h>
#else
#define TARGET_OS_SIMULATOR 0
#endif

// ===================== Debug logging =====================

static bool g_enable_debug = false;

static void dbg_init() {
    if (g_enable_debug) return;
    const char *e = std::getenv("LLAMATIK_DEBUG");
    g_enable_debug = (e && std::strcmp(e, "0") != 0);
}

static void dbg_printf(const char *fmt, ...) {
    if (!g_enable_debug) return;
    va_list args;
    va_start(args, fmt);
    std::vfprintf(stderr, fmt, args);
    std::fprintf(stderr, "\n");
    va_end(args);
}

#define DBG(fmt, ...) \
    do { dbg_printf("[ios] " fmt, ##__VA_ARGS__); } while (0)

// ===================== Global state =====================

static struct llama_model   *model      = nullptr; // embeddings model
static struct llama_context *ctx        = nullptr;
static int                   embedding_size = 0;

static struct llama_model   *gen_model  = nullptr; // generation model
static struct llama_context *gen_ctx    = nullptr;

static bool g_backend_inited = false;

// ===================== Helpers =====================

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

static llama_model *load_model_with_fallback(const char *path) {
    llama_model_params mp = llama_model_default_params();

#if TARGET_OS_SIMULATOR
    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
#endif

    llama_model *m = llama_model_load_from_file(path, mp);
    if (m) return m;

    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;

    return llama_model_load_from_file(path, mp);
}

// ===================== Prompt builders =====================
//
// IMPORTANT: We DO NOT include system instructions verbatim in the prompt text.
// We just structure the task as Context + Question + "Answer:" cue so the model
// finishes the answer without echoing roles.

static std::string build_plain_prompt(const std::string &context_block,
        const std::string &user_msg) {
    std::string p;
    p.reserve(context_block.size() + user_msg.size() + 128);
    if (!context_block.empty()) {
        p += "Context:\n";
        p += context_block;
        p += "\n\n";
    }
    p += "Question:\n";
    p += user_msg;
    p += "\n\nAnswer:\n";
    return p;
}

// No chat template path in this build; keep stub for future wiring.
static bool apply_chat_template_if_available(const char *system_msg,
        const char *user_msg,
        std::string &wrapped) {
    (void)system_msg; (void)user_msg; (void)wrapped;
    return false;
}

// ===================== Text sanitation =====================

static inline std::string trim_ios(std::string s) {
    auto b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    auto e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}
static inline std::string to_lower_ios(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
            [](unsigned char c){ return char(std::tolower(c)); });
    return s;
}

static void drop_lines_with_prefix_ci(std::string &s, const char *prefix_ci) {
    std::string out; out.reserve(s.size());
    size_t i = 0, line_start = 0;
    const std::string pfx = to_lower_ios(prefix_ci);
    while (i <= s.size()) {
        if (i == s.size() || s[i] == '\n') {
            std::string line(s.data()+line_start, i - line_start);
            std::string lc = to_lower_ios(line);
            if (!(lc.rfind(pfx, 0) == 0)) {
                out.append(line);
                if (i != s.size()) out.push_back('\n');
            }
            line_start = i + 1;
        }
        ++i;
    }
    s.swap(out);
}

static void drop_lines_containing_ci(std::string &s, const char *needle_ci) {
    std::string out; out.reserve(s.size());
    const std::string ndl = to_lower_ios(needle_ci);
    size_t i = 0, line_start = 0;
    while (i <= s.size()) {
        if (i == s.size() || s[i] == '\n') {
            std::string line(s.data()+line_start, i - line_start);
            std::string lc = to_lower_ios(line);
            if (lc.find(ndl) == std::string::npos) {
                out.append(line);
                if (i != s.size()) out.push_back('\n');
            }
            line_start = i + 1;
        }
        ++i;
    }
    s.swap(out);
}

static std::string sanitize_generation_ios(std::string s) {
    if (s.empty()) return s;

    // 1) cut at common EOT / next-turn markers
    for (const char* stop : { "<end_of_turn>", "<|eot_id|>", "</s>", "<start_of_turn>" }) {
        size_t p = s.find(stop);
        if (p != std::string::npos) { s = s.substr(0, p); }
    }

    // 2) remove leaked role headers and common labels
    for (const char* pfx : { "assistant:", "user:", "system:", "answer:" }) {
        drop_lines_with_prefix_ci(s, pfx);
    }

    // 3) strip any accidental copies of your system guidance
    for (const char* sub : {
            "you are a helpful technical assistant",
            "answer in plain text",
            "do not echo the question",
            "never write role labels"
    }) {
        drop_lines_containing_ci(s, sub);
    }

    // 4) if we still see a lone "Answer:" cue, remove that cue only
    {
        std::string low = to_lower_ios(s);
        size_t p = low.find("answer:");
        if (p != std::string::npos) {
            // delete "Answer:" and any immediate single space
            size_t end = p + 7;
            if (end < s.size() && s[end] == ' ') ++end;
            s.erase(p, end - p);
        }
    }

    // 5) trim
    s = trim_ios(s);
    return s;
}

// === Streaming: robust gate so we don't show partial headers or "Answer:" ===

// Return the index where real content starts, or npos if we should wait for more chars.
static size_t find_stream_start(const std::string &s) {
    size_t i = 0;

    auto is_space = [](char c){ return c==' '||c=='\t'||c=='\r'||c=='\n'; };
    auto starts_ci = [&](size_t pos, const char* w)->bool{
        size_t n = std::strlen(w);
        if (pos + n > s.size()) return false;
        for (size_t k = 0; k < n; ++k) {
            char a = std::tolower((unsigned char)s[pos+k]);
            char b = std::tolower((unsigned char)w[k]);
            if (a != b) return false;
        }
        return true;
    };
    auto is_prefix_ci = [&](size_t pos, const char* w)->bool{
        size_t n = std::strlen(w);
        size_t len = std::min(n, s.size() - pos);
        for (size_t k = 0; k < len; ++k) {
            char a = std::tolower((unsigned char)s[pos+k]);
            char b = std::tolower((unsigned char)w[k]);
            if (a != b) return false;
        }
        return true; // s[pos..] matches the prefix of w
    };

    // skip leading whitespace
    while (i < s.size() && is_space(s[i])) ++i;

    while (i < s.size()) {
        // Incomplete or complete tag line: wait until '>' then skip it (and trailing spaces/newline)
        if (s[i] == '<') {
            size_t gt = s.find('>', i + 1);
            size_t nl = s.find('\n', i);
            if (gt == std::string::npos || (nl != std::string::npos && nl < gt)) {
                return std::string::npos; // incomplete tag line
            }
            i = gt + 1;
            while (i < s.size() && is_space(s[i])) ++i;
            continue;
        }

        // Handle role labels (assistant|user|system|answer), even if partial
        if (is_prefix_ci(i, "assistant") || is_prefix_ci(i, "user") ||
                is_prefix_ci(i, "system") || is_prefix_ci(i, "answer")) {
            // If we don't yet have the full word, wait.
            if (!(starts_ci(i, "assistant") || starts_ci(i, "user") ||
                    starts_ci(i, "system") || starts_ci(i, "answer"))) {
                return std::string::npos; // partial like "Assis" or "Ans"
            }
            // We have the full word; if colon not here yet, wait one more char.
            size_t j = i;
            while (j < s.size() && std::isalpha((unsigned char)s[j])) ++j;
            if (j >= s.size()) return std::string::npos; // need more to see ':' or content

            if (s[j] == ':') {
                // Skip "Label:" + spaces and an optional newline, then continue scanning
                ++j;
                while (j < s.size() && (s[j] == ' ' || s[j] == '\t')) ++j;
                if (j < s.size() && s[j] == '\n') {
                    ++j;
                    while (j < s.size() && is_space(s[j])) ++j;
                }
                i = j;
                continue; // drop the label and keep looking
            }
            // Full word but no colon: treat as normal content (rare)
            return i;
        }

        // Otherwise, content starts here.
        return i;
    }

    return std::string::npos;
}

// ===================== Embeddings =====================

extern "C" {

bool llama_embed_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    model = load_model_with_fallback(model_path);
    if (!model) return false;

    llama_context_params cp = llama_context_default_params();
    cp.embeddings = true;
    cp.n_ctx      = 2048;

    ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        llama_model_free(model);
        model = nullptr;
        return false;
    }

    embedding_size = llama_model_n_embd(model);
    DBG("embed: dim=%d", embedding_size);
    return true;
}

float *llama_embed(const char *input) {
    if (!ctx || !model || !input) return nullptr;

    std::vector<llama_token> tokens(1024);
    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(model),
            input,
            tokens,
            /*add_bos*/ true,
            /*parse_special*/ false);

    if (n_tokens <= 0 || n_tokens > llama_n_ctx(ctx)) {
        DBG("embed: tokenize fail/too long n=%d ctx=%u", n_tokens, (unsigned)llama_n_ctx(ctx));
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

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        DBG("embed: decode failed");
        return nullptr;
    }

    const float *emb = llama_get_embeddings_seq(ctx, 0);
    if (!emb) {
        llama_batch_free(batch);
        DBG("embed: embeddings null");
        return nullptr;
    }

    const int dim = llama_model_n_embd(model);
    float *out = (float *) std::malloc(sizeof(float) * (size_t)dim);
    if (!out) {
        llama_batch_free(batch);
        return nullptr;
    }
    std::memcpy(out, emb, sizeof(float) * (size_t)dim);
    llama_batch_free(batch);
    return out;
}

int   llama_embedding_size()          { return llama_model_n_embd(model); }
void  llama_free_embedding(float *p)  { if (p) std::free(p); }

void llama_embed_free() {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr; model = nullptr;

    if (!gen_ctx && !gen_model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

// ===================== Text Generation =====================

bool llama_generate_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        g_backend_inited = true;
    }

    gen_model = load_model_with_fallback(model_path);
    if (!gen_model) return false;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.embeddings = false;
    ctx_params.n_ctx      = 8192;   // larger context

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return false;
    }
    DBG("generate: n_ctx = %u", (unsigned)llama_n_ctx(gen_ctx));
    return true;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    llama_memory_clear(llama_get_memory(gen_ctx), false);

    // We treat `prompt` we receive as the *Question* and build our wrapper.
    std::string wrapped;
    if (!apply_chat_template_if_available(nullptr, prompt, wrapped)) {
        wrapped = build_plain_prompt(/*context=*/"", /*question=*/prompt);
    }

    // 2) Tokenize + prompt decode
    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) return nullptr;
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int) n_ctx - 8) {
        truncate_to_ctx(tokens, (int) n_ctx, 8);
        DBG("generate: prompt truncated");
    }

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
        DBG("generate: decode prompt failed");
        return nullptr;
    }

    // Sampler
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 3) Decode loop
    std::vector<llama_token> out;
    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::max(remaining_ctx, 2048);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) {
            DBG("generate: hit EOS");
            break;
        }

        // Early stop on common “end” pieces
        char piece[64];
        int nn = llama_token_to_piece(v, tok, piece, (int)sizeof(piece), 0, /*special*/ true);
        if (nn > 0) {
            if (nn >= (int)sizeof(piece)) piece[sizeof(piece)-1] = '\0';
            else piece[nn] = '\0';
            if (std::strcmp(piece, "<|eot_id|>") == 0 ||
                    std::strcmp(piece, "<end_of_turn>") == 0 ||
                    std::strcmp(piece, "</s>") == 0 ||
                    std::strcmp(piece, "<start_of_turn>") == 0) {
                DBG("generate: hit EOT piece: %s", piece);
                break;
            }
        }

        llama_sampler_accept(sampler, tok);
        out.push_back(tok);

        if (cur_pos >= (int)n_ctx) {
            DBG("generate: context full at %d positions", cur_pos);
            break;
        }

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens      = 1;
        step.token[0]      = tok;
        step.pos[0]        = cur_pos;
        step.n_seq_id[0]   = 1;
        step.seq_id[0][0]  = 0;
        step.logits[0]     = true;

        if (llama_decode(gen_ctx, step) != 0) {
            DBG("generate: decode step failed at pos=%d", cur_pos);
            llama_batch_free(step);
            break;
        }
        cur_pos++;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    // 4) Detokenize
    std::string text;
    char buf[8192];
    for (llama_token t : out) {
        int n = llama_token_to_piece(v, t, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n > 0) {
            if (n >= (int)sizeof(buf)) buf[sizeof(buf)-1] = '\0';
            text.append(buf, n);
        }
    }

    // Strong sanitize (remove any leaked labels / system echoes)
    text = sanitize_generation_ios(std::move(text));

    char *result = (char *) std::malloc(text.size() + 1);
    if (!result) return nullptr;
    std::memcpy(result, text.c_str(), text.size() + 1);
    return result;
}

// Clean prompt helper used by chat wrapper
static std::string build_clean_prompt(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    (void)system_prompt; // not injected into text; we keep only a simple structure
    std::string ctx = context_block ? context_block : "";
    std::string usr = user_prompt   ? user_prompt   : "";
    return build_plain_prompt(ctx, usr);
}

char *llama_generate_chat(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    std::string prompt = build_clean_prompt(system_prompt, context_block, user_prompt);
    char *raw = llama_generate(prompt.c_str());
    return raw; // already sanitized inside llama_generate
}

// ===================== Streaming APIs (iOS) =====================

typedef void (*llm_on_delta)(const char *utf8, void *user);
typedef void (*llm_on_done)(void *user);
typedef void (*llm_on_error)(const char *utf8, void *user);

void llama_generate_stream(const char *prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    if (!gen_ctx || !gen_model || !prompt) { if (on_error) on_error("generator not ready", user); return; }

    llama_memory_clear(llama_get_memory(gen_ctx), false);

    // Wrap incoming prompt as Question only (no system echo)
    std::string wrapped;
    if (!apply_chat_template_if_available(nullptr, prompt, wrapped)) {
        wrapped = build_plain_prompt(/*context=*/"", /*question=*/prompt);
    }

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(llama_model_get_vocab(gen_model),
            wrapped.c_str(),
            tokens, /*add_bos*/ true, /*parse_special*/ true);
    if (n_tokens <= 0) { if (on_error) on_error("tokenize failed", user); return; }
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int)n_ctx - 8) {
        truncate_to_ctx(tokens, (int)n_ctx, 8);
    }

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
        if (on_error) on_error("decode failed", user);
        return;
    }

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        if (on_error) on_error("sampler init failed", user);
        return;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, 1.10f, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.80f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.55f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const llama_vocab *v = llama_model_get_vocab(gen_model);

    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::max(remaining_ctx, 2048);

    std::string assembled;            // raw accumulation from tokens (no specials)
    size_t start_idx = std::string::npos; // where real content begins
    size_t sent_from_start = 0;           // how many chars emitted from [start_idx..)

    assembled.reserve(4096);

    for (int i = 0; i < max_new_tokens; ++i) {
        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) break;

        // Early stop on common “end” pieces (including turn tags)
        char spiece[64];
        int nn = llama_token_to_piece(v, tok, spiece, (int)sizeof(spiece), 0, /*special*/ true);
        if (nn > 0) {
            if (nn >= (int)sizeof(spiece)) spiece[sizeof(spiece)-1] = '\0';
            else spiece[nn] = '\0';
            if (std::strcmp(spiece, "<|eot_id|>") == 0 ||
                    std::strcmp(spiece, "<end_of_turn>") == 0 ||
                    std::strcmp(spiece, "</s>") == 0 ||
                    std::strcmp(spiece, "<start_of_turn>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);

        // Append normal text piece (no specials) to assembled buffer
        char piece[256];
        int nout = llama_token_to_piece(v, tok, piece, (int)sizeof(piece), 0, /*special*/ false);
        if (nout > 0) {
            if (nout >= (int)sizeof(piece)) piece[sizeof(piece)-1] = '\0';
            assembled.append(piece, nout);

            // Try to find a safe start (skip role labels / "Answer:" / tags)
            if (start_idx == std::string::npos) {
                start_idx = find_stream_start(assembled);
            }

            // If we have a safe start, emit only the *new* tail from that point.
            if (start_idx != std::string::npos && assembled.size() > start_idx + sent_from_start) {
                const std::string_view delta(assembled.data() + start_idx + sent_from_start,
                        assembled.size() - (start_idx + sent_from_start));
                if (on_delta && !delta.empty()) on_delta(std::string(delta).c_str(), user);
                sent_from_start += delta.size();
            }
        }

        if (cur_pos >= (int)n_ctx) break;

        llama_batch step = llama_batch_init(1, 0, 1);
        step.n_tokens      = 1;
        step.token[0]      = tok;
        step.pos[0]        = cur_pos;
        step.n_seq_id[0]   = 1;
        step.seq_id[0][0]  = 0;
        step.logits[0]     = true;
        if (llama_decode(gen_ctx, step) != 0) {
            llama_batch_free(step);
            break;
        }
        cur_pos++;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    if (on_done) on_done(user);
}

void llama_generate_chat_stream(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    std::string prompt = build_clean_prompt(system_prompt ? system_prompt : "",
            context_block ? context_block : "",
            user_prompt ? user_prompt : "");
    llama_generate_stream(prompt.c_str(), on_delta, on_done, on_error, user);
}

void llama_generate_free() {
    if (gen_ctx)   llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx   = nullptr;
    gen_model = nullptr;

    if (!ctx && !model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

} // extern "C"