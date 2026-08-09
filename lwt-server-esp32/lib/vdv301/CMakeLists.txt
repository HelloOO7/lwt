include(${CMAKE_CURRENT_LIST_DIR}/xsd/CMakeLists.txt)

file(GLOB VDV301_SRCS
    "${CMAKE_CURRENT_LIST_DIR}/src/*.cpp"
)

idf_component_register(
    SRCS
        ${VDV301_SRCS}
        ${VDV301_XSD_SRCS}
    INCLUDE_DIRS
        "include"
        ${VDV301_XSD_INCLUDE_DIRS}
    REQUIRES common mdns esp_http_client esp_http_server
)

target_compile_options(${COMPONENT_LIB} PRIVATE -Wno-missing-field-initializers -Wno-implicit-fallthrough -Wno-error=missing-field-initializers -Wno-error=implicit-fallthrough)