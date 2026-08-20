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

// Compose resources are not packaged for the Android target of the Android KMP library plugin, so
// the painted backdrops are also staged as plain java resources, which an AAR does carry. One set
// of committed files, two ways of reaching them — the same arrangement games/mars uses for its
// engine module.
val stageBackdrops by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/commonMain/composeResources/files")) { into("files") }
    into(layout.buildDirectory.dir("backdropResources"))
}

compose.resources {
    // Named explicitly so the accessor lands where the shell's own code can see it.
    packageOfResClass = "com.jacksonfdam.slipgate.ui.generated.resources"
    publicResClass = false
}

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
        // The launcher's state model is plain Kotlin, so it is tested where a test needs no device.
        withHostTest {}
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain { resources.srcDir(stageBackdrops) }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }

        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(project(":host:audio"))
            api(project(":host:graphics:core"))
            implementation(project(":host:gamedata"))
            implementation(project(":host:graphics:backend:classic"))
            implementation(project(":host:graphics:backend:skia"))
            api(project(":host:controls"))
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
