package com.llamatik

import com.llamatik.plugins.configureAdministration
import com.llamatik.plugins.configureHTTP
import com.llamatik.plugins.configureMonitoring
import com.llamatik.plugins.configureSerialization
import com.llamatik.plugins.configureTemplating
import io.ktor.server.engine.embeddedServer
import io.ktor.server.tomcat.jakarta.Tomcat

fun main() {
    embeddedServer(Tomcat, port = 8080, host = "0.0.0.0") {
        configureSessions()
        configureGeneralRouting()
        configureAuthentication()
        configureHTTP()
        configureMonitoring()
        configureTemplating()
        configureSerialization()
        configureAdministration()
    }.start(wait = true)
}

const val API_VERSION = "/v1"
