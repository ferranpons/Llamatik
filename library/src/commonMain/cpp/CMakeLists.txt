cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set compile flags for newer arm
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.7-a")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.7-a")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--section-start=.note.gnu.build-id=0x4000 -Wl,--no-rosegment")

add_subdirectory("${CMAKE_SOURCE_DIR}/../../../../llama.cpp/" "${CMAKE_BINARY_DIR}/llama")

add_library(llama_jni SHARED
        llama_jni.cpp
        llama_embed.cpp
        ${LLAMA_SOURCES}
)

find_library(log-lib log)

target_include_directories(llama_jni PRIVATE
        ../../../../library/src/main/res
        ${CMAKE_SOURCE_DIR}/../c_interop/include
        ${LLAMA_SOURCES}
)

target_link_libraries(
        llama_jni
        llama
        ${log-lib}
)