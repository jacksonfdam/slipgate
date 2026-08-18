import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

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
            implementation(compose.ui)
            implementation(libs.koin.core)
        }
    }
}
