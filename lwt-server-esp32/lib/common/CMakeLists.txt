file(GLOB COMMON_SRCS
    "${CMAKE_CURRENT_LIST_DIR}/src/*.cpp"
)

idf_component_register(
    SRCS ${COMMON_SRCS}
    INCLUDE_DIRS "include"
    REQUIRES mbedtls
)