package org.mess.backend.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mess.backend.auth.JwtConfig
import java.util.*

/**
 * Сервис для создания долгоживущего JWT Access Token.
 */
class TokenService(private val config: JwtConfig) {

    private val algorithm = Algorithm.HMAC256(config.secret)
    // Устанавливаем время жизни в 1 год (~31.5 миллиардов мс)
    private val longLivedValidityMs = 31536000000L

    /** Создает долгоживущий JWT access token (на 1 год). */
    fun createAccessToken(userId: UUID, username: String): String {
        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withClaim("userId", userId.toString())
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + longLivedValidityMs))
            .sign(algorithm)
    }
}