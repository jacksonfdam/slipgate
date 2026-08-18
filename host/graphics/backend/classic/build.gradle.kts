import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String

kotlin {
    explicitApi()
    jvmToolchain(slipgateJvmToolchain.toInt())

    android {
        namespace = "com.jacksonfdam.slipgate.host.graphics.backend.classic"
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
            api(project(":host:graphics:core"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
