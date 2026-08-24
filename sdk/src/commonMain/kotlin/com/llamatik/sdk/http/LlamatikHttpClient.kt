package com.llamatik.sdk.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

class LlamatikHttpClient(
    val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }
)
