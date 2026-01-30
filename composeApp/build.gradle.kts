import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val versionNum: String? by project
val versionMajor = properties["llamatik.version.major"].toString().toInt()
val versionMinor = properties["llamatik.version.minor"].toString().toInt()
val versionPatch = properties["llamatik.version.patch"].toString().toInt()

fun versionCode(): Int {
    return versionMajor * 10000 + versionMinor * 100 + versionPatch
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvm("desktop")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        val desktopMain by getting
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.android)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(project(":shared"))

            // Dependency Injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.animation)
            implementation(compose.materialIconsExtended)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)

            implementation(libs.jetbrains.compose.ui.util)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swingui)
            implementation(libs.ktor.client)
            implementation(libs.ktor.client.java)
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
            implementation(libs.koin.compose)

            // Logging
            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(libs.koin.test)
        }
    }
}

android {
    namespace = "com.llamatik.app.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.llamatik.app.android"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = versionCode()
        versionName = "$versionMajor.$versionMinor.$versionPatch"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    dependencies {
        implementation(project(":shared"))

        implementation(platform(libs.compose.bom))
        implementation(platform(libs.firebase.bom))

        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.core.splashscreen)
        implementation(libs.androidx.tracing.ktx)

        implementation(libs.androidx.ui)
        implementation(libs.androidx.material)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.material3.window.size)
        implementation(libs.androidx.ui.tooling.preview)

        implementation(libs.androidx.activity.compose)
        implementation(libs.accompanist.systemuicontroller)

        implementation(libs.koin.core)
        implementation(libs.koin.android)

        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
        implementation(libs.firebase.analytics)

        implementation(libs.filekit.core)
        implementation(libs.filekit.dialogs)
        implementation(libs.filekit.dialogs.compose)

        testImplementation(libs.junit)
        testImplementation(libs.koin.test)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)

        // androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        debugImplementation(libs.androidx.ui.tooling)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        val nativeDir = project(":library")
            .buildDir
            .resolve("llama-jni/macos")
            .absolutePath

        run {
            // Ensure the dylib exists BEFORE we run
            dependsOn(":library:compileLlamaJniDesktop")

            jvmArgs(
                "-Dapple.awt.application.name=Llamatik",
                "-Djava.library.path=$nativeDir"
            )
        }

        nativeDistributions {
            macOS {
                bundleID = "com.llamatik.app"
                iconFile.set(project.file("src/desktopMain/resources/icons/mac/Llamatik.icns"))
            }
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Llamatik"
            packageVersion = "$versionMajor.$versionMinor.$versionPatch"
        }

        buildTypes.release.proguard {
            configurationFiles.from(file("proguard-rules.pro"))
        }
    }

    dependencies {
        implementation(compose.desktop.currentOs)
        implementation(compose.ui)
        implementation(compose.foundation)
        implementation(compose.material)
        implementation(compose.material3)
        implementation(compose.animation)
        implementation(compose.materialIconsExtended)
        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.components.resources)
        implementation(project(":shared"))

        implementation(libs.androidx.compose.ui.util)

        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.datetime)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.coroutines.swingui)
        implementation(libs.ktor.client)
        implementation(libs.ktor.client.java)
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
        testImplementation(libs.koin.test)
        implementation(libs.koin.compose)

        // Logging
        implementation(libs.kermit)
    }
}

val nativeDir = project(":library")
    .layout
    .buildDirectory
    .dir("llama-jni/macos")
    .get()
    .asFile
    .absolutePath

tasks.matching { it.name == "run" || it.name.endsWith("Run") }.configureEach {
    dependsOn(":library:compileLlamaJniDesktop")
}

tasks.withType(org.gradle.api.tasks.JavaExec::class.java).configureEach {
    if (name == "run" || name.endsWith("Run")) {
        dependsOn(":library:compileLlamaJniDesktop")
        jvmArgs(
            "-Dapple.awt.application.name=Llamatik",
            "-Djava.library.path=$nativeDir"
        )
    }
}
