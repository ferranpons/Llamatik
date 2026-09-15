#include "ggml.h"

// Fallback for ggml_mul_mat_i8_tensorwise (SD-specific INT8 tensorwise matmul).
// Delegates to ggml_mul_mat, ignoring INT8-specific arguments.
// Standard FP16/FP32 SD models work correctly; INT8-quantized models do not.
struct ggml_tensor * ggml_mul_mat_i8_tensorwise(
        struct ggml_context * ctx,
        struct ggml_tensor  * weight,
        struct ggml_tensor  * input,
        struct ggml_tensor  * weight_scale,
        struct ggml_tensor  * bias,
        int                   convrot_group_size)
{
    (void)weight_scale;
    (void)bias;
    (void)convrot_group_size;
    return ggml_mul_mat(ctx, weight, input);
}

// Fallback for ggml_quantize_i8_convrot (packs I8 activations with convrot).
// Returns a contiguous view; convrot quantisation is a no-op.
struct ggml_tensor * ggml_quantize_i8_convrot(
        struct ggml_context * ctx,
        struct ggml_tensor  * a,
        int                   group_size)
{
    (void)group_size;
    return ggml_cont(ctx, a);
}
