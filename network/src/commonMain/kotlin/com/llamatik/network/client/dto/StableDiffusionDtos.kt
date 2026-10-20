package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class StableDiffusionInitRequest(val modelPath: String, val threads: Int = -1)

@Serializable
data class Txt2ImgRequest(
    val prompt: String,
    val negativePrompt: String? = null,
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val seed: Long = -1L,
)

@Serializable
data class Img2ImgRequest(
    val initImageBase64: String,
    val initImageW: Int,
    val initImageH: Int,
    val prompt: String,
    val negativePrompt: String? = null,
    val width: Int = 512,
    val height: Int = 512,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val strength: Float = 0.75f,
    val seed: Long = -1L,
)

@Serializable
data class ImageResponse(val imageBase64: String)
