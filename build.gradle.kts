// --- Централизованное управление версиями ---
// Все sub-проекты будут ссылаться на эти переменные


// Применяем плагины ко всем модулям (включая :proto, :gateway и т.д.)
plugins {
    val kotlinVersion = "1.9.23"
    val ktorVersion = "2.3.11"
    val logbackVersion = "1.4.14"
    val exposedVersion = "0.49.0"
    val postgresDriverVersion = "42.7.3"
    val natsVersion = "2.17.3"
    val serializationVersion = "1.6.3"
    val kotlinxDatetimeVersion = "0.6.0"
    val jwtVersion = "4.4.0"
    val jbcryptVersion = "0.4"
    val slf4jVersion = "2.0.13"
    val grpcVersion = "1.64.0"
    val grpcKotlinVersion = "1.4.1"
    val protobufVersion = "0.9.4"
    val protobufPluginVersion = "3.25.3"
    val shadowVersion = "8.1.1"

    // Делаем Kotlin JVM плагин доступным для всех
    kotlin("jvm") version kotlinVersion apply false
    // Делаем Serialization плагин доступным для всех
    kotlin("plugin.serialization") version kotlinVersion apply false
    // Делаем Protobuf плагин доступным (для :proto и :gateway)
    id("com.google.protobuf") version protobufVersion apply false
    // Делаем Shadow (fat jar) плагин доступным
    id("com.github.johnrengelman.shadow") version shadowVersion apply false
}

// Общие настройки для ВСЕХ проектов (включая корневой)
allprojects {
    group = "org.mess.backend"
    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}