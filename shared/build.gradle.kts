import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.korge)
}

kotlin {
    /*@OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.projectDir.path)
                    }
                }
            }
        }
    }*/
    /*
        val hostOs = System.getProperty("os.name")
        val isArm64 = System.getProperty("os.arch") == "aarch64"
        val isMingwX64 = hostOs.startsWith("Windows")
        val nativeTarget = when {
            hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
            hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
            hostOs == "Linux" && isArm64 -> linuxArm64("native")
            hostOs == "Linux" && !isArm64 -> linuxX64("native")
            isMingwX64 -> mingwX64("native")
            else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
        }

        nativeTarget.apply {
            compilations.getByName("main") {
                cinterops {
                    val llama by creating {
                        definitionFile.set(project.file("src/androidMain/c_interop/llama.def"))
                        compilerOpts("-I/path")
                        includeDirs.allHeaders("path")
                    }
                }
            }
            binaries {
                executable {
                    entryPoint = "main"
                }
            }
        }
    */

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        if (name == "android") {
            compilations["main"].cinterops.create("llama") {
                defFile(project.file("src/commonMain/c_interop/llama.def"))
            }
        }
    }
    /*
        val llamaRoot = rootProject.file("llama.cpp")
        val embedCpp = project.file("src/commonMain/cpp/llama_embed_ios.cpp")
        val embedInclude = project.file("src/commonMain/c_interop/include")
        val outputDir = buildDir.resolve("llama")
        val sdkPath = System.getenv("IOS_SDK_PATH")
            ?: "/Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator.sdk"
        val outputOCompiledFile = outputDir.resolve("llama_embed.o").absolutePath
        val outputACompiledFile = outputDir.resolve("libllama_embed.a").absolutePath
        val arch = "arm64"
        val sdk = "iphonesimulator" // or "iphonesimulator" "iphoneos" based on the target

        val compileLlamaCpp by tasks.registering(Exec::class) {
            outputs.dir(outputDir)
            outputs.file(outputDir.resolve("llama_embed.o"))

            val target = if (sdk == "iphoneos") {
                "$arch-apple-ios"
            } else {
                "$arch-apple-ios-simulator"
            }
            println("Target: $target\n")
            println("SDK Path: $sdkPath\n")
            println("OutputOCompiledFile: $outputOCompiledFile\n")
            println("OutputACompiledFile: $outputACompiledFile\n")

            commandLine(
                "clang++",
                "-c", // compile only, no linking
                "-std=c++17",
                "-O3",
                "-fPIC",
                "-c", embedCpp.absolutePath,
                "-I${llamaRoot.resolve("include")}",
                "-I${llamaRoot.absolutePath}",
                "-I${llamaRoot.resolve("src")}",
                "-I${llamaRoot.resolve("ggml")}",
                "-I${llamaRoot.resolve("ggml/include")}",
                "-I${embedInclude.absolutePath}",
                "-DINCLUDE_EXTRA_CMAKELISTS=ON",
                "-DGGML_OPENMP=OFF",
                "-DGGML_LLAMAFILE=OFF",
                "-target", target,
                "-isysroot", sdkPath,
                "-o", outputOCompiledFile
            )
        }

        val archiveLlamaCpp by tasks.registering(Exec::class) {
            dependsOn(compileLlamaCpp)
            inputs.file(outputOCompiledFile)
            outputs.file(outputACompiledFile)

            commandLine = listOf(
                "ar", "rcs",
                outputACompiledFile,
                outputOCompiledFile
            )
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(compileLlamaCpp)
            dependsOn(archiveLlamaCpp)
        }
    */
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

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
            baseName = "shared"
            isStatic = true
            linkerOpts(
                //"-L${project.rootDir}/shared/build/llama/$sdkName/", "-lllama_embed${arch.name.capitalize()}",
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
                    "${project.rootDir}/shared/src/commonMain/c_interop/include",
                    "${project.rootDir}/shared/src/commonMain/cpp/",
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
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
            }
        }
        commonMain.dependencies {
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

            implementation(libs.youtube.kmp)

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

    /*
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
            compilations["main"].cinterops.create("llama") {
                defFile(project.file("src/androidMain/c_interop/llama.def"))
            }
        }

     */
}

android {
    namespace = "com.llamatik.app"
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

compose.resources {
    publicResClass = true
    packageOfResClass = "com.llamatik.app.resources"
    generateResClass = always
}
