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

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = false

            // We used to re-export :library, but Kotlin/Native can't export a cinterop klib.
            // The :library framework is static and force-loads its merged archive already,
            // so symbols are included transitively without export().
            // export(project(":library"))  <-- keep removed

            // Give it a bundle id to keep Xcode happy
            freeCompilerArgs += "-Xbinary=bundleId=com.llamatik.shared"
            freeCompilerArgs += "-Xbinary=ios_version_min=16.6"
            freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios=16.6"

            // NOTE:
            // We deliberately do NOT add custom linkerOpts here.
            // The native bits (llama/ggml) are linked & force-loaded in :library already.
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
            implementation(libs.androidx.compose.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.routine.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.activity.ktx)
            implementation(libs.ktor.client.android)
            implementation(libs.xmlutil.serialization.android)
            implementation(libs.bouquet)
            implementation(libs.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.stately.common)
            implementation(compose.components.resources)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.common)
        }

        commonTest.dependencies {
            implementation(libs.junit)
            implementation(libs.koin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
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
        // If you use NDK here, configure ABI filters accordingly.
        // ndk { abiFilters.add("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    // If you’re not building JNI in :shared, keep these disabled.
    // sourceSets["main"].jniLibs.srcDirs("src/commonMain/jniLibs")
    // externalNativeBuild { cmake { path = file("src/commonMain/cpp/CMakeLists.txt") } }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.llamatik.app.resources"
    generateResClass = always
}