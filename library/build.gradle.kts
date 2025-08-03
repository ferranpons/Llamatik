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
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.llamatik.library")
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
                val sourceDir = projectDir.resolve("cmake/llama-wrapper")
                val buildDir = cmakeBuildDir

                val sdk = when {
                    sdkName == "MacOSX" -> "macosx"
                    sdkName.contains("Simulator") -> "iphonesimulator"
                    else -> "iphoneos"
                }

                val sdkPathProvider = providers.exec {
                    commandLine("xcrun", "--sdk", sdk, "--show-sdk-path")
                }.standardOutput.asText.map { it.trim() }

                val systemName = if (sdk == "macosx") "Darwin" else "iOS"
                cmakeBuildDir.mkdirs()

                commandLine = listOf(
                    "cmake",
                    "-S", sourceDir.absolutePath,
                    "-B", buildDir.absolutePath,
                    "-DCMAKE_SYSTEM_NAME=$systemName",
                    "-DCMAKE_OSX_ARCHITECTURES=$archName",
                    "-DCMAKE_OSX_SYSROOT=${sdkPathProvider.get()}",
                    "-DCMAKE_INSTALL_PREFIX=${buildDir.resolve("install")}",
                    "-DCMAKE_IOS_INSTALL_COMBINED=NO",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_CURL=OFF"
                )
            }
        }

        val compileTask = tasks.register("compileLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(buildTaskName)
            commandLine = listOf("cmake", "--build", cmakeBuildDir.absolutePath, "--target", "llama_static_wrapper", "--verbose")
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

                defFile("src/iosMain/c_interop/$defFileName")
                packageName("com.llamatik.library.platform.llama")
                compilerOpts("-I${projectDir}/src/iosMain/c_interop/include")
                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(compileTask)
                }
            }
        }

        // Required linkerOpts for final framework binary (not inherited from .def!)
        val libPath = cmakeBuildDir.absolutePath
        println("Lib path: $libPath")
        val staticLib = "$libPath/libllama_static.a"
        println("Static lib: $staticLib")
        arch.binaries.getFramework("DEBUG").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L$libPath",
                //"-Wl,-all_load", staticLib,
                "-Wl,-no_implicit_dylibs"
            )
        }
        arch.binaries.getFramework("RELEASE").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L$libPath",
                //"-Wl,-all_load", staticLib,
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