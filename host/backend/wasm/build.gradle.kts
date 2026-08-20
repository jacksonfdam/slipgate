import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String

// The boot test needs an IWAD the developer supplies, and no game data may live in this
// repository. It arrives as a Gradle property rather than an environment variable: the daemon holds
// the environment it was started with, so an exported variable reaches neither the build nor the
// forked test JVM, and the test would skip while appearing to pass.
//
//     ./gradlew :host:backend:wasm:jvmTest -Pslipgate.iwad=/path/to/freedoom1.wad
val suppliedIwad = providers.gradleProperty("slipgate.iwad")

tasks.withType<Test>().configureEach {
    environment("SLIPGATE_IWAD", suppliedIwad.getOrElse(""))
}

// Chasm publishes JVM and native artifacts, not wasmJs — and a browser already has a WebAssembly
// engine of its own, which is what addendum 01's resolution table says the web should use. So both
// drivers live here behind one interface: Chasm under chasmMain, the browser's own engine under
// wasmJsMain, and nothing above this module knows which one it got.
kotlin {
    explicitApi()
    jvmToolchain(slipgateJvmToolchain.toInt())

    android {
        namespace = "com.jacksonfdam.slipgate.host.backend.wasm"
        compileSdk = slipgateAndroidCompileSdk.toInt()
        minSdk = slipgateAndroidMinSdk.toInt()
    }

    // The JVM target is not shipped: it exists for headless verification, where a module can be
    // instantiated and stepped on a build machine.
    jvm()

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":host:runtime"))
        }

        // Everything that is not the web runs the module through Chasm, so that driver is written
        // once and the three targets that can use it share the source set. Declared by hand rather
        // than through the hierarchy template, which has no name for this plugin's android target.
        val chasmMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.chasm)
            }
        }
        // Attached target by target: the intermediate iOS source set is not what the compilations
        // read from, and an actual the compiler cannot see is an actual that does not exist.
        androidMain.get().dependsOn(chasmMain)
        jvmMain.get().dependsOn(chasmMain)
        iosArm64Main.get().dependsOn(chasmMain)
        iosSimulatorArm64Main.get().dependsOn(chasmMain)

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.register("printIwadWiring") {
    val supplied = suppliedIwad.getOrElse("<none>")
    val testTasks = tasks.withType(Test::class.java).names.joinToString(", ")
    doLast {
        println("slipgate.iwad = $supplied")
        println("test tasks = $testTasks")
    }
}
