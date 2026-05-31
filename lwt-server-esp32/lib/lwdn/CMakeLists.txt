file(GLOB LWDN_SRCS
    "${CMAKE_CURRENT_LIST_DIR}/src/*.cpp"
)

idf_component_register(
    SRCS
        ${LWDN_SRCS}
    INCLUDE_DIRS
        "include"
    REQUIRES common bt mbedtls
)
