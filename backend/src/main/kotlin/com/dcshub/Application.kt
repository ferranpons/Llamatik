package com.dcshub

import com.dcshub.plugins.configureAdministration
import com.dcshub.plugins.configureHTTP
import com.dcshub.plugins.configureMonitoring
import com.dcshub.plugins.configureSerialization
import com.dcshub.plugins.configureTemplating
import io.ktor.server.engine.embeddedServer
import io.ktor.server.tomcat.Tomcat

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
