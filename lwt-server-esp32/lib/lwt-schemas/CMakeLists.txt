include(${CMAKE_CURRENT_LIST_DIR}/flatbuffers_codegen.cmake)

idf_component_register(
    SRCS
        "src/lwt_schemas.cpp"
    INCLUDE_DIRS
        "include"
        "flatbuffers/include"
        "generated/include"
        ${LWT_SCHEMAS_GENERATED_INCLUDE_DIRS}
)

target_include_directories(${COMPONENT_LIB} PUBLIC
    "${CMAKE_CURRENT_LIST_DIR}/generated/include"
    "${CMAKE_CURRENT_LIST_DIR}/flatbuffers/include"
)