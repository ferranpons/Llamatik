pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://androidx.dev/storage/compose-compiler/repository")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        maven("https://s01.oss.sonatype.org/content/repositories/releases/")

        /* Example
                val gprUser = providers.gradleProperty("GH_USER").orNull
                val gprKey = providers.gradleProperty("GH_TOKEN").orNull
                maven {
                    url = uri("https://maven.pkg.github.com/ferranpons/llamatik")
                    credentials {
                        username = gprUser
                        password = gprKey
                    }
                }
         */

    }

    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "Llamatik"
include(":composeApp")
include(":shared")
include(":backend")
include(":library")
