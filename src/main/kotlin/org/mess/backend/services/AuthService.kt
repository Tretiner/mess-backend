package org.mess.backend.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mess.backend.JwtConfig
import org.mess.backend.models.AuthResponse
import java.util.*

class AuthService(private val config: JwtConfig) {

    fun createToken(userId: String): AuthResponse {
        val token = JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + config.validityMs))
            .sign(Algorithm.HMAC256(config.secret))
        
        return AuthResponse(token)
    }
}