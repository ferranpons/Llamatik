package com.dcshub.app.platform

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

object ServiceClient {
    val httpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }
}
