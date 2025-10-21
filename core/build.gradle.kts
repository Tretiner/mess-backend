val kotlinxDatetimeVersion = "0.6.0"
val serializationVersion = "1.6.3"

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
}

group = "org.mess.backend.core"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // --- Утилиты ---
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
}