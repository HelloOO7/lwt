cmake_minimum_required(VERSION 3.16.0)
list(APPEND EXTRA_COMPONENT_DIRS "${CMAKE_SOURCE_DIR}/lib/common" "${CMAKE_SOURCE_DIR}/lib/vdv301" "${CMAKE_SOURCE_DIR}/lib/lwdn" "${CMAKE_SOURCE_DIR}/lib/lwt-schemas")
include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(lwt-server-esp32)
