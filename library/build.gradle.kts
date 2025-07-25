import java.io.ByteArrayOutputStream

fun Project.execAndGetOutput(command: String): String {
    val outputStream = ByteArrayOutputStream()
    exec {
        commandLine = command.split(" ")
        standardOutput = outputStream
        isIgnoreExitValue = true // or false if you want failures
    }
    return outputStream.toString().trim()
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    id("com.android.library")
    id("maven-publish")
}

kotlin {
    androidTarget()
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "library"
            isStatic = true
            linkerOpts("-Wl,-no_implicit_dylibs")
        }
    }

    listOf(
        Triple(iosX64(), "x86_64", "MacOSX"),
        Triple(iosArm64(), "arm64", "iPhoneOS"),
        Triple(iosSimulatorArm64(), "arm64", "iPhoneSimulator")
    ).forEach { (arch, archName, sdkName) ->
        val outputDir = buildDir.resolve("llama/$sdkName/")
        val outputOCompiledFile = outputDir.resolve("llama_embed${arch.name.capitalize()}.o").absolutePath
        val outputACompiledFile = outputDir.resolve("libllama_embed${arch.name.capitalize()}.a").absolutePath
        val compileTaskName = "compileLlamaCpp${arch.name.capitalize()}"
        val archiveTaskName = "archiveLlamaCpp${arch.name.capitalize()}"

        tasks.register(compileTaskName, Exec::class) {
            outputs.dir(outputDir)
            doFirst {
                val llamaRoot = layout.projectDirectory.dir("../llama.cpp").asFile
                val embedCpp = layout.projectDirectory.file("src/commonMain/cpp/llama_embed_ios.cpp").asFile
                val embedInclude = layout.projectDirectory.dir("src/commonMain/c_interop/include").asFile
                val sdkPath = execAndGetOutput("xcrun --sdk ${sdkName.lowercase()} --show-sdk-path")
                val sdkVersion = 15.6
                val target = if (sdkName.contains("Simulator"))
                    "$archName-apple-ios${sdkVersion}-simulator"
                else
                    "$archName-apple-ios${sdkVersion}"
                commandLine(
                    "clang++", "-c", "-stdlib=libc++", "-std=c++17", "-O3", "-fPIC",
                    "-I${llamaRoot.absolutePath}",
                    "-I${llamaRoot.resolve("include")}",
                    "-I${llamaRoot.resolve("src")}",
                    "-I${llamaRoot.resolve("ggml")}",
                    "-I${llamaRoot.resolve("ggml/include")}",
                    "-I${embedInclude.absolutePath}",
                    "-DINCLUDE_EXTRA_CMAKELISTS=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-target", target,
                    "-isysroot", sdkPath,
                    "-o", outputOCompiledFile,
                    embedCpp.absolutePath
                )
            }
        }

        tasks.register(archiveTaskName, Exec::class) {
            dependsOn(compileTaskName)
            inputs.file(outputOCompiledFile)
            outputs.file(outputACompiledFile)
            commandLine("ar", "rcs", outputACompiledFile, outputOCompiledFile)
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(compileTaskName)
            dependsOn(archiveTaskName)
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val llamaRoot = layout.projectDirectory.dir("../llama.cpp").asFile
                val defFileName = if (sdkName.contains("Simulator"))
                    "llama_ios_${archName}_simulator.def"
                else
                    "llama_ios_${archName}.def"

                defFile("src/commonMain/c_interop/$defFileName")
                packageName("com.llamatik.app.platform.llama")
                includeDirs(
                    "src/commonMain/c_interop/include",
                    "src/commonMain/cpp/",
                    "${llamaRoot}/ggml",
                    "${llamaRoot}/ggml/include",
                    "${llamaRoot}",
                    "${llamaRoot}/include"
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(compileTaskName)
                    dependsOn(archiveTaskName)
                }
            }
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
            version = "0.1.0"
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