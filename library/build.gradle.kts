import com.android.build.api.dsl.LibraryExtension
import org.gradle.jvm.tasks.Jar

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

fun Project.propString(name: String): String? =
    findProperty(name)?.toString()?.takeIf { it.isNotBlank() }

fun canExec(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val f = File(path)
    return f.exists() && f.canExecute()
}

fun existingDir(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val f = File(path)
    return if (f.exists() && f.isDirectory) f.absolutePath else null
}

// Resolve EMSDK root in a way that works in Android Studio (no shell env needed).
fun Project.resolveEmsdkRoot(): String? {
    // 1) Gradle properties (IDE-safe)
    propString("EMSDK")?.let { existingDir(it)?.let { d -> return d } }
    propString("EMSDK_PATH")?.let { existingDir(it)?.let { d -> return d } }

    // 2) Environment variables (terminal / CI)
    existingDir(System.getenv("EMSDK"))?.let { return it }

    // 3) Common local layout: <repoParent>/emsdk (your setup: AndroidStudioProjects/emsdk)
    val parent = rootDir.parentFile
    existingDir(parent.resolve("emsdk").absolutePath)?.let { return it }

    // 4) Another common layout: <repoRoot>/emsdk
    existingDir(rootDir.resolve("emsdk").absolutePath)?.let { return it }

    return null
}

fun Project.resolveEmsdkToolOrNull(name: String, emsdkRoot: String?): String? {
    // Allow overrides in gradle.properties / env:
    // EMCMAKE_PATH=/full/path/to/emcmake
    // EMMAKE_PATH=/full/path/to/emmake
    val key = "${name.uppercase()}_PATH"
    propString(key)?.let { if (canExec(it)) return it }
    System.getenv(key)?.let { if (canExec(it)) return it }

    if (!emsdkRoot.isNullOrBlank()) {
        // If EMSDK root known, tools live here:
        // $EMSDK/upstream/emscripten/<tool>
        val p = File(emsdkRoot, "upstream/emscripten/$name").absolutePath

        // ✅ Robustness: even if cache restores without +x, return absolute path for clearer failure
        val f = File(p)
        if (f.exists()) {
            if (!f.canExecute()) {
                logger.warn("Found $name at $p but it's not executable. CI cache can strip +x. " +
                        "Fix by chmod +x in workflow, or set ${key} to a valid executable.")
            } else {
                return p
            }
            // return it anyway; ensureToolAtExecutionTime will fail with a good message if needed
            return p
        }
    }

    // As a last resort, let it be resolved from PATH at execution time.
    // (We DO NOT throw here, otherwise Android Studio sync fails.)
    return null
}

fun Project.ensureToolAtExecutionTime(toolLabel: String, resolvedPathOrName: String): String {
    // If it's an absolute path, verify it exists.
    if (resolvedPathOrName.contains(File.separatorChar)) {
        val f = file(resolvedPathOrName)
        if (!f.exists()) {
            throw GradleException("Cannot find '$toolLabel' at: ${f.absolutePath}")
        }
        if (!f.canExecute()) {
            throw GradleException(
                "Cannot execute '$toolLabel' at: ${f.absolutePath}\n" +
                        "On CI, this is often fixed by: chmod +x ${f.absolutePath}\n" +
                        "Or set ${toolLabel.uppercase()}_PATH in gradle.properties."
            )
        }
        return f.absolutePath
    }

    // Otherwise it's a bare command (e.g. "emcmake") → rely on PATH at runtime.
    return resolvedPathOrName
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    jvm()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

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
        propString("${name.uppercase()}_PATH")?.let { if (file(it).canExecute()) return it }
        System.getenv("${name.uppercase()}_PATH")?.let { if (file(it).canExecute()) return it }

        val candidates = mutableListOf(
            "/opt/homebrew/bin/$name",
            "/usr/local/bin/$name",
            "/usr/bin/$name"
        )
        candidates.addAll(extraCandidates)

        for (p in candidates) if (file(p).canExecute()) return p

        throw GradleException(
            "Cannot find required tool '$name'. " +
                    "Install it or set ${name.uppercase()}_PATH=/full/path/to/$name"
        )
    }

    val cmakePath = findTool("cmake")

    val hostOsName = System.getProperty("os.name").lowercase()
    val isMacHost = hostOsName.contains("mac")

    // ---- WASM (Emscripten) tools ----
    val emsdkRoot: String? = resolveEmsdkRoot()
    val emscriptenBinDir: String? = emsdkRoot?.let { File(it, "upstream/emscripten").absolutePath }

    val emcmakePath: String = resolveEmsdkToolOrNull("emcmake", emsdkRoot) ?: "emcmake"
    val emmakePath: String = resolveEmsdkToolOrNull("emmake", emsdkRoot) ?: "emmake"

    // ------------------------------------------------------------
    // iOS native build / merge tasks (MAC-ONLY)
    // ------------------------------------------------------------
    if (isMacHost) {
        val libtoolPath = findTool("libtool")

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

                    val sdCandidates = listOf(
                        "$libPath/libstable-diffusion.a",
                        "$libPath/stable-diffusion/libstable-diffusion.a",
                        "$libPath/stable-diffusion/src/libstable-diffusion.a",
                        "$libPath/stable-diffusion-build/libstable-diffusion.a",
                        "$libPath/stable-diffusion-build/src/libstable-diffusion.a",
                    )
                    val sdLib = sdCandidates.firstOrNull { file(it).exists() }
                    if (sdLib != null) {
                        args += sdLib
                    } else {
                        logger.warn("stable-diffusion static library not found in $libPath. iOS image generation symbols will NOT be linked.")
                    }

                    val zipLib = file("$libPath/libzip.a").takeIf { it.exists() }?.absolutePath
                    if (zipLib != null) {
                        args += zipLib
                    }

                    commandLine(args)
                }
            }

            tasks.withType<org.jetbrains.kotlin.gradle.tasks.CInteropProcess>().configureEach {
                dependsOn(mergeTask)
            }

            arch.compilations.getByName("main").cinterops {
                create("llama") {
                    val defFileName = "llama_ios.def"
                    defFile("src/iosMain/c_interop/$defFileName")
                    packageName("com.llamatik.library.platform.llama")
                    compilerOpts("-I${projectDir}/src/iosMain/c_interop/include")
                    extraOpts("-libraryPath", libPath)
                    tasks.named(interopProcessingTaskName).configure { dependsOn(mergeTask) }
                }

                create("whisper") {
                    val defFileName = "whisper_ios.def"
                    defFile("src/iosMain/c_interop/$defFileName")
                    packageName("com.llamatik.library.platform.whisper")
                    compilerOpts("-I${projectDir}/src/iosMain/c_interop/include")
                    extraOpts("-libraryPath", libPath)
                    tasks.named(interopProcessingTaskName).configure { dependsOn(mergeTask) }
                }

                create("stableDiffusion") {
                    val defFileName = "stable_diffusion_ios.def"
                    defFile("src/iosMain/c_interop/$defFileName")
                    packageName("com.llamatik.library.platform.sd")
                    compilerOpts(
                        "-I${projectDir}/src/iosMain/c_interop/include",
                        "-I${rootDir}/stable-diffusion.cpp/include"
                    )
                    extraOpts("-libraryPath", libPath)
                    tasks.named(interopProcessingTaskName).configure { dependsOn(mergeTask) }
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
    } else {
        logger.lifecycle("Skipping iOS native build tasks (xcrun/libtool) because host OS is not macOS: $hostOsName")
    }

    // ---------- Desktop (JVM) JNI build for llama_jni ----------
    val desktopPlatform = when {
        hostOsName.contains("mac") -> "macos"
        hostOsName.contains("linux") -> "linux"
        hostOsName.contains("win") -> "windows"
        else -> error("Unsupported desktop OS: $hostOsName")
    }

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
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_POSITION_INDEPENDENT_CODE=ON"
            )

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

    val libFileName = System.mapLibraryName("llama_jni")

    val generatedNativeResourcesDir = layout.buildDirectory.dir("generated/native-resources").get().asFile

    val copyDesktopJniToResources by tasks.registering(Copy::class) {
        group = "llama-native"
        dependsOn(compileLlamaJniDesktop)

        val outDir = generatedNativeResourcesDir.resolve("native/$desktopPlatform")
        from(desktopJniBuildDir.resolve(libFileName))
        into(outDir)

        outputs.dir(outDir)

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

    // ---------- Web (WASM) native build ----------
    val wasmNativeSourceDir = projectDir.resolve("cmake/llamatik-wasm")
    val wasmNativeBuildDir = layout.buildDirectory.dir("llamatik-wasm").get().asFile

    val wasmResourcesOutDir = projectDir.resolve("src/wasmJsMain/resources/kotlin/llamatik_wasm")

    val wasmCFlags = "-O3 -sMEMORY64=1 -sWASM_BIGINT=1"
    val wasmCxxFlags = "-O3 -sMEMORY64=1 -sWASM_BIGINT=1"
    val wasmLinkFlags = "-sMEMORY64=1 -sWASM_BIGINT=1"

    val configureLlamatikWasm by tasks.registering(Exec::class) {
        group = "llama-native"
        description = "Configure Emscripten CMake for WebAssembly (llamatik wasm engine)"

        doFirst {
            if (!wasmNativeSourceDir.resolve("CMakeLists.txt").exists()) {
                throw GradleException(
                    "WASM CMakeLists.txt not found at: ${wasmNativeSourceDir.resolve("CMakeLists.txt").absolutePath}\n" +
                            "Expected a CMake project under: library/cmake/llamatik-wasm\n" +
                            "This project must produce llamatik_wasm.mjs + llamatik_wasm.wasm."
                )
            }
            wasmNativeBuildDir.mkdirs()

            val resolved = ensureToolAtExecutionTime("emcmake", emcmakePath)

            if (!emsdkRoot.isNullOrBlank() && !emscriptenBinDir.isNullOrBlank()) {
                environment("EMSDK", emsdkRoot)
                environment("PATH", emscriptenBinDir + ":" + System.getenv("PATH"))
            }

            val args = mutableListOf(
                resolved,
                cmakePath,
                "-S", wasmNativeSourceDir.absolutePath,
                "-B", wasmNativeBuildDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DCMAKE_C_FLAGS=$wasmCFlags",
                "-DCMAKE_CXX_FLAGS=$wasmCxxFlags",
            )

            if (wasmLinkFlags.isNotBlank()) {
                args += "-DCMAKE_EXE_LINKER_FLAGS=$wasmLinkFlags"
            }

            commandLine(args)
        }
    }

    val buildLlamatikWasm by tasks.registering(Exec::class) {
        group = "llama-native"
        description = "Build WebAssembly (llamatik wasm engine)"
        dependsOn(configureLlamatikWasm)

        doFirst {
            val resolved = ensureToolAtExecutionTime("emmake", emmakePath)

            if (!emsdkRoot.isNullOrBlank() && !emscriptenBinDir.isNullOrBlank()) {
                environment("EMSDK", emsdkRoot)
                environment("PATH", emscriptenBinDir + ":" + System.getenv("PATH"))
            }

            commandLine(
                resolved,
                cmakePath,
                "--build", wasmNativeBuildDir.absolutePath,
                "--config", "Release"
            )
        }
    }

    val copyLlamatikWasmToResources by tasks.registering(Copy::class) {
        group = "llama-native"
        description = "Copy llamatik_wasm.{mjs,wasm} into wasmJs resources"
        dependsOn(buildLlamatikWasm)

        val mjs = wasmNativeBuildDir.resolve("llamatik_wasm.mjs")
        val wasm = wasmNativeBuildDir.resolve("llamatik_wasm.wasm")

        from(mjs, wasm)
        into(wasmResourcesOutDir)

        doFirst {
            if (!mjs.exists()) throw GradleException("Missing WASM JS module: ${mjs.absolutePath}")
            if (!wasm.exists()) throw GradleException("Missing WASM binary: ${wasm.absolutePath}")
        }
    }

    tasks.matching { it.name == "wasmJsProcessResources" }.configureEach {
        dependsOn(copyLlamatikWasmToResources)
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
                    url.set("https://github.com/ferranpons/llamatik")
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
