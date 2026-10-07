#pragma once
// Extends llama.cpp's ggml.h with symbols present in stable-diffusion.cpp's
// bundled ggml but absent from llama.cpp's ggml. When the KMP project forces
// stable-diffusion.cpp to use llama.cpp's ggml (via the CMake target guard),
// this shim restores the missing declarations so SD sources compile.
#include_next "ggml.h"

#ifndef GGML_TYPE_F8_E4M3
#  define GGML_TYPE_F8_E4M3 ((ggml_type)43)
#  define GGML_TYPE_F8_E5M2 ((ggml_type)44)
#endif

#ifdef __cplusplus
extern "C" {
#endif

// Declared in SD's ggml but absent from llama.cpp's ggml.
// Stub in sd_ggml_shim.cpp falls back to ggml_mul_mat; INT8-quantized SD
// models will not produce correct results.
struct ggml_tensor * ggml_mul_mat_i8_tensorwise(
        struct ggml_context * ctx,
        struct ggml_tensor  * weight,
        struct ggml_tensor  * input,
        struct ggml_tensor  * weight_scale,
        struct ggml_tensor  * bias,
        int                   convrot_group_size);

// Stub in sd_ggml_shim.cpp returns ggml_cont; convrot quantisation is a no-op.
struct ggml_tensor * ggml_quantize_i8_convrot(
        struct ggml_context * ctx,
        struct ggml_tensor  * a,
        int                   group_size);

#ifdef __cplusplus
}
#endif
