# This file was automatically generated for projects
# without default 'CMakeLists.txt' file.

FILE(GLOB_RECURSE app_sources ${CMAKE_SOURCE_DIR}/src/*.*)

idf_component_register(
    SRCS ${app_sources}
    REQUIRES common vdv301 lwdn lwt-schemas bt
    EMBED_TXTFILES "${CMAKE_SOURCE_DIR}/../pki/LWT_Server_debug_key.key"
                   "${CMAKE_SOURCE_DIR}/../pki/esp32-alice.crt"
)
