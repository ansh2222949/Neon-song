cmake_minimum_required(VERSION 3.22.1)
project(neonsong_audio LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(POLICY CMP0169)
    cmake_policy(SET CMP0169 OLD)
endif()

# Support 16 KB page sizes on Android 15+ (e.g. Galaxy S25 series)
# Android 15+ devices may run 16 KB memory-page kernels. Shared libraries
# that are only 4 KB ELF-aligned trigger loader compatibility warnings (or
# fail outright) on such devices. Directory-wide so the bundled soxr shared
# library inherits the same alignment.
add_link_options("-Wl,-z,max-page-size=16384")

find_package(oboe REQUIRED CONFIG)

# libsoxr 0.1.3 is pinned for reproducible HQ sinc resampling. It is built as
# a separate LGPL shared library rather than statically folded into the app.
include(FetchContent)
FetchContent_Declare(
    soxr_source
    URL https://github.com/chirlu/soxr/archive/refs/tags/0.1.3.tar.gz
    URL_HASH SHA256=db6ca1b1e8405c6ef92f8294fc123d910abf0a114003b3f0f13fa57a95fd62d0
)
FetchContent_GetProperties(soxr_source)
if(NOT soxr_source_POPULATED)
    FetchContent_Populate(soxr_source)
    if(EXISTS "${soxr_source_SOURCE_DIR}/CMakeLists.txt")
        file(READ "${soxr_source_SOURCE_DIR}/CMakeLists.txt" SOXR_CMAKE)
        string(REPLACE
            "EXISTS \${PROJECT_SOURCE_DIR}/lsr-tests"
            "EXISTS \"\${PROJECT_SOURCE_DIR}/lsr-tests\""
            SOXR_CMAKE
            "${SOXR_CMAKE}"
        )
        string(REPLACE
            "add_subdirectory (tests)"
            "# add_subdirectory (tests)"
            SOXR_CMAKE
            "${SOXR_CMAKE}"
        )
        string(REPLACE
            "add_subdirectory (lsr-tests)"
            "# add_subdirectory (lsr-tests)"
            SOXR_CMAKE
            "${SOXR_CMAKE}"
        )
        string(REPLACE
            "add_subdirectory (examples)"
            "# add_subdirectory (examples)"
            SOXR_CMAKE
            "${SOXR_CMAKE}"
        )
        file(WRITE "${soxr_source_SOURCE_DIR}/CMakeLists.txt" "${SOXR_CMAKE}")
    endif()
    set(_PREV_CMAKE_MODULE_PATH "${CMAKE_MODULE_PATH}")
    set(_PREV_POLICY_MIN "${CMAKE_POLICY_VERSION_MINIMUM}")
    set(CMAKE_POLICY_VERSION_MINIMUM "3.5")
    list(APPEND CMAKE_MODULE_PATH "${soxr_source_SOURCE_DIR}/cmake/Modules")
    set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(BUILD_LSR_TESTS OFF CACHE BOOL "" FORCE)
    set(WITH_LSR_BINDINGS OFF CACHE BOOL "" FORCE)
    set(WITH_OPENMP OFF CACHE BOOL "" FORCE)
    set(WITH_CR32S OFF CACHE BOOL "" FORCE)
    set(WITH_CR64S OFF CACHE BOOL "" FORCE)
    set(WITH_PFFFT OFF CACHE BOOL "" FORCE)
    set(_PREV_BUILD_SHARED_LIBS "${BUILD_SHARED_LIBS}")
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    add_subdirectory("${soxr_source_SOURCE_DIR}" "${soxr_source_BINARY_DIR}" EXCLUDE_FROM_ALL)
    set(BUILD_SHARED_LIBS "${_PREV_BUILD_SHARED_LIBS}" CACHE BOOL "" FORCE)
    set(CMAKE_MODULE_PATH "${_PREV_CMAKE_MODULE_PATH}")
    set(CMAKE_POLICY_VERSION_MINIMUM "${_PREV_POLICY_MIN}")

    # Android APKs need an unversioned DT_SONAME/file name for reliable
    # packaging and namespace loading of transitive native dependencies.
    set_target_properties(soxr PROPERTIES VERSION "" SOVERSION "")
    target_compile_options(
        soxr
        PRIVATE
        -fno-math-errno
        -fno-trapping-math
        -fno-signed-zeros
        -ffp-contract=fast
        $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-O3>
    )
endif()

add_library(
    neonsong_audio
    SHARED
    AudioEngine.cpp
    DspProcessor.cpp
    AutoMixEngine.cpp
    AutoMixBridge.cpp
    AutoDJProcessor.cpp
    AutoDJBridge.cpp
    WsolaTimeStretcher.cpp
    BpmPhaseDetector.cpp
    ParametricEq.cpp
    ParametricEqBridge.cpp
    NativeBridge.cpp
)

target_include_directories(
    neonsong_audio
    PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
)
target_include_directories(
    neonsong_audio
    SYSTEM PRIVATE
    "${soxr_source_SOURCE_DIR}/src"
)

target_compile_options(
    neonsong_audio
    PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -Wconversion
    -Wshadow
    -fvisibility=hidden
    $<$<COMPILE_LANGUAGE:CXX>:-fvisibility-inlines-hidden>
    -ffunction-sections
    -fdata-sections
    -fno-math-errno
    -fno-trapping-math
    -fno-signed-zeros
    -ffp-contract=fast
    $<$<OR:$<CONFIG:Release>,$<CONFIG:RelWithDebInfo>>:-O3>
)

target_link_options(
    neonsong_audio
    PRIVATE
    -Wl,--exclude-libs,ALL
    $<$<CONFIG:Release>:-Wl,--gc-sections>
    $<$<CONFIG:Release>:-Wl,--icf=safe>
)

include(CheckIPOSupported)
check_ipo_supported(RESULT _LTO_SUPPORTED OUTPUT _LTO_ERROR)
if(_LTO_SUPPORTED)
    set_target_properties(
        neonsong_audio
        PROPERTIES
        INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE
    )
endif()

target_link_libraries(
    neonsong_audio
    PRIVATE
    oboe::oboe
    soxr
    android
    log
)
