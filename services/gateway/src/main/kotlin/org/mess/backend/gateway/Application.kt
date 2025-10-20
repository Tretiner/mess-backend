package org.mess.backend.gateway

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.* // <-- Import the WebSockets plugin
import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.serialization.json.Json
import org.mess.backend.gateway.plugins.* // Import all plugin config functions
import org.mess.backend.gateway.services.NatsClient
import org.slf4j.LoggerFactory
import java.time.Duration // <-- Import for WebSocket settings if needed

val json = Json { ignoreUnknownKeys = true ; prettyPrint = true }
val log = LoggerFactory.getLogger("GatewayApplication")

fun main() {
    val config = Config.load()
    log.info("Config loaded. Connecting to NATS at ${config.natsUrl}")

    val natsConnection = try {
        Nats.connect(config.natsUrl)
    } catch (e: Exception) {
        log.error("Failed to connect to NATS at ${config.natsUrl}: ${e.message}", e)
        throw e
    }
    log.info("NATS connected successfully.")

    val natsClient = NatsClient(natsConnection, json)

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    log.info("Starting Ktor server on port $port")
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { module(config, natsClient, natsConnection) })
        .start(wait = true)
}

fun Application.module(config: AppConfig, natsClient: NatsClient, natsConnection: Connection) {
    configureSerialization()
    configureSecurity(config.jwt)
    configureStatusPages()
    configureLogging()

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15) // Example: configure ping interval
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Configure routing (which uses WebSockets)
    configureRouting(natsClient, natsConnection)

    log.info("Ktor server configured and running.")
}