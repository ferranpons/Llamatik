package com.dcshub.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm.HMAC512
import com.dcshub.models.DatabaseUser
import java.util.Date

const val JWT_CONFIGURATION = "MyProjectNameJWT"

class JwtService {

    private val issuer = "MyProjectNameServer"
    private val jwtSecret = System.getenv("JWT_SECRET")
    private val algorithm = HMAC512(jwtSecret)

    val verifier: JWTVerifier = JWT
        .require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(databaseUser: DatabaseUser): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("id", databaseUser.userId)
        .withExpiresAt(expiresAt())
        .sign(algorithm)

    @Suppress("MagicNumber")
    private fun expiresAt() = Date(System.currentTimeMillis() + 3_600_000 * 24) // 24 hours
}
