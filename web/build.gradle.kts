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
            implementation(project(":host:graphics:backend:webgpu"))
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.ui)
            implementation(libs.koin.core)
        }
    }
}
