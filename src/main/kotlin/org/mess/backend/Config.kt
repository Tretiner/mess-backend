package org.mess.backend

import io.ktor.server.config.*

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val validityMs: Long
) {
    constructor(config: ApplicationConfig) : this(
        secret = config.property("ktor.jwt.secret").getString(),
        issuer = config.property("ktor.jwt.issuer").getString(),
        audience = config.property("ktor.jwt.audience").getString(),
        realm = config.property("ktor.jwt.realm").getString(),
        validityMs = config.property("ktor.jwt.validity_ms").getString().toLong()
    )
}