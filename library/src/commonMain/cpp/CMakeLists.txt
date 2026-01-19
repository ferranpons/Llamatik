cmake_minimum_required(VERSION 3.22)
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

# Add llama.cpp as a subdirectory (builds ggml, llama, common, etc)
add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama")

find_library(log-lib log)

add_library(
        llama_jni
        SHARED
        llama_jni.cpp
        llama_embed.cpp
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
)

target_link_libraries(
        llama_jni
        PRIVATE
        llama
        common
        ${log-lib}
)
