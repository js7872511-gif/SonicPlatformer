cmake_minimum_required(VERSION 3.22.1)
project(reborn_runtime LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(main SHARED main_wrapper.cpp)
set_target_properties(main PROPERTIES OUTPUT_NAME "main")
target_link_libraries(main log dl)

add_library(reborn SHARED
        reborn_runtime.cpp)
set_target_properties(reborn PROPERTIES OUTPUT_NAME "reborn")
target_link_libraries(reborn log dl)
