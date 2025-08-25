import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.korge)
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

    // One framework per iOS target (do NOT export :library – it contains a cinterop)
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = false

            // We used to re-export :library, but Kotlin/Native can't export a cinterop klib.
            // The :library framework is static and force-loads its merged archive already,
            // so symbols are included transitively without export().
            // export(project(":library"))  <-- REMOVED to fix "can't be exported" error

            // Give it a bundle id to keep Xcode happy
            freeCompilerArgs += "-Xbinary=bundleId=com.llamatik.shared"

            // NOTE:
            // We deliberately do NOT add custom linkerOpts here.
            // The native bits (llama/ggml) are linked & force-loaded in :library already.
            // The iOS Deployment Target should be set in the Xcode app that embeds this framework.
            // If you still hit a min-version mismatch, set it in Xcode, or (less ideal)
            // pass ld-style flags here: "-ios_version_min", "15.6" (device) or
            // "-ios_simulator_version_min", "15.6" (sim), not the clang-style -mios* flags.
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
            // IMPORTANT: depend on :library
            api(project(":library"))

            implementation(libs.skiko)
            implementation(compose.ui)
            implementation(compose.foundation)
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

            // Kamel for image loading
            implementation(libs.kamel)

            // Voyager for Navigation
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.bottom.sheet.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)

            // Multiplatform Settings
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.serialization)

            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.test)

            // Logging
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

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.xmlutil.serialization.android)
            implementation(libs.bouquet)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.stately.common)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
        }

        commonTest.dependencies {
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

android {
    namespace = "com.llamatik.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        // If you use NDK, configure it here
        // ndk { abiFilters.add("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    // If you’re not building JNI here, keep this commented
    // sourceSets["main"].jniLibs.srcDirs("src/commonMain/jniLibs")
    // externalNativeBuild {
    //     cmake { path = file("src/commonMain/cpp/CMakeLists.txt") }
    // }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.llamatik.app.resources"
    generateResClass = always
}