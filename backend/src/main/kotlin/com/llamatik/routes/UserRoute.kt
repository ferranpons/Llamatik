package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.auth.JwtService
import com.llamatik.auth.UserSession
import com.llamatik.repository.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

const val USERS = "$API_VERSION/users"
const val USER_LOGIN = "$USERS/login"
const val USER_CREATE = "$USERS/create"
const val USER_LOGOUT = "$USERS/logout"

@Resource(USER_LOGIN)
class UserLoginRoute

@Resource(USER_CREATE)
class UserCreateRoute

@Resource(USER_LOGOUT)
class UserLogoutRoute

@Suppress("TooGenericExceptionCaught")
fun Route.users(
    userRepository: UserRepository,
    jwtService: JwtService,
    hashFunction: (String) -> String
) {
    post<UserCreateRoute> {
        val signupParameters = call.receive<Parameters>()
        val password = signupParameters["password"] ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            "Missing Fields"
        )
        val name = signupParameters["name"] ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            "Missing Fields"
        )
        val email = signupParameters["email"] ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            "Missing Fields"
        )
        val hash = hashFunction(password)
        try {
            val newUser = userRepository.addUser(email, name, hash)
            newUser?.userId?.let {
                call.sessions.set(UserSession(it))
                call.response.header("user_id", it.toString())
                call.respondText(
                    jwtService.generateToken(newUser),
                    status = HttpStatusCode.Created
                )
            }
        } catch (e: Throwable) {
            this@users.application.log.error("Failed to register user", e)
            call.respond(HttpStatusCode.BadRequest, "Problems creating User")
        }
    }

    post<UserLoginRoute> {
        val signInParameters = call.receive<Parameters>()
        val password = signInParameters["password"] ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            "Missing Fields"
        )
        val email = signInParameters["email"] ?: return@post call.respond(
            HttpStatusCode.Unauthorized,
            "Missing Fields"
        )
        val hash = hashFunction(password)
        try {
            val currentUser = userRepository.findUserByEmail(email)
            currentUser?.userId?.let {
                if (currentUser.passwordHash == hash) {
                    call.sessions.set(UserSession(it))
                    call.response.header("user_id", it.toString())
                    call.respondText(jwtService.generateToken(currentUser))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                }
            }
        } catch (e: Throwable) {
            this@users.application.log.error("Failed to register user", e)
            call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
        }
    }

    post<UserLogoutRoute> {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }
}
