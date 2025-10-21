package org.mess.backend.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset

val DefaultJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    isLenient = true
}

inline fun <reified T> Json.decodeFromBytes(bytes: ByteArray, charset: Charset = Charsets.UTF_8): T =
    decodeFromString(String(bytes, charset))

inline fun <reified T> Json.encodeToBytes(item: T, charset: Charset = Charsets.UTF_8): ByteArray =
    encodeToString<T>(item).toByteArray(charset)