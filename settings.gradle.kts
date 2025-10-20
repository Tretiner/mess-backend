plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "MessBackend"
include(":proto")
include(":services:auth")
include(":services:user")
include(":services:chat")