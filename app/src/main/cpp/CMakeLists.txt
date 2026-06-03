cmake_minimum_required(VERSION 3.22)
project("anitail_discord")

add_library(anitail_discord SHARED
    discord_bridge.cpp
)

target_include_directories(anitail_discord PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(discord_partner_sdk SHARED IMPORTED)
set_target_properties(discord_partner_sdk PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libdiscord_partner_sdk.so
)

target_link_libraries(anitail_discord
    discord_partner_sdk
    jnigraphics
    log
)

target_compile_features(anitail_discord PUBLIC cxx_std_17)
