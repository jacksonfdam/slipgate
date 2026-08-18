import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String

// Shader sources live in one directory at the repository root so the WGSL and SkSL versions of
// a program sit next to each other and drift is visible in a diff. They are embedded at build
// time rather than loaded at runtime: no shader string is ever written by hand in Kotlin, and
// no platform needs a resource loader to draw a frame.
val shaderDirectory = rootProject.layout.projectDirectory.dir("shaders")
val generatedShaderDirectory = layout.buildDirectory.dir("generated/shaders/kotlin")

val generateShaderSources =
    tasks.register("generateShaderSources") {
        val sources = shaderDirectory.asFileTree.matching { include("*.wgsl") }
        val outputDirectory = generatedShaderDirectory
        inputs.files(sources).withPropertyName("shaders")
        outputs.dir(outputDirectory).withPropertyName("generated")

        doLast {
            val packageDirectory =
                outputDirectory
                    .get()
                    .asFile
                    .resolve("com/jacksonfdam/slipgate/host/graphics/backend/webgpu")
            packageDirectory.mkdirs()
            val quotes = "\"".repeat(3)
            val entries =
                sources.files.sortedBy { it.name }.joinToString(separator = "\n") { file ->
                    val name = file.name.substringBeforeLast('.')
                    "        \"$name\" to\n            $quotes\n${file.readText().trimEnd()}\n$quotes,"
                }
            packageDirectory.resolve("ShaderSources.kt").writeText(
                buildString {
                    appendLine("package com.jacksonfdam.slipgate.host.graphics.backend.webgpu")
                    appendLine()
                    appendLine("// Generated from shaders/*.wgsl. Edit the shader, not this file.")
                    appendLine("internal val wgslSources: Map<String, String> =")
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

    // Kotlin/Wasm's JsAny interop is still marked experimental; the whole module is interop.
    compilerOptions {
        optIn.add("kotlin.js.ExperimentalWasmJsInterop")
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        wasmJsMain {
            kotlin.srcDir(generateShaderSources)
            dependencies {
                api(project(":host:graphics:core"))
                implementation(libs.kotlinx.browser)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
