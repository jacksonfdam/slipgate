plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String

kotlin {
    explicitApi()
    jvmToolchain(slipgateJvmToolchain.toInt())

    // The JVM target exists for headless verification: the wasm modules can be instantiated and
    // stepped on a build machine, which is what the demo determinism harness will need.
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":host:runtime"))
            implementation(libs.chasm)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
