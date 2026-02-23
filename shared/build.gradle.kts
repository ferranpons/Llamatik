import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.compiler)
}

kotlin {
    // Android
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // JVM desktop
    jvm()

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Web (Kotlin/Wasm)
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = false

            freeCompilerArgs += "-Xbinary=bundleId=com.llamatik.shared"
            freeCompilerArgs += "-Xbinary=ios_version_min=16.6"
            freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios=16.6"
        }
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }

        commonMain.dependencies {
            api(project(":library"))

            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.components.resources)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.animation)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io)

            implementation(libs.ktor.client)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.server.serialization.kotlinx.json)

            implementation(libs.kamel)
            implementation(libs.kamel.default)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.bottom.sheet.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.serialization)

            implementation(libs.koin.core)
            implementation(libs.kermit)

            implementation(libs.junit)

            implementation(libs.xmlutil.core)
            implementation(libs.xmlutil.serialization)

            implementation(libs.richeditor.compose)
            implementation(libs.koalaplot.core)

            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.urlencoder)
        }
/*
        // Native/desktop platforms keep using :library.
        androidMain.dependencies { api(project(":library")) }
        iosMain.dependencies { api(project(":library")) }
        jvmMain.dependencies { api(project(":library")) }

        // wasmJsMain does NOT depend on :library (native/JNI). It uses stubbed APIs.
        val wasmJsMain by getting
*/
        androidMain.dependencies {
            implementation(libs.androidx.compose.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.routine.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.activity.ktx)
            implementation(libs.ktor.client.android)
            implementation(libs.bouquet)
            implementation(libs.okhttp)

            implementation(libs.android.play.review)
            implementation(libs.android.play.review.ktx)
            implementation(libs.kotlinx.coroutines.play.services)

            implementation("com.tom-roush:pdfbox-android:2.0.27.0")
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.stately.common)
            implementation(compose.components.resources)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
            implementation("org.apache.pdfbox:pdfbox:2.0.30")
        }

        commonTest.dependencies {
            implementation(libs.junit)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }

        val wasmJsMain by getting
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.resources {
    publicResClass = true
}

android {
    namespace = "com.llamatik.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.llamatik.app.resources"
    generateResClass = always
}