cmake_minimum_required(VERSION 3.22)
project(HighResNative)

add_library(HighResNative SHARED native_helper.cpp)
target_link_libraries(HighResNative android log)
