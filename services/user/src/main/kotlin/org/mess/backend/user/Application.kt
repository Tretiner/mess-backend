package org.mess.backend.user

import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.core.encodeToBytes
import org.mess.backend.user.db.initDatabase
import org.mess.backend.user.models.*
import org.mess.backend.user.services.ProfileService
import org.slf4j.LoggerFactory
import java.util.*

val json = DefaultJson
val log = LoggerFactory.getLogger("UserApplication")
val serviceScope = CoroutineScope(Dispatchers.IO) // Scope для suspend-функций

fun main() {
    val config = Config.load()
    log.info("Config loaded. Connecting to NATS at ${config.natsUrl}")

    val profileService = ProfileService()

    try { initDatabase(config.db); log.info("Database connected.") }
    catch (e: Exception) { log.error("DB connection failed", e); throw e }

    val nats: Connection = try { Nats.connect(config.natsUrl) }
    catch (e: Exception) { log.error("NATS connection failed", e); throw e }
    log.info("NATS connected. Awaiting requests...")

    // --- Слушатель 1: user.created Event ---
    val eventDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине
            try {
                val event = json.decodeFromBytes<UserCreatedEvent>(msg.data)
                log.info("Received user.created event for username: {}", event.username)
                profileService.createProfile(UUID.fromString(event.userId), event.username)
            } catch (e: Exception) { log.error("Error processing user.created event: ${e.message}", e) }
        }
    }
    eventDispatcher.subscribe("user.created")

    // --- Слушатель 2: user.profile.get Request ---
    val getDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине
            val responseBytes = try {
                val request = json.decodeFromBytes<NatsProfileGetRequest>(msg.data)
                log.debug("Received user.profile.get request for ID: {}", request.userId)
                val profile = profileService.getProfile(UUID.fromString(request.userId))

                if (profile != null) {
                    json.encodeToBytes(profile)
                } else {
                    log.warn("Profile not found for user ID: {}", request.userId)
                    json.encodeToBytes(NatsErrorResponse("Profile not found for user ${request.userId}"))
                }
            } catch (e: Exception) {
                log.error("Error processing user.profile.get: ${e.message}", e)
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
            }
            nats.publish(msg.replyTo, responseBytes)
        }
    }
    getDispatcher.subscribe("user.profile.get")

    // --- НОВЫЙ СЛУШАТЕЛЬ 3: user.profiles.get.batch Request ---
    val batchGetDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch {
            val responseBytes = try {
                val request = json.decodeFromBytes<NatsProfilesGetBatchRequest>(msg.data)
                log.debug("Received user.profiles.get.batch request for {} IDs", request.userIds.size)

                val uuids = request.userIds.map { UUID.fromString(it) }
                val profiles = profileService.getProfilesBatch(uuids)
                val response = NatsProfilesGetBatchResponse(profiles)

                json.encodeToBytes(response)
            } catch (e: Exception) {
                log.error("Error processing user.profiles.get.batch: ${e.message}", e)
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
            }
            nats.publish(msg.replyTo, responseBytes)
        }
    }
    batchGetDispatcher.subscribe("user.profiles.get.batch")


    // --- Слушатель 4: user.profile.update Request ---
    val updateDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине
            val responseBytes = try {
                val request = json.decodeFromBytes<NatsProfileUpdateRequest>(msg.data)
                log.debug("Received user.profile.update request for ID: {}", request.userId)

                val updatedProfile = profileService.updateProfile(
                    userId = UUID.fromString(request.userId),
                    newUsername = request.newUsername,
                    newAvatarUrl = request.newAvatarUrl,
                    newEmail = request.newEmail,
                    newFullName = request.newFullName
                )

                json.encodeToBytes(updatedProfile)

            } catch (e: Exception) { // Ловим ошибки валидации (напр. "Username taken")
                log.error("Error processing user.profile.update: ${e.message}", e)
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Update failed"))
            }
            nats.publish(msg.replyTo, responseBytes)
        }
    }
    updateDispatcher.subscribe("user.profile.update")

    // --- Слушатель 5: user.search Request ---
    val searchDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине
            val responseBytes = try {
                val request = json.decodeFromBytes<NatsSearchRequest>(msg.data)
                log.debug("Received user.search request with query: '{}'", request.query)
                val response = profileService.searchProfiles(request.query)
                json.encodeToBytes(response)
            } catch (e: Exception) {
                log.error("Error processing user.search: ${e.message}", e)
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Unknown error"))
            }
            nats.publish(msg.replyTo, responseBytes)
        }
    }
    searchDispatcher.subscribe("user.search")

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down NATS connection...")
        nats.close()
    })
}