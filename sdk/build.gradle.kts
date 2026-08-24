import com.android.build.api.dsl.LibraryExtension
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.android.library")
    id("org.jetbrains.dokka") version "2.1.0"
    id("maven-publish")
    id("signing")
    kotlin("plugin.serialization")
}

group = "com.llamatik"
version = (System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT")

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
        publishLibraryVariants("release")
    }

    jvm()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "llamatik-sdk"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.llamatik.sdk")
            freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios=16.6"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.io)

            implementation(libs.ktor.client)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.server.serialization.kotlinx.json)

            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.serialization)

            implementation(libs.kermit)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

extensions.configure<LibraryExtension> {
    namespace = "com.llamatik.sdk"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

fun Project.propString(name: String): String? =
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

val dokkaHtmlDir = layout.buildDirectory.dir("dokka/html")

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.set("javadoc")
    tasks.findByName("dokkaGeneratePublicationHtml")?.let { dependsOn(it) }
        ?: tasks.findByName("dokkaGenerateHtml")?.let { dependsOn(it) }
    from(dokkaHtmlDir)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Llamatik SDK")
            description.set("Kotlin Multiplatform SDK for building LLM-powered applications with Llamatik.")
            url.set("https://github.com/ferranpons/llamatik")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("ferranpons")
                    name.set("Ferran Pons")
                    url.set("https://github.com/ferranpons/llamatik")
                }
            }
            scm {
                url.set("https://github.com/ferranpons/llamatik")
                connection.set("scm:git:git://github.com/ferranpons/llamatik.git")
                developerConnection.set("scm:git:ssh://github.com/ferranpons/llamatik.git")
            }
        }

        if (name.contains("jvm", ignoreCase = true)) {
            artifact(javadocJar)
        }
    }
}

signing {
    val signingKey = (findProperty("signingInMemoryKey") as String?) ?: System.getenv("SIGNING_KEY")
    val signingPassword = (findProperty("signingInMemoryKeyPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
