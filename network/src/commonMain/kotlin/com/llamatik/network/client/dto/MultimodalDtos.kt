package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class MultimodalInitRequest(val modelPath: String, val mmprojPath: String)

@Serializable
data class AnalyzeImageRequest(val imageBase64: String, val prompt: String)
