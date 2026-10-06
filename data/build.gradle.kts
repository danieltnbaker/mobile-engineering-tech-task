import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Resolve the GoRest access token at build time from an out-of-source location, with this precedence:
//   1. -Pgorest.api.token=... (Gradle property, e.g. injected by CI)
//   2. GOREST_API_TOKEN environment variable (CI / shell)
//   3. gorest.api.token in the gitignored local.properties (local development)
//   4. "" -> the app runs read-only rather than embedding a credential in source control.
val goRestApiToken: String = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) {
        Properties().apply { local.inputStream().use { load(it) } }.getProperty("gorest.api.token")
    } else {
        null
    }
    (project.findProperty("gorest.api.token") as String?)
        ?: System.getenv("GOREST_API_TOKEN")
        ?: fromLocal
        ?: ""
}

// Generate a tiny Kotlin constant into commonMain so the token reaches both the Android app and the
// iOS framework from a single source, without ever being committed.
val tokenOutputDir = layout.buildDirectory.dir("generated/token/commonMain/kotlin")
val generateApiToken by tasks.registering {
    val outFile = tokenOutputDir.get().file("com/userhub/data/remote/BuildTokenConfig.kt").asFile
    inputs.property("token", goRestApiToken)
    outputs.file(outFile)
    doLast {
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            package com.userhub.data.remote

            // GENERATED at build time from local.properties / Gradle property / env. Do not edit or commit.
            internal object BuildTokenConfig {
                const val API_TOKEN: String = "$goRestApiToken"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(tokenOutputDir)
            dependencies {
                api(libs.ktor.client.core)
                api(libs.sqldelight.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.sqldelight.coroutines.extensions)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Ensure the token constant is generated before any Kotlin compilation that may reference it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateApiToken)
}

android {
    namespace = "com.userhub.data"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("UserDatabase") {
            packageName.set("com.userhub.data.db")
        }
    }
}
