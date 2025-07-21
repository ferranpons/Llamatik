cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17")

add_subdirectory("${CMAKE_SOURCE_DIR}/../../../../llama.cpp/" "${CMAKE_BINARY_DIR}/llama")

add_definitions(
        -DINCLUDE_EXTRA_CMAKELISTS=ON
        -DANDROID_ABI=arm64-v8a
        -DANDROID_PLATFORM=android-28
        -DCMAKE_C_FLAGS="-march=armv8.7a"
        -DCMAKE_CXX_FLAGS="-march=armv8.7a"
        -DGGML_OPENMP=OFF
        -DGGML_LLAMAFILE=OFF
        -B build-android
)

add_library(llama_jni SHARED
        llama_jni.cpp
        llama_embed.cpp
        ${LLAMA_SOURCES}
)

find_library(log-lib log)

target_include_directories(llama_jni PRIVATE
        ../../../../library/src/main/res
        ${CMAKE_SOURCE_DIR}/../c_interop/include  # location of llama.h
        ${LLAMA_SOURCES}
)
target_link_libraries(
        llama_jni
        llama
        ${log-lib}
)
