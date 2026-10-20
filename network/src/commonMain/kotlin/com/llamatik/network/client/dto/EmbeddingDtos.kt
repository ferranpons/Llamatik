package com.llamatik.network.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmbedRequest(val input: String)

@Serializable
data class EmbedResponse(val embedding: List<Float>)
