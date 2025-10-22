// FILE: services/user/src/main/kotlin/org/mess/backend/user/Application.kt
package org.mess.backend.user

import io.nats.client.Connection
import io.nats.client.Nats
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.core.encodeToBytes
import org.mess.backend.user.db.DatabaseConfig
import org.mess.backend.user.db.initDatabase
import org.mess.backend.user.models.*
import org.mess.backend.user.services.ProfileService
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.*

val json = DefaultJson
val log = LoggerFactory.getLogger("UserApplication")

fun main() {
    val config = Config.load()
    log.info("Config loaded. Connecting to NATS at ${config.natsUrl}")

    val profileService = ProfileService()

    try { initDatabase(config.db); log.info("Database connected.") }
    catch (e: Exception) { log.error("DB connection failed", e); throw e }

    val nats: Connection = try { Nats.connect(config.natsUrl) }
    catch (e: Exception) { log.error("NATS connection failed", e); throw e }
    log.info("NATS connected. Awaiting requests...")

    // --- Listener 1: user.created Event ---
    val eventDispatcher = nats.createDispatcher { msg ->
        try {
            val event = json.decodeFromBytes<UserCreatedEvent>(msg.data)
            log.info("Received user.created event for username: {}", event.username)
            profileService.createProfile(UUID.fromString(event.userId), event.username) // Use username
        } catch (e: Exception) { log.error("Error processing user.created event: ${e.message}", e) }
    }
    eventDispatcher.subscribe("user.created")

    // --- Listener 2: user.profile.get Request ---
    val getDispatcher = nats.createDispatcher { msg ->
        var responseBytes: ByteArray? = null
        try {
            val request = json.decodeFromBytes<NatsProfileGetRequest>(msg.data)
            log.debug("Received user.profile.get request for ID: {}", request.userId)
            val profile = profileService.getProfile(UUID.fromString(request.userId))

            responseBytes = if (profile != null) {
                json.encodeToBytes(profile)
            } else {
                log.warn("Profile not found for user ID: {}", request.userId)
                json.encodeToBytes(NatsErrorResponse("Profile not found for user ${request.userId}"))
            }
        } catch (e: Exception) {
            log.error("Error processing user.profile.get: ${e.message}", e)
            responseBytes = json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        responseBytes?.let { nats.publish(msg.replyTo, it) }
    }
    getDispatcher.subscribe("user.profile.get")

    // --- Listener 3: user.profile.update Request ---
    val updateDispatcher = nats.createDispatcher { msg ->
        var responseBytes: ByteArray? = null
        try {
            val request = json.decodeFromBytes<NatsProfileUpdateRequest>(msg.data)
            log.debug("Received user.profile.update request for ID: {}", request.userId)

            // Call updated service method
            val updatedProfile = profileService.updateProfile(
                userId = UUID.fromString(request.userId),
                newUsername = request.newUsername, // <-- RENAMED from newNickname
                newAvatarUrl = request.newAvatarUrl,
                newEmail = request.newEmail,
                newFullName = request.newFullName
            )
            responseBytes = if (updatedProfile != null) {
                json.encodeToBytes(updatedProfile)
            } else {
                // updateProfile might return null on validation failure or if user not found
                log.warn("Profile update failed or user not found for ID: {}", request.userId)
                json.encodeToBytes(NatsErrorResponse("Profile update failed (e.g., username/email taken or user not found)"))
            }
        } catch (e: Exception) {
            log.error("Error processing user.profile.update: ${e.message}", e)
            responseBytes = json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        responseBytes?.let { nats.publish(msg.replyTo, it) }
    }
    updateDispatcher.subscribe("user.profile.update")

    // --- Listener 4: user.search Request ---
    val searchDispatcher = nats.createDispatcher { msg ->
        var responseBytes: ByteArray? = null
        try {
            val request = json.decodeFromBytes<NatsSearchRequest>(msg.data)
            log.debug("Received user.search request with query: '{}'", request.query)
            val response = profileService.searchProfiles(request.query) // Uses updated search logic
            responseBytes = json.encodeToBytes(response)
        } catch (e: Exception) {
            log.error("Error processing user.search: ${e.message}", e)
            responseBytes = json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        responseBytes?.let { nats.publish(msg.replyTo, it) }
    }
    searchDispatcher.subscribe("user.search")

    // ... (Shutdown hook)
}

// Config.kt and db/Database.kt remain the same