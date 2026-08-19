import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String

// Lets a real IWAD be pointed at the inspector without one ever living in the repository.
val suppliedIwad = providers.gradleProperty("slipgate.iwad")

tasks.withType<Test>().configureEach {
    environment("SLIPGATE_IWAD", suppliedIwad.getOrElse(""))
}

kotlin {
    explicitApi()
    // Android and the JVM both store files through java.io, so that half is written once. The group
    // is declared through the template rather than with a dependsOn edge, because a manual edge
    // switches the default template off entirely and silently drops iosMain from the build.
    applyDefaultHierarchyTemplate {
        common {
            group("javaFile") {
                withJvm()
                withCompilations { it.target.name == "android" }
            }
        }
    }

    jvmToolchain(slipgateJvmToolchain.toInt())

    android {
        namespace = "com.jacksonfdam.slipgate.host.gamedata"
        compileSdk = slipgateAndroidCompileSdk.toInt()
        minSdk = slipgateAndroidMinSdk.toInt()
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(libs.kotlinx.coroutines.core)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
