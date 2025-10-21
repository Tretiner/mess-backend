pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "MessBackend"

include(
    ":services:auth",
    ":services:user",
    ":services:chat",

    ":services:gateway",
)
include("core")