import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String

kotlin {
    jvmToolchain(slipgateJvmToolchain.toInt())

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("slipgate")
        browser {
            commonWebpackConfig {
                outputFileName = "slipgate.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":ui"))
            implementation(project(":games:mars"))
            implementation(compose.ui)
            implementation(libs.koin.core)
        }
    }
}
