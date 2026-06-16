#include <jni.h>
#include <cstring>
#include <string>
#include "whisper_stt.h"

// Converts a raw C string from whisper.cpp to a valid Modified UTF-8 string
// suitable for NewStringUTF(). Invalid byte sequences become U+FFFD; 4-byte
// UTF-8 sequences are re-encoded as CESU-8 surrogate pairs; embedded NUL bytes
// become the Modified UTF-8 two-byte NUL encoding (\xc0\x80).
static std::string sanitize_for_modified_utf8(const char* src) {
    std::string out;
    if (!src) return out;
    out.reserve(std::strlen(src));

    const unsigned char* p = reinterpret_cast<const unsigned char*>(src);
    while (*p) {
        const unsigned char c = *p;

        if (c == 0x00) {
            // Embedded NUL → Modified UTF-8 two-byte NUL
            out += '\xc0';
            out += '\x80';
            ++p;
        } else if (c < 0x80) {
            // ASCII
            out += static_cast<char>(c);
            ++p;
        } else if ((c & 0xE0) == 0xC0) {
            // 2-byte sequence
            if ((p[1] & 0xC0) == 0x80) {
                const uint32_t cp = ((c & 0x1Fu) << 6) | (p[1] & 0x3Fu);
                if (cp >= 0x80) {
                    out += static_cast<char>(c);
                    out += static_cast<char>(p[1]);
                } else {
                    out += "\xef\xbf\xbd"; // U+FFFD (overlong)
                }
                p += 2;
            } else {
                out += "\xef\xbf\xbd";
                ++p;
            }
        } else if ((c & 0xF0) == 0xE0) {
            // 3-byte sequence
            if ((p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80) {
                const uint32_t cp = ((c & 0x0Fu) << 12) | ((p[1] & 0x3Fu) << 6) | (p[2] & 0x3Fu);
                if (cp >= 0x800 && !(cp >= 0xD800 && cp <= 0xDFFF)) {
                    out += static_cast<char>(c);
                    out += static_cast<char>(p[1]);
                    out += static_cast<char>(p[2]);
                } else {
                    out += "\xef\xbf\xbd"; // overlong or lone surrogate
                }
                p += 3;
            } else {
                out += "\xef\xbf\xbd";
                ++p;
            }
        } else if ((c & 0xF8) == 0xF0) {
            // 4-byte sequence — re-encode as CESU-8 surrogate pair (valid Modified UTF-8)
            if ((p[1] & 0xC0) == 0x80 && (p[2] & 0xC0) == 0x80 && (p[3] & 0xC0) == 0x80) {
                const uint32_t cp = ((c & 0x07u) << 18) | ((p[1] & 0x3Fu) << 12) |
                                    ((p[2] & 0x3Fu) << 6)  |  (p[3] & 0x3Fu);
                if (cp >= 0x10000 && cp <= 0x10FFFF) {
                    const uint32_t v  = cp - 0x10000u;
                    const uint32_t hi = 0xD800u + (v >> 10);
                    const uint32_t lo = 0xDC00u + (v & 0x3FFu);
                    out += static_cast<char>(0xE0 | (hi >> 12));
                    out += static_cast<char>(0x80 | ((hi >> 6) & 0x3F));
                    out += static_cast<char>(0x80 | (hi & 0x3F));
                    out += static_cast<char>(0xE0 | (lo >> 12));
                    out += static_cast<char>(0x80 | ((lo >> 6) & 0x3F));
                    out += static_cast<char>(0x80 | (lo & 0x3F));
                } else {
                    out += "\xef\xbf\xbd";
                }
                p += 4;
            } else {
                out += "\xef\xbf\xbd";
                ++p;
            }
        } else {
            // Invalid start byte (continuation byte or 0xF8-0xFF)
            out += "\xef\xbf\xbd";
            ++p;
        }
    }
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_WhisperBridge_initModel(JNIEnv* env, jobject, jstring path) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int ok = whisper_stt_init(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_WhisperBridge_transcribeWav(JNIEnv* env, jobject, jstring wavPath, jstring lang, jstring initialPrompt) {
    const char* cwav = env->GetStringUTFChars(wavPath, nullptr);
    const char* clang = lang ? env->GetStringUTFChars(lang, nullptr) : nullptr;
    const char* cprompt = initialPrompt ? env->GetStringUTFChars(initialPrompt, nullptr) : nullptr;

    const char* out = whisper_stt_transcribe_wav(cwav, clang, cprompt);

    if (initialPrompt) env->ReleaseStringUTFChars(initialPrompt, cprompt);
    if (lang) env->ReleaseStringUTFChars(lang, clang);
    env->ReleaseStringUTFChars(wavPath, cwav);

    std::string safe = sanitize_for_modified_utf8(out ? out : "");
    return env->NewStringUTF(safe.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_WhisperBridge_release(JNIEnv*, jobject) {
whisper_stt_release();
}
