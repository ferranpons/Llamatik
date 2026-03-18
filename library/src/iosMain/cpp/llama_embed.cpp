#include "common/json-schema-to-grammar.h"
#include "nlohmann/json.hpp"

#include "llama.h"

#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <vector>
#include <string>
#include <cstdint>
#include <algorithm>
#include <cctype>
#include <cstdarg>
#include <atomic>
#include <filesystem>
#include <set>

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
static std::atomic<bool> g_cancel_requested{false};

// Generation parameters
static std::atomic<float> g_temperature{0.55f};
static std::atomic<int>   g_max_tokens{256};
static std::atomic<float> g_top_p{0.95f};
static std::atomic<int>   g_top_k{40};
static std::atomic<float> g_repeat_penalty{1.10f};

// Session / KV bookkeeping
static std::vector<llama_token> gen_session_tokens;
static int gen_n_past = 0;

// ===================== Helpers =====================

static bool build_json_grammar(const char *json_schema, std::string &out_grammar, std::string &out_err) {
    try {
        const std::string schema_str = (json_schema && json_schema[0]) ? std::string(json_schema) : std::string("{}");
        nlohmann::ordered_json schema = nlohmann::ordered_json::parse(schema_str);
        out_grammar = json_schema_to_grammar(schema, /*force_gbnf=*/false);
        return !out_grammar.empty();
    } catch (const std::exception &e) {
        out_err = e.what();
        return false;
    }
}

static std::string build_json_prompt_single(const char *prompt) {
    std::string p = prompt ? prompt : "";
    p += "\n\nReturn ONLY JSON. No markdown, no prose.";
    return p;
}

static std::string build_json_prompt_chat(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        const char *json_schema) {
    (void)system_prompt;
    std::string ctxb = context_block ? context_block : "";
    std::string usr  = user_prompt   ? user_prompt   : "";

    std::string p;
    if (!ctxb.empty()) {
        p += "Context:\n";
        p += ctxb;
        p += "\n\n";
    }
    p += "Request:\n";
    p += usr;
    p += "\n\n";
    if (json_schema && json_schema[0]) {
        p += "Return ONLY JSON matching the provided JSON Schema. No markdown, no prose.";
    } else {
        p += "Return ONLY valid JSON. No markdown, no prose.";
    }
    return p;
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

static std::string lower_ascii(std::string s) {
    std::transform(s.begin(), s.end(), s.begin(),
            [](unsigned char c){ return (char)std::tolower(c); });
    return s;
}

static std::vector<std::string> split_tokens_normalized(const std::string &s) {
    std::vector<std::string> out;
    std::string cur;
    for (char ch : s) {
        if (std::isalnum((unsigned char)ch)) {
            cur.push_back((char)std::tolower((unsigned char)ch));
        } else {
            if (!cur.empty()) {
                out.push_back(cur);
                cur.clear();
            }
        }
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

static int token_overlap_score(const std::string &a, const std::string &b) {
    const auto ta = split_tokens_normalized(a);
    const auto tb = split_tokens_normalized(b);

    std::set<std::string> sa(ta.begin(), ta.end());
    std::set<std::string> sb(tb.begin(), tb.end());

    int score = 0;
    for (const auto &x : sa) {
        if (sb.count(x)) ++score;
    }
    return score;
}

static std::string parent_container_root_from_path(const std::string &path) {
    const std::string marker = "/tmp/";
    const auto pos = path.find(marker);
    if (pos != std::string::npos) {
        return path.substr(0, pos);
    }
    return {};
}

static std::string try_resolve_existing_model_path(const char *requested_path_cstr) {
    if (!requested_path_cstr || !requested_path_cstr[0]) return {};

    namespace fs = std::filesystem;

    const std::string requested_path = requested_path_cstr;

    if (fs::exists(requested_path)) {
        return requested_path;
    }

    const fs::path req(requested_path);
    const std::string req_name = req.filename().string();
    const std::string req_stem = req.stem().string();

    DBG("resolve_path: requested missing path=%s", requested_path.c_str());

    std::vector<fs::path> candidates;

    if (!req.parent_path().empty() && fs::exists(req.parent_path())) {
        for (const auto &entry : fs::directory_iterator(req.parent_path())) {
            if (entry.is_regular_file()) {
                candidates.push_back(entry.path());
            }
        }
    }

    {
        const std::string root = parent_container_root_from_path(requested_path);
        if (!root.empty()) {
            fs::path models_dir = fs::path(root) / "Library" / "Application Support" / "Llamatik" / "models";
            if (fs::exists(models_dir)) {
                for (const auto &entry : fs::directory_iterator(models_dir)) {
                    if (entry.is_regular_file()) {
                        candidates.push_back(entry.path());
                    }
                }
            }

            fs::path caches_dir = fs::path(root) / "Library" / "Caches";
            if (fs::exists(caches_dir)) {
                for (const auto &entry : fs::directory_iterator(caches_dir)) {
                    if (entry.is_regular_file()) {
                        candidates.push_back(entry.path());
                    }
                }
            }

            fs::path tmp_dir = fs::path(root) / "tmp";
            if (fs::exists(tmp_dir)) {
                for (const auto &entry : fs::directory_iterator(tmp_dir)) {
                    if (entry.is_regular_file()) {
                        candidates.push_back(entry.path());
                    }
                }
            }
        }
    }

    for (const auto &p : candidates) {
        if (lower_ascii(p.filename().string()) == lower_ascii(req_name)) {
            DBG("resolve_path: exact filename match -> %s", p.string().c_str());
            return p.string();
        }
    }

    for (const auto &p : candidates) {
        if (lower_ascii(p.stem().string()) == lower_ascii(req_stem)) {
            DBG("resolve_path: exact stem match -> %s", p.string().c_str());
            return p.string();
        }
    }

    int best_score = -1;
    std::string best_path;
    for (const auto &p : candidates) {
        const std::string cand = p.filename().string();
        const int score = std::max(
                token_overlap_score(req_name, cand),
                token_overlap_score(req_stem, cand)
        );
        if (score > best_score) {
            best_score = score;
            best_path = p.string();
        }
    }

    if (best_score >= 2 && !best_path.empty()) {
        DBG("resolve_path: fuzzy match(score=%d) -> %s", best_score, best_path.c_str());
        return best_path;
    }

    DBG("resolve_path: no fallback match found");
    return {};
}

static bool model_description_contains(llama_model *m, const char *needle) {
    if (!m || !needle || !needle[0]) return false;

    char buf[512];
    std::memset(buf, 0, sizeof(buf));
    const int n = llama_model_desc(m, buf, (int)sizeof(buf));
    if (n <= 0) return false;

    const std::string desc = lower_ascii(std::string(buf));
    return desc.find(lower_ascii(std::string(needle))) != std::string::npos;
}

static bool model_is_gemma_family(llama_model *m) {
    return model_description_contains(m, "gemma");
}

static llama_model *load_model_with_fallback(const char *path) {
    namespace fs = std::filesystem;

    std::string resolved = try_resolve_existing_model_path(path);
    const char *final_path = resolved.empty() ? path : resolved.c_str();

    if (!final_path || !final_path[0]) {
        DBG("load_model_with_fallback: empty path");
        return nullptr;
    }

    if (!fs::exists(final_path)) {
        DBG("load_model_with_fallback: path still does not exist -> %s", final_path);
        return nullptr;
    }

    DBG("load_model_with_fallback: final_path=%s", final_path);

    llama_model_params mp = llama_model_default_params();

#if TARGET_OS_SIMULATOR
    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
#endif

    llama_model *m = llama_model_load_from_file(final_path, mp);
    if (m) return m;

    mp.use_mmap     = false;
    mp.use_mlock    = false;
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;

    return llama_model_load_from_file(final_path, mp);
}

// ===================== Prompt builders =====================

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

static std::string build_gemma_prompt_user_only(const char *user_msg) {
    std::string usr = user_msg ? user_msg : "";
    std::string p;
    p.reserve(usr.size() + 96);
    p += "<bos><start_of_turn>user\n";
    p += usr;
    p += "<end_of_turn>\n<start_of_turn>model\n";
    return p;
}

static std::string build_gemma_prompt_chat(const char *system_msg,
        const char *context_block,
        const char *user_msg) {
    std::string sys = system_msg ? system_msg : "";
    std::string ctx = context_block ? context_block : "";
    std::string usr = user_msg ? user_msg : "";

    std::string payload;
    if (!sys.empty()) {
        payload += "System:\n";
        payload += sys;
        payload += "\n\n";
    }
    if (!ctx.empty()) {
        payload += "Context:\n";
        payload += ctx;
        payload += "\n\n";
    }
    payload += "Question:\n";
    payload += usr;

    std::string p;
    p.reserve(payload.size() + 96);
    p += "<bos><start_of_turn>user\n";
    p += payload;
    p += "<end_of_turn>\n<start_of_turn>model\n";
    return p;
}

static bool apply_chat_template_if_available(const char *system_msg,
        const char *user_msg,
        std::string &wrapped) {
    if (gen_model && model_is_gemma_family(gen_model)) {
        wrapped = build_gemma_prompt_user_only(user_msg);
        return true;
    }
    (void)system_msg;
    (void)user_msg;
    (void)wrapped;
    return false;
}

static std::string build_clean_prompt(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    if (gen_model && model_is_gemma_family(gen_model)) {
        return build_gemma_prompt_chat(system_prompt, context_block, user_prompt);
    }

    std::string ctxb = context_block ? context_block : "";
    std::string usr = user_prompt   ? user_prompt   : "";
    return build_plain_prompt(ctxb, usr);
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

    for (const char* stop : { "<end_of_turn>", "<|eot_id|>", "</s>", "<start_of_turn>" }) {
        size_t p = s.find(stop);
        if (p != std::string::npos) {
            s = s.substr(0, p);
        }
    }

    for (const char* pfx : { "assistant:", "user:", "system:", "model:" }) {
        drop_lines_with_prefix_ci(s, pfx);
    }

    {
        std::string low = to_lower_ios(s);
        if (low.rfind("answer:", 0) == 0) {
            size_t cut = 7;
            if (cut < s.size() && s[cut] == ' ') ++cut;
            s.erase(0, cut);
        }
    }

    for (const char* sub : {
            "you are a helpful technical assistant",
            "answer in plain text",
            "do not echo the question",
            "never write role labels"
    }) {
        drop_lines_containing_ci(s, sub);
    }

    return trim_ios(std::move(s));
}

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
        return true;
    };

    while (i < s.size() && is_space(s[i])) ++i;

    while (i < s.size()) {
        if (s[i] == '<') {
            size_t gt = s.find('>', i + 1);
            size_t nl = s.find('\n', i);
            if (gt == std::string::npos || (nl != std::string::npos && nl < gt)) {
                return std::string::npos;
            }
            i = gt + 1;
            while (i < s.size() && is_space(s[i])) ++i;
            continue;
        }

        if (is_prefix_ci(i, "assistant") || is_prefix_ci(i, "user") ||
                is_prefix_ci(i, "system") || is_prefix_ci(i, "answer") ||
                is_prefix_ci(i, "model")) {
            if (!(starts_ci(i, "assistant") || starts_ci(i, "user") ||
                    starts_ci(i, "system") || starts_ci(i, "answer") ||
                    starts_ci(i, "model"))) {
                return std::string::npos;
            }
            size_t j = i;
            while (j < s.size() && std::isalpha((unsigned char)s[j])) ++j;
            if (j >= s.size()) return std::string::npos;

            if (s[j] == ':') {
                ++j;
                while (j < s.size() && (s[j] == ' ' || s[j] == '\t')) ++j;
                if (j < s.size() && s[j] == '\n') {
                    ++j;
                    while (j < s.size() && is_space(s[j])) ++j;
                }
                i = j;
                continue;
            }
            return i;
        }

        return i;
    }

    return std::string::npos;
}

// ===================== Session helpers =====================

static void session_clear_state_only() {
    gen_session_tokens.clear();
    gen_n_past = 0;
}

static void session_hard_reset_context() {
    if (!gen_ctx) return;
    llama_memory_clear(llama_get_memory(gen_ctx), false);
    session_clear_state_only();
}

static void session_record_prompt_tokens_fresh(const std::vector<llama_token> &prompt_tokens) {
    gen_session_tokens = prompt_tokens;
    gen_n_past = (int)prompt_tokens.size();
}

static void session_append_generated_token(llama_token tok) {
    gen_session_tokens.push_back(tok);
    ++gen_n_past;
}

static void session_append_prompt_tokens_continue(const std::vector<llama_token> &prompt_tokens) {
    gen_session_tokens.insert(gen_session_tokens.end(), prompt_tokens.begin(), prompt_tokens.end());
    gen_n_past += (int)prompt_tokens.size();
}

static bool session_is_active() {
    return gen_ctx != nullptr && gen_n_past > 0 && !gen_session_tokens.empty();
}

// ===================== C API =====================

extern "C" {

// ===================== Embeddings =====================

bool llama_embed_init(const char *model_path) {
    dbg_init();
    if (!g_backend_inited) {
        llama_backend_init();
        llama_log_set([](ggml_log_level level, const char * text, void * user_data) {
            (void)user_data;
            if (level == GGML_LOG_LEVEL_ERROR) {
                std::fprintf(stderr, "%s", text);
            }
        }, nullptr);
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

void llama_generate_cancel(void) {
    g_cancel_requested.store(true, std::memory_order_relaxed);
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

    if (n_tokens <= 0 || n_tokens > (int)llama_n_ctx(ctx)) {
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

int llama_embedding_size(void) {
    return llama_model_n_embd(model);
}

void llama_free_embedding(float *p) {
    if (p) std::free(p);
}

void llama_embed_free(void) {
    if (ctx)   llama_free(ctx);
    if (model) llama_model_free(model);
    ctx = nullptr;
    model = nullptr;

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
    ctx_params.n_ctx      = 8192;

#if TARGET_OS_SIMULATOR
    ctx_params.n_batch    = 512;
    ctx_params.n_ubatch   = 128;
#endif

    gen_ctx = llama_init_from_model(gen_model, ctx_params);
    if (!gen_ctx) {
        llama_model_free(gen_model);
        gen_model = nullptr;
        return false;
    }

    session_clear_state_only();
    DBG("generate: n_ctx=%u batch=%u ubatch=%u", (unsigned)llama_n_ctx(gen_ctx), (unsigned)ctx_params.n_batch, (unsigned)ctx_params.n_ubatch);
    return true;
}

char *llama_generate(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);
    session_hard_reset_context();

    std::string wrapped;
    if (!apply_chat_template_if_available(nullptr, prompt, wrapped)) {
        wrapped = build_plain_prompt("", prompt);
    }

    DBG("generate: wrapped prompt bytes=%d", (int)wrapped.size());

    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ false, /*parse_special*/ true);
    if (n_tokens <= 0) {
        DBG("generate: tokenize failed n=%d", n_tokens);
        return nullptr;
    }
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int)n_ctx - 8) {
        truncate_to_ctx(tokens, (int)n_ctx, 8);
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

    session_record_prompt_tokens_fresh(tokens);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::vector<llama_token> out;
    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::min(remaining_ctx, max_tokens);

    DBG("generate: cur_pos=%d remaining_ctx=%d max_new=%d", cur_pos, remaining_ctx, max_new_tokens);

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            DBG("generate: cancelled at token %d", i);
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) {
            DBG("generate: hit EOS/EOG");
            break;
        }

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
        session_append_generated_token(tok);

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
        gen_n_past = cur_pos;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string text;
    char buf[8192];
    for (llama_token t : out) {
        int n = llama_token_to_piece(v, t, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n > 0) {
            if (n >= (int)sizeof(buf)) buf[sizeof(buf)-1] = '\0';
            text.append(buf, n);
        }
    }

    DBG("generate: raw output bytes=%d", (int)text.size());

    text = sanitize_generation_ios(std::move(text));

    DBG("generate: sanitized output bytes=%d text='%s'", (int)text.size(), text.c_str());

    char *result = (char *) std::malloc(text.size() + 1);
    if (!result) return nullptr;
    std::memcpy(result, text.c_str(), text.size() + 1);
    return result;
}

char *llama_generate_chat(const char *system_prompt,
        const char *context_block,
        const char *user_prompt) {
    std::string prompt2 = build_clean_prompt(system_prompt, context_block, user_prompt);
    return llama_generate(prompt2.c_str());
}

char *llama_generate_json_schema(const char *prompt, const char *json_schema) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);
    session_hard_reset_context();

    std::string grammar;
    std::string err;
    if (!build_json_grammar(json_schema, grammar, err)) {
        DBG("json_schema_to_grammar failed: %s", err.c_str());
        return nullptr;
    }

    std::string wrapped = build_json_prompt_single(prompt);

    if (gen_model && model_is_gemma_family(gen_model)) {
        wrapped = build_gemma_prompt_user_only(wrapped.c_str());
    }

    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ false, /*parse_special*/ true);
    if (n_tokens <= 0) return nullptr;
    tokens.resize(n_tokens);

    const unsigned int n_ctx = llama_n_ctx(gen_ctx);
    if (n_tokens > (int)n_ctx - 8) {
        truncate_to_ctx(tokens, (int)n_ctx, 8);
        DBG("generate_json: prompt truncated");
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
        DBG("generate_json: decode prompt failed");
        return nullptr;
    }

    session_record_prompt_tokens_fresh(tokens);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_grammar(v, grammar.c_str(), "root"));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::vector<llama_token> out;
    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::min(remaining_ctx, max_tokens);

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            DBG("generate_json: cancelled at token %d", i);
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) {
            DBG("generate_json: hit EOS");
            break;
        }

        llama_sampler_accept(sampler, tok);
        out.push_back(tok);
        session_append_generated_token(tok);

        if (cur_pos >= (int)n_ctx) {
            DBG("generate_json: context full at %d positions", cur_pos);
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
            DBG("generate_json: decode step failed at pos=%d", cur_pos);
            llama_batch_free(step);
            break;
        }
        cur_pos++;
        gen_n_past = cur_pos;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string text;
    char buf[8192];
    for (llama_token t : out) {
        int n = llama_token_to_piece(v, t, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n > 0) {
            if (n >= (int)sizeof(buf)) buf[sizeof(buf)-1] = '\0';
            text.append(buf, n);
        }
    }

    text = trim_ios(text);

    char *result = (char *) std::malloc(text.size() + 1);
    if (!result) return nullptr;
    std::memcpy(result, text.c_str(), text.size() + 1);
    return result;
}

char *llama_generate_chat_json_schema(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        const char *json_schema) {
    std::string prompt2 = build_json_prompt_chat(system_prompt, context_block, user_prompt, json_schema);
    return llama_generate_json_schema(prompt2.c_str(), json_schema);
}

// ===================== Streaming APIs =====================

typedef void (*llm_on_delta)(const char *utf8, void *user);
typedef void (*llm_on_done)(void *user);
typedef void (*llm_on_error)(const char *utf8, void *user);

void llama_generate_stream(const char *prompt,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    if (!gen_ctx || !gen_model || !prompt) {
        if (on_error) on_error("generator not ready", user);
        return;
    }

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);
    session_hard_reset_context();

    std::string wrapped;
    if (!apply_chat_template_if_available(nullptr, prompt, wrapped)) {
        wrapped = build_plain_prompt("", prompt);
    }

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(
            llama_model_get_vocab(gen_model),
            wrapped.c_str(),
            tokens,
            /*add_bos*/ false,
            /*parse_special*/ true);

    if (n_tokens <= 0) {
        if (on_error) on_error("tokenize failed", user);
        return;
    }
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

    session_record_prompt_tokens_fresh(tokens);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        if (on_error) on_error("sampler init failed", user);
        return;
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const llama_vocab *v = llama_model_get_vocab(gen_model);

    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::min(remaining_ctx, max_tokens);

    std::string assembled;
    size_t start_idx = std::string::npos;
    size_t sent_from_start = 0;
    assembled.reserve(4096);

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            DBG("stream: cancelled at token %d", i);
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) break;

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
        session_append_generated_token(tok);

        char piece[256];
        int nout = llama_token_to_piece(v, tok, piece, (int)sizeof(piece), 0, /*special*/ false);
        if (nout > 0) {
            if (nout >= (int)sizeof(piece)) piece[sizeof(piece)-1] = '\0';
            assembled.append(piece, nout);

            if (start_idx == std::string::npos) {
                start_idx = find_stream_start(assembled);
            }

            if (start_idx != std::string::npos && assembled.size() > start_idx + sent_from_start) {
                const std::string_view delta(
                        assembled.data() + start_idx + sent_from_start,
                        assembled.size() - (start_idx + sent_from_start)
                );
                if (on_delta && !delta.empty()) {
                    std::string out(delta);
                    on_delta(out.c_str(), user);
                }
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
        gen_n_past = cur_pos;
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
    std::string prompt2 = build_clean_prompt(
            system_prompt ? system_prompt : "",
            context_block ? context_block : "",
            user_prompt ? user_prompt : ""
    );
    llama_generate_stream(prompt2.c_str(), on_delta, on_done, on_error, user);
}

void llama_generate_json_schema_stream(const char *prompt,
        const char *json_schema,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    if (!gen_ctx || !gen_model || !prompt) {
        if (on_error) on_error("generator not ready", user);
        return;
    }

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);
    session_hard_reset_context();

    std::string grammar;
    std::string err;
    if (!build_json_grammar(json_schema, grammar, err)) {
        if (on_error) on_error(err.c_str(), user);
        return;
    }

    std::string wrapped = build_json_prompt_single(prompt);
    if (gen_model && model_is_gemma_family(gen_model)) {
        wrapped = build_gemma_prompt_user_only(wrapped.c_str());
    }

    const llama_vocab *v = llama_model_get_vocab(gen_model);
    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, wrapped.c_str(), tokens, /*add_bos*/ false, /*parse_special*/ true);
    if (n_tokens <= 0) {
        if (on_error) on_error("tokenize failed", user);
        return;
    }
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
        if (on_error) on_error("decode prompt failed", user);
        return;
    }

    session_record_prompt_tokens_fresh(tokens);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        if (on_error) on_error("sampler init failed", user);
        return;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_grammar(v, grammar.c_str(), "root"));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string pending;
    bool started = false;
    size_t start_at = 0;

    int cur_pos = batch.n_tokens;
    const int safety = 16;
    int remaining_ctx = (int)n_ctx - cur_pos - safety;
    if (remaining_ctx < 0) remaining_ctx = 0;
    int max_new_tokens = std::min(remaining_ctx, max_tokens);

    for (int i = 0; i < max_new_tokens; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) {
            break;
        }

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) break;

        llama_sampler_accept(sampler, tok);
        session_append_generated_token(tok);

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
        llama_batch_free(step);
        cur_pos++;
        gen_n_past = cur_pos;

        char buf[256];
        int n = llama_token_to_piece(v, tok, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n <= 0) continue;

        pending.append(buf, (size_t)n);

        if (!started) {
            size_t st = find_stream_start(pending);
            if (st == std::string::npos) {
                continue;
            }
            started = true;
            start_at = st;
        }

        if (started && pending.size() > start_at) {
            std::string chunk = pending.substr(start_at);
            pending.clear();
            start_at = 0;
            if (on_delta) on_delta(chunk.c_str(), user);
        }
    }

    if (!pending.empty()) {
        if (!started) {
            size_t st = find_stream_start(pending);
            if (st != std::string::npos) {
                std::string chunk = pending.substr(st);
                if (on_delta) on_delta(chunk.c_str(), user);
            }
        } else {
            if (on_delta) on_delta(pending.c_str(), user);
        }
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    if (on_done) on_done(user);
}

void llama_generate_chat_json_schema_stream(const char *system_prompt,
        const char *context_block,
        const char *user_prompt,
        const char *json_schema,
        llm_on_delta on_delta,
        llm_on_done on_done,
        llm_on_error on_error,
        void *user) {
    std::string prompt2 = build_json_prompt_chat(system_prompt, context_block, user_prompt, json_schema);
    llama_generate_json_schema_stream(prompt2.c_str(), json_schema, on_delta, on_done, on_error, user);
}

void llama_generate_set_params(float temperature,
        int max_tokens,
        float top_p,
        int top_k,
        float repeat_penalty) {
    g_temperature.store(temperature, std::memory_order_relaxed);
    g_max_tokens.store(max_tokens, std::memory_order_relaxed);
    g_top_p.store(top_p, std::memory_order_relaxed);
    g_top_k.store(top_k, std::memory_order_relaxed);
    g_repeat_penalty.store(repeat_penalty, std::memory_order_relaxed);
}

// ===================== KV session support =====================

bool llama_generate_session_reset(void) {
    if (!gen_ctx) return false;
    session_hard_reset_context();
    return true;
}

bool llama_generate_session_save(const char *path_session) {
    if (!gen_ctx || !path_session) return false;
    return llama_state_save_file(
            gen_ctx,
            path_session,
            gen_session_tokens.empty() ? nullptr : gen_session_tokens.data(),
            gen_session_tokens.size()
    );
}

bool llama_generate_session_load(const char *path_session) {
    if (!gen_ctx || !path_session) return false;

    const int cap = std::max(1, (int)llama_n_ctx(gen_ctx));
    gen_session_tokens.resize(cap);
    size_t n_loaded = 0;

    const bool ok = llama_state_load_file(
            gen_ctx,
            path_session,
            gen_session_tokens.data(),
            gen_session_tokens.size(),
            &n_loaded
    );

    if (!ok) {
        session_clear_state_only();
        return false;
    }

    gen_session_tokens.resize((int)n_loaded);
    gen_n_past = (int)n_loaded;
    return true;
}

char *llama_generate_continue(const char *prompt) {
    if (!gen_ctx || !gen_model || !prompt) return nullptr;

    if (!session_is_active()) {
        return llama_generate(prompt);
    }

    const float temperature    = g_temperature.load(std::memory_order_relaxed);
    const int   max_tokens     = g_max_tokens.load(std::memory_order_relaxed);
    const float top_p          = g_top_p.load(std::memory_order_relaxed);
    const int   top_k          = g_top_k.load(std::memory_order_relaxed);
    const float repeat_penalty = g_repeat_penalty.load(std::memory_order_relaxed);

    g_cancel_requested.store(false, std::memory_order_relaxed);

    const llama_vocab *v = llama_model_get_vocab(gen_model);

    std::vector<llama_token> tokens(2048);
    int n_tokens = tokenize_with_retry(v, prompt, tokens, /*add_bos*/ false, /*parse_special*/ true);
    if (n_tokens <= 0) return nullptr;
    tokens.resize(n_tokens);

    const int n_ctx = (int)llama_n_ctx(gen_ctx);
    const int safety = 16;

    if (gen_n_past + (int)tokens.size() >= n_ctx - safety) {
        DBG("continue: context full, resetting session and falling back to fresh generate");
        return llama_generate(prompt);
    }

    llama_batch batch = llama_batch_init((int)tokens.size(), 0, 1);
    batch.n_tokens = (int)tokens.size();
    for (int i = 0; i < batch.n_tokens; ++i) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = gen_n_past + i;
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i]    = (i == batch.n_tokens - 1);
    }

    if (llama_decode(gen_ctx, batch) != 0) {
        llama_batch_free(batch);
        return nullptr;
    }

    session_append_prompt_tokens_continue(tokens);

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (!sampler) {
        llama_batch_free(batch);
        return nullptr;
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(128, repeat_penalty, 0.0f, 0.10f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::vector<llama_token> out;
    int cur_pos = gen_n_past;
    int remaining = n_ctx - cur_pos - safety;
    if (remaining < 0) remaining = 0;
    int max_new = std::min(remaining, max_tokens);

    for (int i = 0; i < max_new; ++i) {
        if (g_cancel_requested.load(std::memory_order_relaxed)) break;

        llama_token tok = llama_sampler_sample(sampler, gen_ctx, -1);
        if (tok < 0) break;
        if (llama_vocab_is_eog(v, tok)) break;

        char sp[64];
        int sn = llama_token_to_piece(v, tok, sp, (int)sizeof(sp), 0, /*special*/ true);
        if (sn > 0) {
            if (sn >= (int)sizeof(sp)) sp[sizeof(sp)-1] = '\0';
            else sp[sn] = '\0';
            if (std::strcmp(sp, "<|eot_id|>") == 0 ||
                    std::strcmp(sp, "<end_of_turn>") == 0 ||
                    std::strcmp(sp, "<start_of_turn>") == 0 ||
                    std::strcmp(sp, "</s>") == 0) {
                break;
            }
        }

        llama_sampler_accept(sampler, tok);
        out.push_back(tok);
        session_append_generated_token(tok);

        if (cur_pos >= n_ctx) break;

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
        gen_n_past = cur_pos;
        llama_batch_free(step);
    }

    llama_batch_free(batch);
    llama_sampler_free(sampler);

    std::string text;
    char buf[8192];
    for (llama_token t : out) {
        int n = llama_token_to_piece(v, t, buf, (int)sizeof(buf), 0, /*special*/ false);
        if (n > 0) text.append(buf, n);
    }

    text = sanitize_generation_ios(std::move(text));

    char *result = (char *) std::malloc(text.size() + 1);
    if (!result) return nullptr;
    std::memcpy(result, text.c_str(), text.size() + 1);
    return result;
}

void llama_generate_free(void) {
    if (gen_ctx)   llama_free(gen_ctx);
    if (gen_model) llama_model_free(gen_model);
    gen_ctx   = nullptr;
    gen_model = nullptr;
    session_clear_state_only();

    if (!ctx && !model && g_backend_inited) {
        llama_backend_free();
        g_backend_inited = false;
    }
}

} // extern "C"