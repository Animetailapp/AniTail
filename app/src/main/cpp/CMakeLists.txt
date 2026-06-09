cmake_minimum_required(VERSION 3.22)
project("anitail_discord")

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(
        anitail_discord
        SHARED
        discord_bridge.cpp
)

target_include_directories(
        anitail_discord
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
        anitail_discord
        discord_partner_sdk::discord_partner_sdk
        jnigraphics
        log
)

target_compile_features(
        anitail_discord
        PUBLIC
        cxx_std_17
)