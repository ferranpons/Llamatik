#pragma once

// Registers the best ggml CPU backend variant this device supports. Only does work in Android
// builds configured with LLAMATIK_ANDROID_CPU_VARIANTS; a no-op everywhere else.
void llamatik_load_android_cpu_backend();
