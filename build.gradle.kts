// --- Централизованное управление версиями ---
// Все sub-проекты будут ссылаться на эти переменные


// Применяем плагины ко всем модулям (включая :proto, :gateway и т.д.)
plugins {
    val kotlinVersion = "1.9.23"
    val protobufVersion = "0.9.4"
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