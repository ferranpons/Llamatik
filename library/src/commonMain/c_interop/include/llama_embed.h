#ifndef LLAMA_EMBED_H
#define LLAMA_EMBED_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jfloatArray
JNICALL Java_com_llamatik_library_platform_LlamaBridge_embedText(
        JNIEnv *env, jobject thiz, jstring text);

#ifdef __cplusplus
}
#endif

#endif // LLAMA_EMBED_H
