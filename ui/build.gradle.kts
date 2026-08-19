import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String

kotlin {
    explicitApi()
    // iOS and web both render through Skia, so their framebuffer upload is written once.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withIos()
                withWasmJs()
            }
        }
    }

    jvmToolchain(slipgateJvmToolchain.toInt())

    android {
        namespace = "com.jacksonfdam.slipgate.ui"
        compileSdk = slipgateAndroidCompileSdk.toInt()
        minSdk = slipgateAndroidMinSdk.toInt()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(project(":host:audio"))
            api(project(":host:graphics:core"))
            implementation(project(":host:graphics:backend:classic"))
            implementation(project(":host:graphics:backend:skia"))
            api(project(":host:controls"))
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
