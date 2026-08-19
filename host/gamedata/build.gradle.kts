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
        // Android and the JVM both store files through java.io, so that half is written once. The
        // dependency is declared rather than templated because the Android target's name in the
        // hierarchy template depends on which Android plugin a module uses.
        val javaFileMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(javaFileMain)
        jvmMain.get().dependsOn(javaFileMain)

        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(libs.kotlinx.coroutines.core)
        }

        javaFileMain.dependencies {
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
