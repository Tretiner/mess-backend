val ktorVersion = "2.3.11"
val exposedVersion = "0.49.0" // Возвращаем Exposed
val logbackVersion = "1.4.14"
val natsVersion = "2.17.3"
val kotlinxDatetimeVersion = "0.6.0"

plugins {
    kotlin("jvm") version "1.9.23"
    application
    kotlin("plugin.serialization") version "1.9.23"
}

group = "org.mess.backend"
version = "1.0.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    // --- Ktor Core & Plugins ---
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // --- Database (Exposed + SQLite) ---
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // --- NATS Client ---
    implementation("io.nats:jnats:$natsVersion")

    // --- Utilities ---
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
}