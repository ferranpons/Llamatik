cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# REMOVE these (they enabled advanced ARM features and caused SIGILL)
# set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.7-a")
# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.7-a")

if(ANDROID)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

set(CMAKE_SHARED_LINKER_FLAGS
        "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--section-start=.note.gnu.build-id=0x4000 -Wl,--no-rosegment"
)

# (Optional) If supported by your ggml version:
option(GGML_CPU_ARM_USE_DOTPROD "Enable ARM dot product" OFF)
option(GGML_CPU_ARM_USE_I8MM    "Enable ARM i8mm"       OFF)

include(FetchContent)
FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG 0bf47a1dbba4d36f2aff4e8c34b06210ba34e688
)
FetchContent_MakeAvailable(llama_cpp)

add_library(llama_jni SHARED
        llama_jni.cpp
        llama_embed.cpp
        ${LLAMA_SOURCES}
)

find_library(log-lib log)

target_include_directories(llama_jni PRIVATE
        ${CMAKE_SOURCE_DIR}/../c_interop/include
)

target_link_libraries(llama_jni llama ${log-lib})

# ---- SAFE FLAGS ON ARM64 (pick ONE block) ----
# Option A: baseline ARMv8-A
if (CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|ARM64|arm64")
    foreach(tgt IN ITEMS llama llama_jni ggml ggml-base ggml-cpu)
        if (TARGET ${tgt})
            target_compile_options(${tgt} PRIVATE -march=armv8-a)
        endif()
    endforeach()
endif()

# Option B (alternative): strict old core
# if (CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|ARM64|arm64")
#   foreach(tgt IN ITEMS llama llama_jni ggml ggml-base ggml-cpu)
#     if (TARGET ${tgt})
#       target_compile_options(${tgt} PRIVATE -mcpu=cortex-a53)
#     endif()
#   endforeach()
# endif()