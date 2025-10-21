val ktorVersion = "2.3.11"
val exposedVersion = "0.49.0"
val logbackVersion = "1.4.14"
val natsVersion = "2.17.3"
val kotlinxDatetimeVersion = "0.6.0"
val kotlinVersion = "1.9.23"
val serializationVersion = "1.6.3"
val postgresDriverVersion = "42.7.3"
val slf4jVersion = "2.0.13" // Для логгирования NATS

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.mess.backend"
version = "1.0.0"

// ИЗМЕНЕНИЕ: Указываем главный класс этого сервиса
application {
    mainClass.set("org.mess.backend.chat.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))

    // --- Kotlin & Ktor (для чтения конфига) ---
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // --- NATS ---
    implementation("io.nats:jnats:$natsVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")

    // --- Database (Exposed + Postgres) ---
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresDriverVersion")

    // --- Утилиты ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
}