plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.compose")
    id("com.android.library")
    id("maven-publish")
}

group = "com.llamatik.library"
version = "0.4.0"

// Choose ONE min iOS version and use it everywhere
val minIos = "16.6"

kotlin {
    // Helper to find an executable reliably
    fun findTool(name: String, extraCandidates: List<String> = emptyList()): String {
        // 1) env override
        System.getenv("${name.uppercase()}_PATH")?.let { if (file(it).canExecute()) return it }

        // 2) common locations
        val candidates = mutableListOf(
            "/opt/homebrew/bin/$name",   // Apple Silicon Homebrew
            "/usr/local/bin/$name",      // Intel Homebrew or manual install
            "/usr/bin/$name"             // system (libtool lives here)
        )
        candidates.addAll(extraCandidates)

        // 3) PATH lookup via `which`
        try {
            val out = providers.exec { commandLine("which", name) }
                .standardOutput.asText.get().trim()
            if (out.isNotEmpty() && file(out).canExecute()) return out
        } catch (_: Throwable) {}

        // 4) fallbacks
        for (p in candidates) if (file(p).canExecute()) return p

        throw GradleException("Cannot find required tool '$name'. " +
                "Install it (e.g. 'brew install $name') or set ${name.uppercase()}_PATH=/full/path/to/$name")
    }

    // Resolve tools once
    val cmakePath = findTool("cmake")
    val libtoolPath = findTool("libtool") // should be /usr/bin/libtool on macOS

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
        Triple(iosX64(), "x86_64", "iPhoneSimulator"),
        Triple(iosArm64(), "arm64", "iPhoneOS"),
        Triple(iosSimulatorArm64(), "arm64", "iPhoneSimulator")
    ).forEach { (arch, archName, sdkName) ->
        val cmakeBuildDir = buildDir.resolve("llama-cmake/$sdkName/${arch.name}")
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
                    "-DLLAMA_CURL=OFF"
                )
            }
        }

        val compileTask = tasks.register("compileLlamaCMake${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(buildTaskName)
            environment("PATH", "/opt/homebrew/bin:" + System.getenv("PATH"))
            commandLine = listOf(cmakePath, "--build", cmakeBuildDir.absolutePath, "--target", "llama_static_wrapper", "--verbose")
        }

        val libPath = cmakeBuildDir.absolutePath
        val mergeTask = tasks.register("mergeLlamaStatic${arch.name.replaceFirstChar { it.uppercase() }}", Exec::class) {
            dependsOn(compileTask)
            commandLine(
                libtoolPath, "-static",
                "-o", "$libPath/libllama_merged.a",
                "$libPath/libllama_static.a",
                "$libPath/llama-local-build/src/libllama.a",
                "$libPath/llama-local-build/ggml/src/libggml.a",
                "$libPath/llama-local-build/ggml/src/ggml-blas/libggml-blas.a",
                "$libPath/llama-local-build/ggml/src/ggml-metal/libggml-metal.a"
            )
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
            dependsOn(mergeTask)
        }

        arch.compilations.getByName("main").cinterops {
            create("llama") {
                val defFileName = if (sdkName.contains("Simulator"))
                    "llama_ios_simulator.def"
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

        // Final framework linking: force-load the merged archive and add Apple frameworks
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
                if (sdkName.contains("Simulator")) "-mios-simulator-version-min=$minIos" else "-mios-version-min=$minIos"
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
                if (sdkName.contains("Simulator")) "-mios-simulator-version-min=$minIos" else "-mios-version-min=$minIos"
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
    // If you’re not building JNI libs, you can comment these out
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