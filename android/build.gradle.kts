plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val slipgateJvmToolchain = property("slipgate.jvmToolchain") as String
val slipgateAndroidCompileSdk = property("slipgate.androidCompileSdk") as String
val slipgateAndroidMinSdk = property("slipgate.androidMinSdk") as String
val slipgateAndroidTargetSdk = property("slipgate.androidTargetSdk") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(slipgateJvmToolchain))
    }
}

android {
    namespace = "com.jacksonfdam.slipgate"
    compileSdk = slipgateAndroidCompileSdk.toInt()

    defaultConfig {
        applicationId = "com.jacksonfdam.slipgate"
        minSdk = slipgateAndroidMinSdk.toInt()
        targetSdk = slipgateAndroidTargetSdk.toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":games:mars"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
