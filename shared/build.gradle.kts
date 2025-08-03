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
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            isStatic = false
            //export(project(":library"))
            embedBitcode("disable")
            freeCompilerArgs += "-Xbinary=bundleId=com.llamatik.shared"

            val arch = target.name
            val sdk = when {
                arch.contains("Simulator", ignoreCase = true) -> "iPhoneSimulator"
                arch.contains("x64", ignoreCase = true) -> "MacOSX"
                else -> "iPhoneOS"
            }

            val libPath = project(":library").buildDir.resolve("llama-cmake/$sdk/$arch").absolutePath
            //val staticLib = project(":library").buildDir.resolve("llama-cmake/iPhoneSimulator/iosSimulatorArm64/libllama_static.a").absolutePath
            val staticLib = rootProject.projectDir
                .resolve("library/build/llama-cmake/$sdk/$arch/libllama_static.a")
                .absolutePath

            linkerOpts(
                "-L$libPath",
                "-Wl,-force_load", staticLib,
                "-ldl", "-lz",
                "-Wl,-no_implicit_dylibs"
            )
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

            // Multiplatform Settings to encrypted key-value data
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

            //implementation("com.mohamedrejeb.ksoup:ksoup-html:0.4.0")
            //implementation("com.mohamedrejeb.ksoup:ksoup-entities:0.4.0")

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
        /*
                ndk {
                    abiFilters.add("arm64-v8a")
                    //abiFilters("armeabi-v7a", "arm64-v8a", "x86")
                }
         */
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    /*
        sourceSets["main"].jniLibs.srcDirs("src/commonMain/jniLibs")
        externalNativeBuild {
            cmake {
                path = file("src/commonMain/cpp/CMakeLists.txt")
            }
        }
     */
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.llamatik.app.resources"
    generateResClass = always
}
