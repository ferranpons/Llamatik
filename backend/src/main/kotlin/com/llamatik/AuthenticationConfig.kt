@file:OptIn(KtorExperimentalLocationsAPI::class)

package com.llamatik

import com.llamatik.auth.JWT_CONFIGURATION
import com.llamatik.auth.JwtService
import com.llamatik.auth.hash
import com.llamatik.repository.DatabaseFactory
import com.llamatik.repository.product.ProductsRepositoryImp
import com.llamatik.repository.profile.ProfileRepositoryImpl
import com.llamatik.repository.user.UserRepositoryImp
import com.llamatik.routes.products
import com.llamatik.routes.profiles
import com.llamatik.routes.users
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.locations.KtorExperimentalLocationsAPI
import io.ktor.server.routing.routing

fun Application.configureAuthentication() {
    DatabaseFactory.init()
    val userRepository = UserRepositoryImp()
    val petRepository = ProductsRepositoryImp()
    val productsMockRepository = ProductsMockRepository()
    val profileRepository = ProfileRepositoryImpl()
    val jwtService = JwtService()
    val hashFunction = { s: String -> hash(s) }

    install(Authentication) {
        jwt(JWT_CONFIGURATION) {
            verifier(jwtService.verifier)
            realm = "MyProjectName Server"
            validate {
                val payload = it.payload
                val claim = payload.getClaim("id")
                val claimString = claim.asInt()
                val user = userRepository.findUser(claimString)
                user
            }
        }
    }

    routing {
        users(userRepository, jwtService, hashFunction)
        products(petRepository, userRepository, productsMockRepository)
        profiles(profileRepository, userRepository)
    }
}
