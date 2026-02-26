cmake_minimum_required(VERSION 3.22.1)
project("pulsenetwork")

# llama.cpp 源文件
set(LLAMA_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src/ggml.c
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src/ggml-alloc.c
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src/ggml-backend.c
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src/ggml-quants.c
)

# 编译 libllama
add_library(llama SHARED ${LLAMA_SOURCES})
target_include_directories(llama PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src
)
target_compile_definitions(llama PRIVATE
    LLAMA_NATIVE=OFF
    LLAMA_BUILD_EXAMPLES=OFF
)

# whisper.cpp 源文件
set(WHISPER_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml.c
)

# 编译 libwhisper
add_library(whisper SHARED ${WHISPER_SOURCES})
target_include_directories(whisper PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp
)
target_compile_definitions(whisper PRIVATE
    WHISPER_NATIVE=OFF
    WHISPER_BUILD_EXAMPLES=OFF
)

# JNI 桥接库
add_library(pulsenative SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/jni/llama_jni.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/jni/whisper_jni.cpp
)

target_include_directories(pulsenative PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

# 链接库
target_link_libraries(pulsenative
    llama
    whisper
    android
    log
    m
)
