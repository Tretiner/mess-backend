val ktorVersion = "2.3.11" // (Используем для config, но можно убрать)
val exposedVersion = "0.49.0"
val logbackVersion = "1.4.14"
val natsVersion = "2.17.3"
val kotlinxDatetimeVersion = "0.6.0"
val kotlinVersion = "1.9.23"
val serializationVersion = "1.6.3"
val postgresDriverVersion = "42.7.3"
val jwtVersion = "4.4.0" // auth0-jwt
val jbcryptVersion = "0.4"
val slf4jVersion = "2.0.13" // Для логгирования NATS

plugins {
    kotlin("jvm") version "1.9.23"
    // Плагин для kotlinx.serialization
    kotlin("plugin.serialization") version "1.9.23"
    // Плагин для сборки "fat" JAR
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.mess.backend"
version = "1.0.0"

// Указываем главный класс для запуска
application {
    mainClass.set("org.mess.backend.auth.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Kotlin & Ktor (для чтения конфига) ---
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion") // Для HOCON config
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion") // или HOCON

    // --- NATS ---
    implementation("io.nats:jnats:$natsVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion") // Простой логгер для NATS

    // --- Database (Exposed + Postgres) ---
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresDriverVersion")

    // --- Утилиты ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("com.auth0:java-jwt:$jwtVersion") // Для JWT
    implementation("org.mindrot:jbcrypt:$jbcryptVersion") // Для хеширования паролей
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}