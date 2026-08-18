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

// Shader sources live in one directory at the repository root and are embedded at build time, so
// no shader string is written by hand in Kotlin and no platform needs a resource loader to draw.
val shaderDirectory = rootProject.layout.projectDirectory.dir("shaders")
val generatedShaderDirectory = layout.buildDirectory.dir("generated/shaders/kotlin")

val generateShaderSources =
    tasks.register("generateShaderSources") {
        val sources = shaderDirectory.asFileTree.matching { include("*.sksl") }
        val outputDirectory = generatedShaderDirectory
        inputs.files(sources).withPropertyName("shaders")
        outputs.dir(outputDirectory).withPropertyName("generated")

        doLast {
            val packageDirectory =
                outputDirectory
                    .get()
                    .asFile
                    .resolve("com/jacksonfdam/slipgate/host/graphics/backend/skia")
            packageDirectory.mkdirs()
            val quotes = "\"".repeat(3)
            val entries =
                sources.files.sortedBy { it.name }.joinToString(separator = "\n") { file ->
                    val name = file.name.substringBeforeLast('.')
                    "        \"$name\" to\n            $quotes\n${file.readText().trimEnd()}\n$quotes,"
                }
            packageDirectory.resolve("ShaderSources.kt").writeText(
                buildString {
                    appendLine("package com.jacksonfdam.slipgate.host.graphics.backend.skia")
                    appendLine()
                    appendLine("// Generated from shaders/*.sksl. Edit the shader, not this file.")
                    appendLine("internal val skslSources: Map<String, String> =")
                    appendLine("    mapOf(")
                    appendLine(entries)
                    appendLine("    )")
                },
            )
        }
    }

kotlin {
    explicitApi()
    jvmToolchain(slipgateJvmToolchain.toInt())

    // iOS and web both draw through Skiko, so their runtime effect path is written once.
    applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withIos()
                withWasmJs()
            }
        }
    }

    android {
        namespace = "com.jacksonfdam.slipgate.host.graphics.backend.skia"
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
        androidMain.dependencies {
            implementation(libs.androidx.annotation)
        }

        // The golden image comparison needs both paths in one test binary, and a Skia surface can
        // be created without Compose only where Skiko runs natively.
        iosTest.dependencies {
            implementation(project(":host:graphics:backend:classic"))
            implementation(libs.kotlin.test)
        }

        commonMain {
            kotlin.srcDir(generateShaderSources)
            dependencies {
                api(project(":host:graphics:core"))
                implementation(compose.ui)
            }
        }
    }
}
