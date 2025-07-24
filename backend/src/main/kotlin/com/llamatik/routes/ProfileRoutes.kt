package com.llamatik.routes

import com.llamatik.API_VERSION
import com.llamatik.auth.JWT_CONFIGURATION
import com.llamatik.auth.UserSession
import com.llamatik.repository.profile.ProfileRepository
import com.llamatik.repository.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.patch
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

const val PROFILE = "$API_VERSION/profile"
const val PROFILE_CREATE = "$PROFILE/create"
const val PROFILE_UPDATE = "$PROFILE/update"

@Resource(PROFILE)
class ProfileRoute

@Resource(PROFILE_CREATE)
class ProfileCreateRoute

@Resource(PROFILE_UPDATE)
class ProfileUpdateRoute

@Suppress("LongMethod", "TooGenericExceptionCaught", "CyclomaticComplexMethod")
fun Route.profiles(
    profileRepository: ProfileRepository,
    userRepository: UserRepository
) {
    authenticate(JWT_CONFIGURATION) {
        post<ProfileCreateRoute> {
            val profileParameters = call.receive<Parameters>()
            val name = profileParameters["name"] ?: ""
            val nickname = profileParameters["nickname"] ?: ""
            val description = profileParameters["description"] ?: ""
            val image = profileParameters["image"] ?: ""
            val location = null
            val preferredLanguage = profileParameters["preferredLanguage"] ?: ""
            val serversList = null
            val rank = profileParameters["rank"]?.toInt()
            val country = profileParameters["country"] ?: ""
            val squadron = profileParameters["squadron"] ?: ""
            val squadronPatch = profileParameters["squadronPatch"] ?: ""

            val medals = emptyList<String>()

            val user = call.sessions.get<UserSession>()?.let {
                userRepository.findUser(it.userId)
            }
            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@post
            }

            try {
                val profile = profileRepository.addProfile(
                    id = user.userId,
                    name = name,
                    nickname = nickname,
                    description = description,
                    image = image,
                    preferredLanguage = preferredLanguage,
                    serversList = serversList,
                    rank = rank,
                    country = country,
                    squadron = squadron,
                    squadronPatch = squadronPatch,
                    medals = medals
                )
                /*
                profile?.id?.let {
                    call.respond(HttpStatusCode.OK, profile)
                }*/
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to add Profile", e)
                call.respond(HttpStatusCode.BadRequest, "Problems Adding Profile")
            }
        }

        get<ProfileRoute> {
            val user = call.sessions.get<UserSession>()?.let { userRepository.findUser(it.userId) }
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, "Problems retrieving User")
                return@get
            }
            try {
                val profile = profileRepository.getProfile(user.userId)
                if (profile != null) {
                    call.respond(profile)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Profile not found")
                    return@get
                }
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to get Profile", e)
                call.respond(HttpStatusCode.BadRequest, "Problems getting Profile")
            }
        }

        patch<ProfileUpdateRoute> {
            val profileParameters = call.receive<Parameters>()
            val name = profileParameters["name"] ?: ""
            val nickname = profileParameters["nickname"] ?: ""
            val description = profileParameters["description"] ?: ""
            val image = profileParameters["image"] ?: ""
            val location = null
            val preferredLanguage = profileParameters["preferredLanguage"] ?: ""
            val serversList = null
            val rank = profileParameters["rank"]?.toInt()
            val country = profileParameters["country"] ?: ""
            val squadron = profileParameters["squadron"] ?: ""
            val squadronPatch = profileParameters["squadronPatch"] ?: ""
            val medals = emptyList<String>()

            val user = call.sessions.get<UserSession>()?.let {
                userRepository.findUser(it.userId)
            }
            if (user == null) {
                call.respond(HttpStatusCode.BadRequest, "Problems retrieving User")
                return@patch
            }

            try {
                val profile = profileRepository.updateProfile(
                    userId = user.userId,
                    name = name,
                    nickname = nickname,
                    description = description,
                    image = image,
                    location = location,
                    preferredLanguage = preferredLanguage,
                    serversList = serversList,
                    rank = rank,
                    country = country,
                    squadron = squadron,
                    squadronPatch = squadronPatch,
                    medals = medals
                )
                /*
                profile?.id?.let {
                    call.respond(HttpStatusCode.OK, profile)
                }*/
            } catch (e: Throwable) {
                this@authenticate.application.log.error("Failed to update Profile", e)
                call.respond(HttpStatusCode.BadRequest, "Problems updating Profile")
            }
        }
    }
}
