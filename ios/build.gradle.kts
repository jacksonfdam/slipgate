plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String

kotlin {
    jvmToolchain(slipgateJvmToolchain.toInt())

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SlipgateKit"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":ui"))
            implementation(libs.koin.core)
        }
    }
}
