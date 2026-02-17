cmake_minimum_required(VERSION 3.18)
project(anitail-native)

option(ENABLE_VIBRAFP "Build vibra_fp native library" OFF)

add_subdirectory(coverart)

if(ENABLE_VIBRAFP)
    add_subdirectory(vibrafp/lib)
endif()
