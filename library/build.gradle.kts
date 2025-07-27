import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    id("com.android.library")
    id("maven-publish")
}

group = "com.llamatik.library"
version = "0.4.0"

kotlin {
    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "llamatik"
            isStatic = true
            linkerOpts("-Wl,-no_implicit_dylibs")
        }
    }

    listOf(
        Triple(iosX64(), "x86_64", "MacOSX"),
        Triple(iosArm64(), "arm64", "iPhoneOS"),
        Triple(iosSimulatorArm64(), "arm64", "iPhoneSimulator")
    ).forEach { (arch, archName, sdkName) ->
        val cmakeBuildDir = buildDir.resolve("llama-cmake/$sdkName/${arch.name}")
        val buildTaskName = "buildLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}"

        tasks.register(buildTaskName, Exec::class) {
            doFirst {
                val command = "xcrun --sdk ${sdkName.lowercase()} --show-sdk-path"
                val outputStream = ByteArrayOutputStream()
                ProcessBuilder(*command.split(" ").toTypedArray())
                    .redirectErrorStream(true)
                    .start()
                    .apply { inputStream.copyTo(outputStream) }
                    .waitFor()
                val sdkPath = outputStream.toString().trim()
                cmakeBuildDir.mkdirs()
                commandLine = listOf(
                    "cmake",
                    "-S", "../llama.cpp",
                    "-B", cmakeBuildDir.absolutePath,
                    "-DCMAKE_SYSTEM_NAME=iOS",
                    "-DCMAKE_OSX_ARCHITECTURES=$archName",
                    "-DCMAKE_OSX_SYSROOT=$sdkPath",
                    "-DCMAKE_INSTALL_PREFIX=${cmakeBuildDir.resolve("install")}",
                    "-DCMAKE_IOS_INSTALL_COMBINED=NO",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DBUILD_TESTING=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",           // ✅ Prevents CLI tools from being built
                    "-DLLAMA_BUILD_SERVER=OFF",          // ✅ If defined by llama.cpp
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_STATIC=ON",
                    "-DLLAMA_CURL=OFF",
                )
            }
        }

        val compileTask = tasks.register("compileLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(buildTaskName)
            commandLine = listOf("cmake", "--build", cmakeBuildDir.absolutePath, "--config", "Release")
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(compileTask)
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val defFileName = if (sdkName.contains("Simulator"))
                    "llama_ios_${archName}_simulator.def"
                else
                    "llama_ios_${archName}.def"

                defFile("src/commonMain/c_interop/$defFileName")
                packageName("com.llamatik.app.platform.llama")
                includeDirs(
                    "src/commonMain/c_interop/include",
                    "src/commonMain/cpp/"
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(compileTask)
                }
            }
        }

        arch.binaries.getFramework("DEBUG").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L${cmakeBuildDir.resolve("Release")}",
                "-lllama",
                "-Wl,-no_implicit_dylibs"
            )
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(compose.ui)
                implementation(compose.foundation)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependencies {
                dependsOn(this@creating)
            }
            iosArm64Main.dependencies {
                dependsOn(this@creating)
            }
            iosSimulatorArm64Main.dependencies {
                dependsOn(this@creating)
            }
        }
    }
}

android {
    namespace = "com.llamatik.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    sourceSets["main"].jniLibs.srcDirs("src/commonMain/jniLibs")
    externalNativeBuild {
        cmake {
            path = file("src/commonMain/cpp/CMakeLists.txt")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["kotlin"])
            groupId = "com.llamatik.library"
            artifactId = "llamatik"
            version = "0.4.0"
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ferranpons/llamatik")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}

// Ensure all iOS frameworks are built before publishing
afterEvaluate {
    tasks.named("publish") {
        dependsOn(
            "linkDebugFrameworkIosArm64",
            "linkDebugFrameworkIosX64",
            "linkDebugFrameworkIosSimulatorArm64"
        )
    }
}