plugins {
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    kotlin("android").version(libs.versions.kotlin).apply(false)
    kotlin("multiplatform").version(libs.versions.kotlin).apply(false)
    kotlin("plugin.serialization").version(libs.versions.kotlin).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.ktlint).apply(false)
    alias(libs.plugins.detekt).apply(false)
    id("com.google.gms.google-services") version "4.4.4" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set((findProperty("mavenCentralUsername") as String?) ?: System.getenv("MAVEN_CENTRAL_USERNAME"))
            password.set((findProperty("mavenCentralPassword") as String?) ?: System.getenv("MAVEN_CENTRAL_PASSWORD"))
            packageGroup.set("com.llamatik")
        }
    }
}

val kotlinVersion: String = libs.versions.kotlin.get()

subprojects {
    configurations.all {
        resolutionStrategy {
            // Prevent transitive deps from upgrading kotlin-stdlib beyond the compiler version.
            // A stdlib newer than the Kotlin compiler causes "Symbol for Any not found" when
            // compiling wasm targets because the Gradle plugin stops injecting the correct klib.
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-wasm-js:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-common:$kotlinVersion",
            )
        }
    }

    if (name != "desktopApp") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            debug.set(true)
            filter {
                exclude { element ->
                    element.file.path.contains("generated")
                }
            }
        }


        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            parallel = false
            config.setFrom("../config/detekt-config.yml")
            buildUponDefaultConfig = false
        }

    }
}