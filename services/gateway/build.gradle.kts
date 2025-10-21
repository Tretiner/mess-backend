val ktorVersion = "2.3.11"
val logbackVersion = "1.4.14"
val natsVersion = "2.17.3"
val serializationVersion = "1.6.3"
val jwtVersion = "4.4.0" // auth0-jwt
val coroutinesVersion = "1.8.0" // Для kotlinx-coroutines-future

plugins {
    kotlin("jvm") // Версия из корневого build.gradle.kts
    kotlin("plugin.serialization") // Версия из корневого build.gradle.kts
    id("com.github.johnrengelman.shadow") // Версия из корневого build.gradle.kts
    application // Версия из корневого build.gradle.kts
}

application {
    // Главный класс Ktor
    mainClass.set("org.mess.backend.gateway.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    // --- Ktor Server ---
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // --- NATS Client ---
    implementation("io.nats:jnats:$natsVersion")

    // --- Утилиты ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("com.auth0:java-jwt:$jwtVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
}