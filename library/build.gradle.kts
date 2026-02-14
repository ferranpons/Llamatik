import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    id("com.android.library")
    id("org.jetbrains.dokka") version "2.1.0"
    id("maven-publish")
    id("signing")
}

group = "com.llamatik"
version = (System.getenv("RELEASE_VERSION") ?: "0.0.0-SNAPSHOT")

// Choose ONE min iOS version and use it everywhere
val minIos = "16.6"

kotlin {
    // ---- ANDROID target MUST publish a library variant (AAR) ----
    androidTarget {
        // This is the key bit that ensures the Android actuals (and AAR) are published
        publishLibraryVariants("release")
    }

    // JVM target (if you want JVM consumer artifacts)
    jvm()

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            baseName = "llamatik"
            isStatic = true
            linkerOpts("-Wl,-no_implicit_dylibs")
            freeCompilerArgs += listOf("-Xbinary=bundleId=com.llamatik.library")
            freeCompilerArgs += "-Xoverride-konan-properties=osVersionMin.ios=16.6"
        }
    }

    fun findTool(name: String, extraCandidates: List<String> = emptyList()): String {
        System.getenv("${name.uppercase()}_PATH")?.let { if (file(it).canExecute()) return it }
        val candidates = mutableListOf(
            "/opt/homebrew/bin/$name",   // Apple Silicon Homebrew
            "/usr/local/bin/$name",      // Intel Homebrew or manual install
            "/usr/bin/$name"             // system (libtool lives here)
        )
        candidates.addAll(extraCandidates)
        try {
            val out = providers.exec { commandLine("which", name) }
                .standardOutput.asText.get().trim()
            if (out.isNotEmpty() && file(out).canExecute()) return out
        } catch (_: Throwable) {}
        for (p in candidates) if (file(p).canExecute()) return p
        throw GradleException(
            "Cannot find required tool '$name'. " +
                    "Install it (e.g. 'brew install $name') or set ${name.uppercase()}_PATH=/full/path/to/$name"
        )
    }

    // Resolve tools once
    val cmakePath = findTool("cmake")
    val libtoolPath = findTool("libtool") // should be /usr/bin/libtool on macOS

    listOf(
        Triple(iosX64(), "x86_64", "iPhoneSimulator"),
        Triple(iosArm64(), "arm64", "iPhoneOS"),
        Triple(iosSimulatorArm64(), "arm64", "iPhoneSimulator")
    ).forEach { (arch, archName, sdkName) ->
        val cmakeBuildDir = layout.buildDirectory
            .dir("llama-cmake/$sdkName/${arch.name}")
            .get()
            .asFile
        val buildTaskName = "buildLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}"

        tasks.register(buildTaskName, Exec::class) {
            doFirst {
                val sourceDir = projectDir.resolve("cmake/llama-wrapper")
                val buildDir = cmakeBuildDir
                val sdk = when (sdkName) {
                    "iPhoneSimulator" -> "iphonesimulator"
                    "iPhoneOS" -> "iphoneos"
                    else -> "macosx"
                }
                val sdkPathProvider = providers.exec {
                    commandLine("xcrun", "--sdk", sdk, "--show-sdk-path")
                }.standardOutput.asText.map { it.trim() }
                val systemName = if (sdk == "macosx") "Darwin" else "iOS"
                cmakeBuildDir.mkdirs()
                environment("PATH", "/opt/homebrew/bin:" + System.getenv("PATH"))

                commandLine = listOf(
                    cmakePath,
                    "-S", sourceDir.absolutePath,
                    "-B", buildDir.absolutePath,
                    "-DCMAKE_SYSTEM_NAME=$systemName",
                    "-DCMAKE_OSX_ARCHITECTURES=$archName",
                    "-DCMAKE_OSX_SYSROOT=${sdkPathProvider.get()}",
                    "-DCMAKE_OSX_DEPLOYMENT_TARGET=$minIos",
                    "-DCMAKE_INSTALL_PREFIX=${buildDir.resolve("install")}",
                    "-DCMAKE_IOS_INSTALL_COMBINED=NO",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
                    "-DGGML_OPENMP=OFF",
                    "-DLLAMA_CURL=OFF",
                    if (sdk == "iphonesimulator") "-DLLAMA_BUILD_BERT=ON" else "-DLLAMA_BUILD_BERT=OFF",
                    if (sdk == "iphonesimulator") "-DLLAMA_BUILD_EMBEDDERS=ON" else "-DLLAMA_BUILD_EMBEDDERS=OFF",
                )
            }
        }

        val compileTask = tasks.register(
            "compileLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}",
            Exec::class
        ) {
            dependsOn(buildTaskName)
            environment("PATH", "/opt/homebrew/bin:" + System.getenv("PATH"))
            commandLine = listOf(
                cmakePath,
                "--build", cmakeBuildDir.absolutePath,
                "--target", "llama_static_wrapper",
                "--verbose"
            )
        }

        val libPath = cmakeBuildDir.absolutePath

        val mergeTask = tasks.register(
            "mergeLlamaStatic${arch.name.replaceFirstChar { it.uppercase() }}",
            Exec::class
        ) {
            dependsOn(compileTask)

            doFirst {
                // ---- Add whisper into the merged archive (so the symbols exist at link time) ----
                val whisperCandidates = listOf(
                    "$libPath/whisper/src/libwhisper.a",
                    "$libPath/whisper/libwhisper.a",
                    "$libPath/whisper-build/src/libwhisper.a",
                    "$libPath/whisper-build/libwhisper.a"
                )

                val whisperLib = whisperCandidates.firstOrNull { file(it).exists() }

                val args = mutableListOf(
                    libtoolPath, "-static",
                    "-o", "$libPath/libllama_merged.a",
                    "$libPath/libllama_static.a",
                    "$libPath/llama-local-build/src/libllama.a",
                    "$libPath/llama-local-build/ggml/src/libggml.a",
                    "$libPath/llama-local-build/ggml/src/libggml-base.a",
                    "$libPath/llama-local-build/ggml/src/libggml-cpu.a",
                    "$libPath/llama-local-build/ggml/src/ggml-blas/libggml-blas.a",
                    "$libPath/llama-local-build/ggml/src/ggml-metal/libggml-metal.a"
                )

                if (whisperLib != null) {
                    args += whisperLib
                } else {
                    logger.warn("Whisper static library not found in $libPath. iOS voice/STT symbols will NOT be linked.")
                }

                // ---- Add stable-diffusion.cpp into the merged archive (optional) ----
                val sdLib = file("$libPath/libstable-diffusion.a").takeIf { it.exists() }?.absolutePath
                if (sdLib != null) {
                    args += sdLib
                } else {
                    logger.warn("stable-diffusion static library not found in $libPath. iOS image generation symbols will NOT be linked.")
                }

                // stable-diffusion.cpp links a small static 'zip' lib (thirdparty)
                val zipLib = file("$libPath/libzip.a").takeIf { it.exists() }?.absolutePath
                if (zipLib != null) {
                    args += zipLib
                }

                commandLine(args)
            }
        }

        // Ensure cinterop runs after the native libs are built/merged
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(mergeTask)
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val defFileName = "llama_ios.def"

                defFile("src/iosMain/c_interop/$defFileName")
                packageName("com.llamatik.library.platform.llama")

                compilerOpts("-I${projectDir}/src/iosMain/c_interop/include")

                extraOpts(
                    "-libraryPath", libPath
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(mergeTask)
                }
            }

            create("whisper") {
                val defFileName = "whisper_ios.def"

                defFile("src/iosMain/c_interop/$defFileName")
                packageName("com.llamatik.library.platform.whisper")

                compilerOpts("-I${projectDir}/src/iosMain/c_interop/include")

                extraOpts(
                    "-libraryPath", libPath
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(mergeTask)
                }
            }

            create("stableDiffusion") {
                val defFileName = "stable_diffusion_ios.def"

                defFile("src/iosMain/c_interop/$defFileName")
                packageName("com.llamatik.library.platform.sd")

                compilerOpts(
                    "-I${projectDir}/src/iosMain/c_interop/include",
                    "-I${rootDir}/stable-diffusion.cpp/include"
                )

                extraOpts(
                    "-libraryPath", libPath
                )

                tasks.named(interopProcessingTaskName).configure {
                    dependsOn(mergeTask)
                }
            }
        }

        val merged = "$libPath/libllama_merged.a"

        arch.binaries.getFramework("DEBUG").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L$libPath",
                "-Wl,-force_load", merged,
                "-framework", "Accelerate",
                "-framework", "Metal",
                "-Wl,-no_implicit_dylibs",
                if (sdkName.contains("Simulator"))
                    "-mios-simulator-version-min=$minIos"
                else
                    "-mios-version-min=$minIos"
            )
        }
        arch.binaries.getFramework("RELEASE").apply {
            baseName = "llamatik"
            isStatic = true
            linkerOpts(
                "-L$libPath",
                "-Wl,-force_load", merged,
                "-framework", "Accelerate",
                "-framework", "Metal",
                "-Wl,-no_implicit_dylibs",
                if (sdkName.contains("Simulator"))
                    "-mios-simulator-version-min=$minIos"
                else
                    "-mios-version-min=$minIos"
            )
        }
    }

    // ---------- Desktop (JVM) JNI build for llama_jni (macOS/Linux/Windows) ----------

    // Detect host OS (for build output folder naming only)
    val hostOsName = System.getProperty("os.name").lowercase()
    val desktopPlatform = when {
        hostOsName.contains("mac") -> "macos"
        hostOsName.contains("linux") -> "linux"
        hostOsName.contains("win") -> "windows"
        else -> error("Unsupported desktop OS: $hostOsName")
    }

    // Output: library/build/llama-jni/<platform>/{libllama_jni.dylib|so|dll}
    val desktopJniBuildDir = layout.buildDirectory
        .dir("llama-jni/$desktopPlatform")
        .get()
        .asFile

    val desktopJniSourceDir = projectDir.resolve("cmake/llama-jni-desktop")

    val buildLlamaJniDesktop by tasks.registering(Exec::class) {
        group = "llama-native"
        description = "Configure CMake for desktop ($desktopPlatform) llama_jni"

        doFirst {
            if (!desktopJniSourceDir.resolve("CMakeLists.txt").exists()) {
                throw GradleException(
                    "Desktop JNI CMakeLists.txt not found at: ${desktopJniSourceDir.resolve("CMakeLists.txt").absolutePath}\n" +
                            "Expected a CMake project under library/src/commonMain/cpp"
                )
            }

            desktopJniBuildDir.mkdirs()

            val args = mutableListOf(
                cmakePath,
                "-S", desktopJniSourceDir.absolutePath,
                "-B", desktopJniBuildDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release"
            )

            // Optional: help CMake on macOS when run from CI
            if (desktopPlatform == "macos") {
                args += listOf("-DCMAKE_SYSTEM_NAME=Darwin")
            }

            commandLine(args)
        }
    }

    val compileLlamaJniDesktop by tasks.registering(Exec::class) {
        group = "llama-native"
        description = "Build desktop ($desktopPlatform) llama_jni native library"
        dependsOn(buildLlamaJniDesktop)

        commandLine(
            cmakePath,
            "--build", desktopJniBuildDir.absolutePath,
            "--config", "Release"
        )
    }

    val libFileName = System.mapLibraryName("llama_jni") // mac: libllama_jni.dylib, linux: libllama_jni.so, win: llama_jni.dll

    val generatedNativeResourcesDir = layout.buildDirectory.dir("generated/native-resources").get().asFile

    val copyDesktopJniToResources by tasks.registering(Copy::class) {
        group = "llama-native"
        dependsOn(compileLlamaJniDesktop)

        val outDir = generatedNativeResourcesDir.resolve("native/$desktopPlatform")
        from(desktopJniBuildDir.resolve(libFileName))
        into(outDir)

        outputs.dir(outDir) // ✅ helps Gradle validation

        doFirst {
            val f = desktopJniBuildDir.resolve(libFileName)
            if (!f.exists()) throw GradleException("Desktop JNI output not found: ${f.absolutePath}")
        }
    }

    tasks.matching { it.name == "jvmProcessResources" }.configureEach {
        dependsOn(copyDesktopJniToResources)
    }

    tasks.matching { it.name == "compileKotlinJvm" }.configureEach {
        dependsOn(compileLlamaJniDesktop)
        dependsOn(copyDesktopJniToResources)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.components.resources)
                resources.srcDir("src/commonMain/resources")
                resources.exclude("**/*.gguf")
            }
        }
        val commonTest by getting {
            dependencies { implementation(libs.kotlin.test) }
        }
        val androidMain by getting
        val jvmMain by getting {
            resources.srcDir(generatedNativeResourcesDir)
        }
    }
}

compose.resources {
    publicResClass = true
}

extensions.configure<LibraryExtension> {
    namespace = "com.llamatik"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
    }

    sourceSets.getByName("main").jniLibs.srcDirs("src/commonMain/jniLibs")

    externalNativeBuild {
        cmake {
            path = file("src/commonMain/cpp/CMakeLists.txt")
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

val dokkaPubHtmlTask: Task? = tasks.findByName("dokkaGeneratePublicationHtml")
val dokkaAllHtmlTask: Task? = tasks.findByName("dokkaGenerateHtml")

val dokkaHtmlDir = if (dokkaPubHtmlTask != null) {
    layout.buildDirectory.dir("dokka/htmlPublication")
} else {
    layout.buildDirectory.dir("dokka/html")
}

val javadocJar by tasks.registering(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    archiveClassifier.set("javadoc")
    dokkaPubHtmlTask?.let { dependsOn(it) } ?: dokkaAllHtmlTask?.let { dependsOn(it) }

    from(dokkaHtmlDir)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Llamatik")
            description.set("Kotlin Multiplatform library for LLaMA/LLM inference.")
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
                    url.set("https://github.com/ferranpons")
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
    useInMemoryPgpKeys(
        (findProperty("signingInMemoryKey") as String?) ?: System.getenv("SIGNING_KEY"),
        (findProperty("signingInMemoryKeyPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")
    )
    sign(publishing.publications)
}

afterEvaluate {
    tasks.named("publish") {
        dependsOn(
            "linkDebugFrameworkIosArm64",
            "linkDebugFrameworkIosX64",
            "linkDebugFrameworkIosSimulatorArm64"
        )
        dependsOn("assembleRelease")
    }
}
