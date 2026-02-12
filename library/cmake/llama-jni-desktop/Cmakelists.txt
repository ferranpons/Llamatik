cmake_minimum_required(VERSION 3.10)
project(llama_jni_desktop LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# -------------------------------
# Paths
# -------------------------------
get_filename_component(REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)

set(LLAMA_CPP_DIR "${REPO_ROOT}/llama.cpp")
set(LLAMA_INCLUDE_DIR "${LLAMA_CPP_DIR}/include")

set(JNI_SRC "${REPO_ROOT}/library/src/commonMain/cpp/llama_jni.cpp")
set(JNI_INCLUDE_DIR "${REPO_ROOT}/library/src/commonMain/c_interop/include")

# These MUST be absolute or CMake will look in the cmake folder
set(WHISPER_STT_SRC "${REPO_ROOT}/library/src/commonMain/cpp/whisper_stt.cpp")
set(WHISPER_JNI_SRC "${REPO_ROOT}/library/src/commonMain/cpp/whisper_jni.cpp")

# Prefer repo-root whisper.cpp
set(WHISPER_CPP_DIR "${REPO_ROOT}/whisper.cpp")

message(STATUS "REPO_ROOT        = ${REPO_ROOT}")
message(STATUS "LLAMA_CPP_DIR    = ${LLAMA_CPP_DIR}")
message(STATUS "LLAMA_INCLUDE_DIR= ${LLAMA_INCLUDE_DIR}")
message(STATUS "JNI_SRC          = ${JNI_SRC}")
message(STATUS "JNI_INCLUDE_DIR  = ${JNI_INCLUDE_DIR}")
message(STATUS "WHISPER_CPP_DIR  = ${WHISPER_CPP_DIR}")
message(STATUS "WHISPER_STT_SRC  = ${WHISPER_STT_SRC}")
message(STATUS "WHISPER_JNI_SRC  = ${WHISPER_JNI_SRC}")

if(NOT EXISTS "${JNI_SRC}")
    message(FATAL_ERROR "JNI source not found: ${JNI_SRC}")
endif()

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at: ${LLAMA_CPP_DIR}")
endif()

# -------------------------------
# Build llama.cpp (from repo)
# -------------------------------
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama-wrapper-build")

# -------------------------------
# Whisper (optional but enabled if present)
# -------------------------------
set(HAVE_WHISPER OFF)

if(EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
    set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)

    add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_BINARY_DIR}/whisper")
    set(HAVE_WHISPER ON)
else()
    message(WARNING "whisper.cpp not found at ${WHISPER_CPP_DIR}. Desktop will build without Whisper.")
    add_compile_definitions(LLAMATIK_NO_WHISPER=1)
endif()

# -------------------------------
# JNI
# -------------------------------
find_package(JNI REQUIRED)

# schema->grammar helper sources (optional)
set(LLAMA_SCHEMA_SOURCES "")
set(_cand1 "${LLAMA_CPP_DIR}/common/json-schema-to-grammar.cpp")
set(_cand2 "${LLAMA_CPP_DIR}/common/json_schema_to_grammar.cpp")
set(_cand3 "${LLAMA_CPP_DIR}/common/json-schema.cpp")

foreach(f IN LISTS _cand1 _cand2 _cand3)
    if(EXISTS "${f}")
        list(APPEND LLAMA_SCHEMA_SOURCES "${f}")
    endif()
endforeach()

if(LLAMA_SCHEMA_SOURCES STREQUAL "")
    message(WARNING "No schema-to-grammar .cpp sources found in llama.cpp/common. Will rely on llama.cpp 'common' target if it exists.")
else()
    message(STATUS "Schema sources: ${LLAMA_SCHEMA_SOURCES}")
endif()

# Collect sources
set(LLAMA_JNI_SOURCES
    "${JNI_SRC}"
    ${LLAMA_SCHEMA_SOURCES}
)

# Only add whisper glue sources if they exist AND whisper is enabled
if(HAVE_WHISPER)
    if(NOT EXISTS "${WHISPER_STT_SRC}")
        message(FATAL_ERROR "Whisper glue source not found: ${WHISPER_STT_SRC}")
    endif()
    if(NOT EXISTS "${WHISPER_JNI_SRC}")
        message(FATAL_ERROR "Whisper glue source not found: ${WHISPER_JNI_SRC}")
    endif()

    list(APPEND LLAMA_JNI_SOURCES
        "${WHISPER_STT_SRC}"
        "${WHISPER_JNI_SRC}"
    )
endif()

add_library(llama_jni SHARED ${LLAMA_JNI_SOURCES})

target_include_directories(llama_jni
    PRIVATE
    "${LLAMA_INCLUDE_DIR}"
    "${LLAMA_CPP_DIR}/common"
    "${LLAMA_CPP_DIR}/vendor"
    "${LLAMA_CPP_DIR}/vendor/nlohmann"
    "${JNI_INCLUDE_DIR}"
    "${JNI_INCLUDE_DIR}/.."
    ${JNI_INCLUDE_DIRS}
)

if(HAVE_WHISPER)
    target_include_directories(llama_jni
        PRIVATE
        "${WHISPER_CPP_DIR}"
        "${WHISPER_CPP_DIR}/include"
    )
endif()

target_link_libraries(llama_jni
    PRIVATE
    llama
)

if(HAVE_WHISPER)
    target_link_libraries(llama_jni PRIVATE whisper)
endif()

if(TARGET common)
    target_link_libraries(llama_jni PRIVATE common)
    message(STATUS "Linking llama.cpp target: common")
endif()

set_target_properties(llama_jni PROPERTIES OUTPUT_NAME "llama_jni")