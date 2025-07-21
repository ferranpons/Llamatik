import java.io.ByteArrayOutputStream

fun execAndGetOutput(command: String): String {
    return ByteArrayOutputStream().use { output ->
        exec {
            commandLine = command.split(" ")
            standardOutput = output
        }
        output.toString().trim()
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    id("com.android.library")
}

kotlin {
    androidTarget()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        if (name == "android") {
            compilations["main"].cinterops.create("llama") {
                defFile(project.file("src/commonMain/c_interop/llama.def"))
            }
        }
    }
    /*
        androidLibrary {
            namespace = "com.llamatik.library"
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()

            withHostTestBuilder {
            }

            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }

            withDeviceTestBuilder {
                sourceSetTreeName = "test"
            }.configure {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }

        val xcfName = "libraryKit"
        iosX64 {
            binaries.framework {
                baseName = xcfName
            }
        }

        iosArm64 {
            binaries.framework {
                baseName = xcfName
            }
        }

        iosSimulatorArm64 {
            binaries.framework {
                baseName = xcfName
            }
        }
        */

    jvm()

    val llamaRoot = rootProject.file("llama.cpp")
    val embedCpp = project.file("src/commonMain/cpp/llama_embed_ios.cpp")
    val embedInclude = project.file("src/commonMain/c_interop/include")

    listOf(
        Triple(iosX64(), "x86_64", "MacOSX"),
        Triple(iosArm64(), "arm64", "iPhoneOS"),
        Triple(iosSimulatorArm64(), "arm64", "iPhoneSimulator")
    ).forEach { (arch, archName, sdkName) ->

        val outputDir = buildDir.resolve("llama/$sdkName/")
        val outputOCompiledFile =
            outputDir.resolve("llama_embed${arch.name.capitalize()}.o").absolutePath
        val outputACompiledFile =
            outputDir.resolve("libllama_embed${arch.name.capitalize()}.a").absolutePath

        val compileTaskName = "compileLlamaCpp${arch.name.capitalize()}"
        tasks.register(compileTaskName, Exec::class) {
            outputs.cacheIf { false }
            outputs.dir(outputDir)
            outputs.file(outputDir.resolve("llama_embed${arch.name.capitalize()}.o"))

            doFirst {
                val sdkPath = execAndGetOutput("xcrun --sdk ${sdkName.lowercase()} --show-sdk-path")
                val sdkVersion = 15.6
                //execAndGetOutput("xcrun --sdk ${sdkName.lowercase()} --show-sdk-version")
                val target =
                    if (sdkName.contains("Simulator")) "$archName-apple-ios${sdkVersion}-simulator" else "$archName-apple-ios${sdkVersion}"
                println("Target: $target\n")
                println("SDK Path: $sdkPath\n")
                println("OutputOCompiledFile: $outputOCompiledFile\n")
                println("OutputACompiledFile: $outputACompiledFile\n")

                commandLine(
                    "clang++",
                    "-c", // compile only, no linking
                    "-stdlib=libc++",
                    "-std=c++17",
                    "-O3",
                    "-fPIC",
                    "-I${llamaRoot.absolutePath}",
                    "-I${llamaRoot.resolve("include")}",
                    "-I${llamaRoot.resolve("src")}",
                    "-I${llamaRoot.resolve("ggml")}",
                    "-I${llamaRoot.resolve("ggml/include")}",
                    "-I${embedInclude.absolutePath}",
                    "-c", embedCpp.absolutePath,
                    "-DINCLUDE_EXTRA_CMAKELISTS=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-target", target,
                    "-isysroot", sdkPath,
                    "-o", outputOCompiledFile
                )
            }
        }

        val archiveTaskName = "archiveLlamaCpp${arch.name.capitalize()}"
        tasks.register(archiveTaskName, Exec::class) {
            dependsOn(compileTaskName)
            inputs.file(outputOCompiledFile)
            outputs.file(outputACompiledFile)

            commandLine = listOf(
                "ar", "rcs",
                outputACompiledFile,
                outputOCompiledFile
            )
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(compileTaskName)
            dependsOn(archiveTaskName)
        }

        arch.binaries.framework {
            baseName = "library"
            isStatic = true
            linkerOpts(
                //"-L${project.rootDir}/library/build/llama/$sdkName/", "-lllama_embed${arch.name.capitalize()}",
                "-Wl,-no_implicit_dylibs",
                //"-L${project.rootDir}/llama.cpp",
                //"-l$outputACompiledFile",
            )
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val defFileName =
                    if (sdkName.contains("Simulator")) "llama_ios_${archName}_simulator.def" else "llama_ios_${archName}.def"
                defFile("src/commonMain/c_interop/$defFileName")
                packageName("com.llamatik.app.platform.llama")
                includeDirs(
                    "${project.rootDir}/library/src/commonMain/c_interop/include",
                    "${project.rootDir}/library/src/commonMain/cpp/",
                    "${project.rootDir}/llama.cpp/ggml",
                    "${project.rootDir}/llama.cpp/ggml/include",
                    "${project.rootDir}/llama.cpp",
                    "${project.rootDir}/llama.cpp/include",
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(compileTaskName)
                    dependsOn(archiveTaskName)
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(compose.ui)
                implementation(compose.foundation)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
            }
        }
        /*
                getByName("androidDeviceTest") {
                    dependencies {
                        implementation(libs.androidx.runner)
                        implementation(libs.androidx.core)
                        implementation(libs.androidx.junit)
                    }
                }
        */
        iosMain {
            dependencies {
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
            //abiFilters("armeabi-v7a", "arm64-v8a", "x86")
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
