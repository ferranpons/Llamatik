package com.llamatik.app.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual object ServiceClient {
    actual val httpClient = HttpClient(Js) {
        install(ContentNegotiation) {
            json()
        }
    }
}
