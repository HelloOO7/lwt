# This file was automatically generated for projects
# without default 'CMakeLists.txt' file.

FILE(GLOB_RECURSE app_sources ${CMAKE_SOURCE_DIR}/src/*.*)

idf_component_register(
    SRCS ${app_sources}
    REQUIRES common vdv301 lwdn lwt-schemas bt esp_wifi esp_eth nvs_flash
)

target_compile_options(${COMPONENT_LIB} PRIVATE -Wno-missing-field-initializers -Wno-error=missing-field-initializers -Wno-deprecated-declarations)

target_add_binary_data(${COMPONENT_TARGET} ${CMAKE_SOURCE_DIR}/../pki/LWT_Server_debug_key.key TEXT)
target_add_binary_data(${COMPONENT_TARGET} ${CMAKE_SOURCE_DIR}/../pki/esp32-alice.crt TEXT)
target_add_binary_data(${COMPONENT_TARGET} ${CMAKE_SOURCE_DIR}/../pki/esp32-bob.crt TEXT)
target_add_binary_data(${COMPONENT_TARGET} ${CMAKE_SOURCE_DIR}/../pki/Ticket_signing_key_pub.pem TEXT)
