package com.llamatik.network.client

data class LlamatikServerConfig(
    val host: String,
    val port: Int = 8080,
) {
    val baseUrl: String get() = "http://$host:$port/v1"
}
