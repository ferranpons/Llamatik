package com.llamatik.app.platform

import io.ktor.client.HttpClient

expect object ServiceClient {
    val httpClient: HttpClient
}
