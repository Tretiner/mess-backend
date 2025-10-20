package org.mess.backend

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.mess.backend.plugins.configureDatabase
import org.mess.backend.plugins.configureRouting
import org.mess.backend.plugins.configureSecurity
import org.mess.backend.plugins.configureSerialization
import org.mess.backend.services.AuthService
import org.mess.backend.services.ChatService
import org.mess.backend.services.NatsService
import org.mess.backend.services.UserService

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    val jwtConfig = JwtConfig(environment.config)
    val natsUrl = environment.config.property("ktor.deployment.nats_url").getString()
    val dbPath = environment.config.property("ktor.deployment.db_path").getString()

    // Инициализируем сервисы
    val natsService = NatsService(natsUrl)
    val userService = UserService()
    // ИЗМЕНЕНИЕ: Внедряем userService в chatService
    val chatService = ChatService(natsService, userService)

    // Настраиваем плагины Ktor
    configureDatabase(dbPath)
    configureSerialization()
    configureSecurity(jwtConfig)
    configureRouting(userService, authService = AuthService(jwtConfig), chatService)

    // Запускаем сервисы
    natsService.connect()
    natsService.subscribeToMessages { message ->
        // Запускаем в корутине, т.к. broadcastMessageToLocalClients теперь suspend
        chatService.broadcastMessageToLocalClients(message)
    }
}