package org.mess.backend.core

import kotlinx.serialization.Serializable

@Serializable
data class NatsErrorResponse(
    val error: String
)