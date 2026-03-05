cmake_minimum_required(VERSION 3.16)
project(llama_kmp_bridge C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---- iOS defaults (only if not passed in by Gradle) -------------------------
if(NOT DEFINED CMAKE_OSX_DEPLOYMENT_TARGET)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "14.0" CACHE STRING "Min iOS version" FORCE)
endif()

# ---- Threading --------------------------------------------------------------
if(CMAKE_SYSTEM_NAME STREQUAL "iOS")
    set(THREADS_PREFER_PTHREAD_FLAG ON)
    set(CMAKE_THREAD_LIBS_INIT "-lpthread")
    set(CMAKE_HAVE_THREADS_LIBRARY 1)
    set(CMAKE_USE_WIN32_THREADS_INIT 0)
    set(CMAKE_USE_PTHREADS_INIT 1)
endif()
find_package(Threads REQUIRED)

# ---- Point to llama.cpp -----------------------------------------------------
set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../llama.cpp")

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at: ${LLAMA_CPP_DIR}")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build llama as static library" FORCE)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} llama-local-build EXCLUDE_FROM_ALL)

set(LLAMA_COMMON_CPP      "${LLAMA_CPP_DIR}/common/common.cpp")
set(LLAMA_SAMPLING_CPP    "${LLAMA_CPP_DIR}/common/sampling.cpp")
set(LLAMA_LOG_CPP         "${LLAMA_CPP_DIR}/common/log.cpp")
set(LLAMA_SCHEMA_CPP      "${LLAMA_CPP_DIR}/common/json-schema-to-grammar.cpp")
set(LLAMA_BUILDINFO_IN    "${LLAMA_CPP_DIR}/common/build-info.cpp.in")

if(NOT EXISTS "${LLAMA_COMMON_CPP}")
    message(FATAL_ERROR "Expected llama common source not found: ${LLAMA_COMMON_CPP}")
endif()

if(NOT EXISTS "${LLAMA_SAMPLING_CPP}")
    message(FATAL_ERROR "Expected llama sampling source not found: ${LLAMA_SAMPLING_CPP}")
endif()

if(NOT EXISTS "${LLAMA_SCHEMA_CPP}")
    message(FATAL_ERROR "Expected schema source not found: ${LLAMA_SCHEMA_CPP}")
endif()

set(GENERATED_DIR "${CMAKE_BINARY_DIR}/generated")
file(MAKE_DIRECTORY "${GENERATED_DIR}")

set(LLAMA_BUILDINFO_CPP "${GENERATED_DIR}/build-info.cpp")

set(LLAMA_BUILD_COMMIT "unknown")
execute_process(
        COMMAND git -C "${LLAMA_CPP_DIR}" rev-parse --short HEAD
        OUTPUT_VARIABLE _GIT_SHA
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
)
if(_GIT_SHA)
    set(LLAMA_BUILD_COMMIT "${_GIT_SHA}")
endif()

set(LLAMA_BUILD_NUMBER 0)

set(BUILD_COMPILER "${CMAKE_CXX_COMPILER_ID} ${CMAKE_CXX_COMPILER_VERSION}")
set(BUILD_TARGET "${CMAKE_SYSTEM_NAME}")

if(DEFINED CMAKE_OSX_SYSROOT)
    set(BUILD_TARGET "${BUILD_TARGET} ${CMAKE_OSX_SYSROOT}")
endif()

if(DEFINED CMAKE_OSX_ARCHITECTURES)
    set(BUILD_TARGET "${BUILD_TARGET} ${CMAKE_OSX_ARCHITECTURES}")
elseif(DEFINED CMAKE_SYSTEM_PROCESSOR)
    set(BUILD_TARGET "${BUILD_TARGET} ${CMAKE_SYSTEM_PROCESSOR}")
endif()

if(EXISTS "${LLAMA_BUILDINFO_IN}")
    set(LLAMA_BUILD_COMMIT "${LLAMA_BUILD_COMMIT}")
    configure_file("${LLAMA_BUILDINFO_IN}" "${LLAMA_BUILDINFO_CPP}" @ONLY)
else()
    message(WARNING "build-info.cpp.in not found at ${LLAMA_BUILDINFO_IN}; generating minimal build-info.cpp")
    file(WRITE "${LLAMA_BUILDINFO_CPP}"
            "int LLAMA_BUILD_NUMBER = ${LLAMA_BUILD_NUMBER};\n"
            "char const *LLAMA_COMMIT = \"${LLAMA_BUILD_COMMIT}\";\n"
            "char const *LLAMA_COMPILER = \"${BUILD_COMPILER}\";\n"
            "char const *LLAMA_BUILD_TARGET = \"${BUILD_TARGET}\";\n"
    )
endif()

# ---- whisper.cpp ------------------------------------------------------------
set(WHISPER_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../whisper.cpp")

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_BINARY_DIR}/whisper")

# ---- stable-diffusion.cpp ---------------------------------------------------
set(STABLE_DIFFUSION_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../stable-diffusion.cpp")
set(HAVE_SD OFF)
if(EXISTS "${STABLE_DIFFUSION_CPP_DIR}/CMakeLists.txt")
    set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
    set(SD_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)
    add_subdirectory("${STABLE_DIFFUSION_CPP_DIR}" "${CMAKE_BINARY_DIR}/stable-diffusion")
    set(HAVE_SD ON)
else()
    message(WARNING "stable-diffusion.cpp not found at: ${STABLE_DIFFUSION_CPP_DIR}. iOS build will not include SD.")
endif()

add_library(llamatik_llama_common_obj OBJECT
        "${LLAMA_COMMON_CPP}"
        "${LLAMA_SAMPLING_CPP}"
        "${LLAMA_SCHEMA_CPP}"
        "${LLAMA_BUILDINFO_CPP}"
)

if(EXISTS "${LLAMA_LOG_CPP}")
    target_sources(llamatik_llama_common_obj PRIVATE "${LLAMA_LOG_CPP}")
endif()

target_include_directories(llamatik_llama_common_obj PRIVATE
        ${LLAMA_CPP_DIR}
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/src
        ${LLAMA_CPP_DIR}/common
        ${LLAMA_CPP_DIR}/vendor
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${WHISPER_CPP_DIR}
        ${WHISPER_CPP_DIR}/include
        ${STABLE_DIFFUSION_CPP_DIR}
        ${STABLE_DIFFUSION_CPP_DIR}/include
)

target_compile_features(llamatik_llama_common_obj PRIVATE cxx_std_17)

add_library(llama_static_wrapper STATIC
        ${CMAKE_CURRENT_SOURCE_DIR}/../../src/iosMain/cpp/llama_embed.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../../src/iosMain/cpp/whisper_stt.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../../src/iosMain/cpp/stable_diffusion_embed.cpp
)

target_include_directories(llama_static_wrapper PUBLIC
        ${LLAMA_CPP_DIR}
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/src
        ${LLAMA_CPP_DIR}/common
        ${LLAMA_CPP_DIR}/vendor
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${WHISPER_CPP_DIR}
        ${WHISPER_CPP_DIR}/include
        ${STABLE_DIFFUSION_CPP_DIR}
        ${STABLE_DIFFUSION_CPP_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/../../src/iosMain/c_interop/include
)

# stable-diffusion headers are needed to compile stable_diffusion_embed.cpp
if(HAVE_SD)
    target_include_directories(llama_static_wrapper PUBLIC
            ${STABLE_DIFFUSION_CPP_DIR}
            ${STABLE_DIFFUSION_CPP_DIR}/include
    )
endif()

target_compile_features(llama_static_wrapper PUBLIC cxx_std_17)

target_link_libraries(llama_static_wrapper
        PRIVATE
        llama
        whisper
        stable-diffusion
        Threads::Threads
)

# Link stable diffusion target if it exists in that CMake project.
# The exact target name can differ across forks; most commonly it's "stable-diffusion".
if(HAVE_SD)
    if(TARGET stable-diffusion)
        target_link_libraries(llama_static_wrapper PRIVATE stable-diffusion)
    elseif(TARGET stable_diffusion)
        target_link_libraries(llama_static_wrapper PRIVATE stable_diffusion)
    endif()
endif()

target_sources(llama_static_wrapper PRIVATE
        $<TARGET_OBJECTS:llamatik_llama_common_obj>
)

set_target_properties(llama_static_wrapper PROPERTIES
        ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}"
        OUTPUT_NAME "llama_static"
)

set(LLAMATIK_STATIC_LIB "${CMAKE_BINARY_DIR}/libllama_static.a"
        CACHE FILEPATH "Path to built llama wrapper archive"
)
