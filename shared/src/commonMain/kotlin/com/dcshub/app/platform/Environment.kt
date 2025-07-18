package com.dcshub.app.platform

sealed class Environment(val name: String, val url: String)

sealed class ServerEnvironment(name: String, endpoint: String) : Environment(name, endpoint) {
    data object PRODUCTION : Environment("PRODUCTION", "https://multiplatformkickstarter.com")
    data object PREPRODUCTION :
        Environment("PREPRODUCTION", "https://pre.multiplatformkickstarter.com")

    // This IP represents the localhost of your computer through the emulator
    data object LOCALHOST : Environment("LOCALHOST", "https://10.0.2.2:8080")

    companion object {
        val environments = listOf(PREPRODUCTION, PRODUCTION, LOCALHOST)
    }
}
