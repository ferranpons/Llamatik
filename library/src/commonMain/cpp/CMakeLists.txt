cmake_minimum_required(VERSION 3.5)
project(llama_jni)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# DO NOT force CMAKE_BUILD_TYPE here; Android/Gradle controls it.

# Path to vendored llama.cpp inside the repo
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp")

# Build llama.cpp "common" helpers (needed for json-schema-to-grammar)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build llama.cpp common helpers" FORCE)

# Disable curl on Android builds (NDK doesn't provide libcurl by default)
set(LLAMA_CURL OFF CACHE BOOL "Disable curl support" FORCE)

# Also disable things that commonly pull in extra deps
set(LLAMA_SERVER OFF CACHE BOOL "Disable server" FORCE)
set(LLAMA_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(LLAMA_TESTS OFF CACHE BOOL "Disable tests" FORCE)
set(LLAMA_BENCHMARK OFF CACHE BOOL "Disable benchmarks" FORCE)

if(ANDROID)
    message(STATUS "Configuring llama.cpp for Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(GGML_LLAMAFILE OFF CACHE BOOL "Disable GGML llamafile on armeabi-v7a" FORCE)
        set(GGML_USE_LLAMAFILE OFF CACHE BOOL "" FORCE)
    endif()
endif()

# Add llama.cpp as a subdirectory (builds ggml, llama, common, etc)
add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama")

set(WHISPER_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../whisper.cpp")

# whisper.cpp options
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_BINARY_DIR}/whisper")

# stable-diffusion.cpp (repo-root submodule)
set(STABLE_DIFFUSION_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../stable-diffusion.cpp")
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(SD_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)

add_subdirectory("${STABLE_DIFFUSION_CPP_DIR}" "${CMAKE_BINARY_DIR}/stable-diffusion")

find_library(log-lib log)

add_library(
        llama_jni
        SHARED
        llama_jni.cpp
        llama_embed.cpp
        whisper_stt.cpp
        whisper_jni.cpp
        stable_diffusion_jni.cpp
)

target_include_directories(
        llama_jni
        PRIVATE
        ${CMAKE_SOURCE_DIR}/../c_interop/include
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/ggml/src
        ${LLAMA_CPP_DIR}/common
        # nlohmann headers live under llama.cpp/vendor in many revisions
        ${LLAMA_CPP_DIR}/vendor
        ${LLAMA_CPP_DIR}/vendor/nlohmann
        ${WHISPER_CPP_DIR}
        ${WHISPER_CPP_DIR}/include
        ${STABLE_DIFFUSION_CPP_DIR}/include
)

target_link_libraries(
        llama_jni
        PRIVATE
        llama
        common
        whisper
        stable-diffusion
        ${log-lib}
)
