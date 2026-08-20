plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String

// The engine module is a resource of the gate that runs it, so a build that ships the gate cannot
// forget to ship the engine.
//
// There is no wasmJs target yet: the Chasm-backed driver does not run there, and the browser's own
// engine needs a driver of its own before this gate can reach the web.
val suppliedIwad = providers.gradleProperty("slipgate.iwad")

// Deathkings, which is an add-on rather than a game: -Pslipgate.addon=/path/to/hexdd.wad
val suppliedAddOn = providers.gradleProperty("slipgate.addon")

tasks.withType<Test>().configureEach {
    environment("SLIPGATE_IWAD", suppliedIwad.getOrElse(""))
    environment("SLIPGATE_ADDON", suppliedAddOn.getOrElse(""))
}

// Compose resources are not packaged for the Android target of the Android KMP library plugin, so
// the engine module is also staged as a plain java resource, which an AAR does carry. One committed
// file, two ways of reaching it: the alternative is a second copy of a binary in the repository.
val stageEngineModule by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/commonMain/composeResources/files")) { into("files") }
    into(layout.buildDirectory.dir("engineResources"))
}

compose.resources {
    // Named explicitly so the accessor lands where the gate's own code can see it.
    packageOfResClass = "com.jacksonfdam.slipgate.games.korax.generated.resources"
    publicResClass = false
}

kotlin {
    explicitApi()
    jvmToolchain(slipgateJvmToolchain.toInt())

    android {
        namespace = "com.jacksonfdam.slipgate.games.korax"
        compileSdk = slipgateAndroidCompileSdk.toInt()
        minSdk = slipgateAndroidMinSdk.toInt()
    }

    // The JVM target exists so the gate can be booted and stepped headless, which is what the demo
    // determinism harness needs and where the module is exercised without a device.
    jvm()

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        androidMain { resources.srcDir(stageEngineModule) }

        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(project(":host:backend:wasm"))
            implementation(compose.runtime)
            implementation(compose.components.resources)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.chasm)
        }
    }
}
