cmake_minimum_required(VERSION 3.22)
project(HighResNative)

add_library(HighResNative SHARED native_helper.cpp)
target_link_libraries(HighResNative android log)

add_executable(set_render_level native_helper.cpp)
target_compile_definitions(set_render_level PRIVATE STANDALONE_EXE)
target_link_libraries(set_render_level android log dl)
