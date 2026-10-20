#pragma once

#if defined(__ANDROID__)
// Registers the best ggml CPU backend variant this device supports.
void llamatik_load_android_cpu_backend();
#else
inline void llamatik_load_android_cpu_backend() {}
#endif
