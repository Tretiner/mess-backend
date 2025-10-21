package org.mess.backend.core

import kotlinx.serialization.Serializable

// Ответ с ошибкой (отправляемый * -> Gateway при неудаче)
@Serializable
data class NatsErrorResponse(
    val error: String
)