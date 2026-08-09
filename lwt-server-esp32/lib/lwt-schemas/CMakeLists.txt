include(${CMAKE_CURRENT_LIST_DIR}/flatbuffers_codegen.cmake)

file(GLOB LWT_SCHEMAS_SRCS
    "${CMAKE_CURRENT_LIST_DIR}/src/*.cpp"
)

idf_component_register(
    SRCS
        ${LWT_SCHEMAS_SRCS}
    INCLUDE_DIRS
        "include"
        "flatbuffers/include"
        "generated/include"
        ${LWT_SCHEMAS_GENERATED_INCLUDE_DIRS}
    REQUIRES
        common
)

target_include_directories(${COMPONENT_LIB} PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/generated/include"
    "${CMAKE_CURRENT_LIST_DIR}/flatbuffers/include"
)