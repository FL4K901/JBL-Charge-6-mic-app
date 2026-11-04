cmake_minimum_required(VERSION 3.22.1)
project("mic-audio-engine" CXX)

find_package(oboe REQUIRED CONFIG)

add_library(mic-audio-engine SHARED
    OboeAudioEngine.cpp
    native-lib.cpp
)

target_include_directories(mic-audio-engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_features(mic-audio-engine PRIVATE cxx_std_17)

target_link_libraries(mic-audio-engine
    PRIVATE
    oboe::oboe
    log
    android
)
