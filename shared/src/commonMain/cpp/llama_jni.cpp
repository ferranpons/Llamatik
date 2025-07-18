#include <jni.h>
#include "llama.h"
#include "../c_interop/include/llama_jni.h"
#include <libgen.h>
#include <android/log.h>


extern "C"
JNIEXPORT jboolean

JNICALL
Java_com_dcshub_app_platform_LlamaBridge_initModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    bool success = llama_embed_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return success;
}

extern "C"
JNIEXPORT jfloatArray

JNICALL
Java_com_dcshub_app_platform_LlamaBridge_embed(JNIEnv *env, jobject, jstring input) {
    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    float *vec = llama_embed(inputStr);
    __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", "Vector: %p", vec);
    if (!vec) return nullptr;

    __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", "Input: %s", inputStr);
    env->ReleaseStringUTFChars(input, inputStr);


    int size = llama_embedding_size();
    __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", "Size: %d", size);

    jfloatArray result = env->NewFloatArray(size);
    __android_log_print(ANDROID_LOG_DEBUG, "LlamaBridge", "Result: %p", result);
    if (!result) return nullptr;

    env->SetFloatArrayRegion(result, 0, size, vec);
    llama_free_embedding(vec);
    return result;
}

extern "C"
JNIEXPORT jboolean

JNICALL
Java_com_dcshub_app_platform_LlamaBridge_initGenerateModel(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    bool success = llama_generate_init(path);
    env->ReleaseStringUTFChars(modelPath, path);
    return success;
}

extern "C"
JNIEXPORT jstring

JNICALL
Java_com_dcshub_app_platform_LlamaBridge_generate(JNIEnv *env, jobject, jstring input) {
    const char *prompt = env->GetStringUTFChars(input, nullptr);
    char *response = llama_generate(prompt);
    env->ReleaseStringUTFChars(input, prompt);
    if (!response) return nullptr;

    jstring result = env->NewStringUTF(response);
    llama_generate_free();
    return result;
}
