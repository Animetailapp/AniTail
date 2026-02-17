cmake_minimum_required(VERSION 3.18)
project(anitail-native)

option(ENABLE_VIBRAFP "Build vibra_fp native library" OFF)

add_subdirectory(coverart)

if(ANDROID)
    # Bento4 defaults to fseeko/ftello, unavailable on Android API 23.
    # Force portable stdio seeks so x86/minSdk23 builds succeed.
    if(TARGET ap4)
        target_compile_definitions(ap4 PRIVATE AP4_fseek=fseek AP4_ftell=ftell)
    endif()
    if(TARGET coverart)
        target_compile_definitions(coverart PRIVATE AP4_fseek=fseek AP4_ftell=ftell)
    endif()
endif()

if(ENABLE_VIBRAFP)
    add_subdirectory(vibrafp/lib)
endif()
