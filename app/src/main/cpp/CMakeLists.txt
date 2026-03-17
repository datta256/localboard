cmake_minimum_required(VERSION 3.22.1)

project(localboard)

# --- WINDOWS BUILD STABILITY FIX ---
# Force-disable all modules that use FetchContent or ExternalProject_Add
# to avoid the "URL is a path" error on Windows.
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_SERVER OFF CACHE BOOL "" FORCE)

# --- PERFORMANCE TUNING ---
# We use NEON and DotProd which are standard and extremely fast on ARMv8.2+
set(GGML_NEON ON CACHE BOOL "" FORCE)
set(LLAMA_FAST ON CACHE BOOL "" FORCE)

# 1. Add llama.cpp
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
add_subdirectory(${LLAMA_DIR} llama_build)

# 2. Build our JNI bridge
add_library(
        localboard
        SHARED
        native-lib.cpp
)

# 3. Link dependencies
target_link_libraries(
        localboard
        llama
        android
        log
)

# 4. ARM Hardware Acceleration
if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    target_compile_options(localboard PRIVATE
            -O3
            -march=armv8.2-a+dotprod+crypto+fp16
    )
    add_definitions(-D__ARM_FEATURE_FP16_VECTOR_ARITHMETIC)
    add_definitions(-D__ARM_NEON)
endif()