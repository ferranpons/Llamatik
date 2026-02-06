#include <jni.h>
#include <string>
#include "whisper_stt.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_llamatik_library_platform_WhisperBridge_initModel(JNIEnv* env, jobject, jstring path) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int ok = whisper_stt_init(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_llamatik_library_platform_WhisperBridge_transcribeWav(JNIEnv* env, jobject, jstring wavPath, jstring lang) {
    const char* cwav = env->GetStringUTFChars(wavPath, nullptr);
    const char* clang = lang ? env->GetStringUTFChars(lang, nullptr) : nullptr;

    const char* out = whisper_stt_transcribe_wav(cwav, clang);

    if (lang) env->ReleaseStringUTFChars(lang, clang);
    env->ReleaseStringUTFChars(wavPath, cwav);

    return env->NewStringUTF(out ? out : "");
}

extern "C" JNIEXPORT void JNICALL
Java_com_llamatik_library_platform_WhisperBridge_release(JNIEnv*, jobject) {
whisper_stt_release();
}
