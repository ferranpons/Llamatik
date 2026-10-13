# FindThreads.cmake (Emscripten override)
#
# ggml does: find_package(Threads REQUIRED)
# CMake's stock FindThreads fails under emcc because pthread checks do not apply.
#
# We provide a "no-op" Threads package that satisfies consumers expecting:
#   - Threads_FOUND
#   - Threads::Threads target
#
# IMPORTANT: This is only intended for wasm builds (single-threaded).

set(Threads_FOUND TRUE)

if(NOT TARGET Threads::Threads)
  add_library(Threads::Threads INTERFACE IMPORTED)
endif()

# Common variables FindThreads sets:
set(CMAKE_THREAD_LIBS_INIT "")

# Some projects look for these too:
set(CMAKE_USE_PTHREADS_INIT 0)
set(CMAKE_USE_WIN32_THREADS_INIT 0)
set(CMAKE_USE_SPROC_INIT 0)