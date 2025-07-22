package com.llamatik

import com.llamatik.auth.UserSession
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.directorySessionStorage
import io.ktor.server.sessions.header
import java.io.File

fun Application.configureSessions() {
    install(Sessions) {
        header<UserSession>("user_session", directorySessionStorage(File("build/.sessions")))
    }
}
