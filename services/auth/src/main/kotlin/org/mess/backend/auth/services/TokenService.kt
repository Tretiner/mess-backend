package org.mess.backend.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mess.backend.auth.JwtConfig
import java.util.*

class TokenService(private val config: JwtConfig) {

    private val algorithm = Algorithm.HMAC256(config.secret)
    private val verifier = JWT.require(algorithm)
        .withAudience(config.audience)
        .withIssuer(config.issuer)
        .build()

    fun createToken(userId: UUID, username: String): String {
        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", userId.toString())
            .withClaim("username", username) // Полезно иметь
            .withExpiresAt(Date(System.currentTimeMillis() + config.validityMs))
            .sign(algorithm)
    }
}