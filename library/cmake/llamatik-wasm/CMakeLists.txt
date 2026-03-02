cmake_minimum_required(VERSION 3.16)
project(llamatik_wasm LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(EMSCRIPTEN)
  list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_LIST_DIR}/cmake")
endif()

get_filename_component(REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../.." ABSOLUTE)

set(LLAMA_CPP_DIR "${REPO_ROOT}/llama.cpp")
set(WHISPER_CPP_DIR "${REPO_ROOT}/whisper.cpp")
set(SD_CPP_DIR "${REPO_ROOT}/stable-diffusion.cpp")

if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
  message(FATAL_ERROR "llama.cpp not found at ${LLAMA_CPP_DIR}")
endif()

# ---- llama.cpp config ----
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama")

# ---- whisper.cpp optional ----
set(HAVE_WHISPER OFF)
if(EXISTS "${WHISPER_CPP_DIR}/CMakeLists.txt")
  set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
  set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
  add_subdirectory("${WHISPER_CPP_DIR}" "${CMAKE_BINARY_DIR}/whisper")
  set(HAVE_WHISPER ON)
else()
  add_compile_definitions(LLAMATIK_NO_WHISPER=1)
endif()

# ---- stable-diffusion.cpp optional ----
set(HAVE_SD OFF)
if(EXISTS "${SD_CPP_DIR}/CMakeLists.txt")
  set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
  set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
  set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
  set(SD_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)
  add_subdirectory("${SD_CPP_DIR}" "${CMAKE_BINARY_DIR}/stable-diffusion")
  set(HAVE_SD ON)
else()
  add_compile_definitions(LLAMATIK_NO_SD=1)
endif()

# ---- our wasm C-API wrapper ----
set(WRAPPER_SRC "${REPO_ROOT}/library/src/commonMain/cpp/llamatik_wasm_api.cpp")
add_executable(llamatik_wasm ${WRAPPER_SRC})

target_include_directories(llamatik_wasm PRIVATE
  "${LLAMA_CPP_DIR}/include"
  "${LLAMA_CPP_DIR}"
  "${LLAMA_CPP_DIR}/common"
  "${LLAMA_CPP_DIR}/vendor"
  "${LLAMA_CPP_DIR}/vendor/nlohmann"
)

target_link_libraries(llamatik_wasm PRIVATE llama)

if(HAVE_WHISPER)
  target_include_directories(llamatik_wasm PRIVATE "${WHISPER_CPP_DIR}" "${WHISPER_CPP_DIR}/include")
  if(TARGET whisper)
    target_link_libraries(llamatik_wasm PRIVATE whisper)
  endif()
endif()

if(HAVE_SD)
  target_include_directories(llamatik_wasm PRIVATE "${SD_CPP_DIR}/include")
  if(TARGET stable-diffusion)
    target_link_libraries(llamatik_wasm PRIVATE stable-diffusion)
  endif()
endif()

# ---- Emscripten output as ES module + wasm ----
set_target_properties(llamatik_wasm PROPERTIES
  OUTPUT_NAME "llamatik_wasm"
  SUFFIX ".mjs"
)

# wasm32 settings (NO MEMORY64) to avoid BigInt pointers in JS.
# Use memory growth + a sensible cap.
target_link_options(llamatik_wasm PRIVATE
  "-sMODULARIZE=1"
  "-sEXPORT_ES6=1"
  "-sENVIRONMENT=web"

  "-sALLOW_MEMORY_GROWTH=1"
  "-sINITIAL_MEMORY=1024MB"
  "-sMAXIMUM_MEMORY=2048MB"

  # Ensure FS exists
  "-sFORCE_FILESYSTEM=1"

  # Added: _llamatik_llama_generate_stream
  "-sEXPORTED_FUNCTIONS=[_malloc,_free,_llamatik_llama_init_generate,_llamatik_llama_generate,_llamatik_llama_generate_stream,_llamatik_free_string]"
  "-sEXPORTED_RUNTIME_METHODS=[ccall,cwrap,UTF8ToString,stringToUTF8,lengthBytesUTF8,FS]"
)
