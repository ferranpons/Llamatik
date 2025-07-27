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
        val wrapperObjectFile = cmakeBuildDir.resolve("llama_embed_${arch.name}.o")
        val wrapperSource = projectDir.resolve("src/commonMain/cpp/llama_embed_ios.cpp")

        tasks.register(buildTaskName, Exec::class) {
            doFirst {
                val sdk = when {
                    sdkName == "MacOSX" -> "macosx"
                    sdkName.contains("Simulator") -> "iphonesimulator"
                    else -> "iphoneos"
                }
                val sdkPath = ByteArrayOutputStream().use { output ->
                    exec {
                        commandLine = listOf("xcrun", "--sdk", sdk, "--show-sdk-path")
                        standardOutput = output
                    }
                    output.toString().trim()
                }
                val systemName = if (sdk == "macosx") "Darwin" else "iOS"
                cmakeBuildDir.mkdirs()
                commandLine = listOf(
                    "cmake",
                    "-S", "cmake/llama-wrapper",
                    "-B", cmakeBuildDir.absolutePath,
                    "-DCMAKE_SYSTEM_NAME=$systemName",
                    "-DCMAKE_OSX_ARCHITECTURES=$archName",
                    "-DCMAKE_OSX_SYSROOT=$sdkPath",
                    "-DCMAKE_INSTALL_PREFIX=${cmakeBuildDir.resolve("install")}",
                    "-DCMAKE_IOS_INSTALL_COMBINED=NO",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                    "-DLLAMA_STATIC=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_CURL=OFF"
                )
            }
        }

        val compileTask = tasks.register("compileLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(buildTaskName)
            commandLine = listOf("cmake", "--build", cmakeBuildDir.absolutePath, "--target", "llama_static")
        }

        val compileWrapperTask = tasks.register("compileWrapper${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(compileTask)
            doFirst {
                val sdk = when {
                    sdkName == "MacOSX" -> "macosx"
                    sdkName.contains("Simulator") -> "iphonesimulator"
                    else -> "iphoneos"
                }
                val sdkVersion = "15.6"
                val targetTriple = when (sdk) {
                    "macosx" -> "x86_64-apple-macosx10.15"
                    "iphonesimulator" -> "$archName-apple-ios$sdkVersion-simulator"
                    else -> "$archName-apple-ios$sdkVersion"
                }
                val sdkPath = ByteArrayOutputStream().use { out ->
                    exec {
                        commandLine = listOf("xcrun", "--sdk", sdk, "--show-sdk-path")
                        standardOutput = out
                    }
                    out.toString().trim()
                }

                commandLine = listOf(
                    "clang++", "-c", "-std=c++17", "-O3", "-fPIC",
                    "-I${rootProject.projectDir}/llama.cpp",
                    "-I${rootProject.projectDir}/llama.cpp/include",
                    "-I${rootProject.projectDir}/llama.cpp/ggml",
                    "-I${rootProject.projectDir}/llama.cpp/ggml/include",
                    "-I${projectDir}/src/commonMain/c_interop/include",
                    "-target", targetTriple,
                    "-isysroot", sdkPath,
                    "-o", wrapperObjectFile.absolutePath,
                    wrapperSource.absolutePath
                )
                println("[CLANG COMPILE COMMAND]")
                println(commandLine.joinToString(" \\\n  "))
            }
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(compileWrapperTask)
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val defFileName = if (sdkName.contains("Simulator"))
                    "llama_ios_${archName}_simulator.def"
                else
                    "llama_ios_${archName}.def"

                defFile("src/commonMain/c_interop/$defFileName")
                packageName("com.llamatik.app.platform.llama")

                val libPath = cmakeBuildDir.absolutePath
                compilerOpts("-I${projectDir}/src/commonMain/c_interop/include")
                linkerOpts(
                    "-L$libPath",
                    "-llama_static",
                    wrapperObjectFile.absolutePath
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(compileWrapperTask)
                }
            }
        }

        arch.binaries.getFramework("DEBUG").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L${cmakeBuildDir.absolutePath}",
                "-llama_static",
                wrapperObjectFile.absolutePath,
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