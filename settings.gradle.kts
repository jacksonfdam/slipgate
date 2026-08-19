pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "slipgate"

include(":host:audio")
include(":host:backend:wasm")
include(":host:controls")
include(":host:graphics:backend:classic")
include(":host:graphics:backend:skia")
include(":host:graphics:core")
include(":host:runtime")
include(":games:mars")
include(":ui")
include(":android")
include(":ios")
include(":web")
