cmake_minimum_required(VERSION 3.22)
project(matinee_airplay C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# OpenSSL is built by scripts/build-openssl.sh.
set(OPENSSL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/openssl)
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES
    IMPORTED_LOCATION ${OPENSSL_DIR}/${ANDROID_ABI}/lib/libcrypto.a
    INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_DIR}/include)

file(GLOB PLIST_SOURCES libplist/src/*.c libplist/libcnary/*.c)
list(FILTER PLIST_SOURCES EXCLUDE REGEX "cnary\\.c$")
add_library(plist STATIC ${PLIST_SOURCES})
target_include_directories(plist PUBLIC libplist/include PRIVATE libplist/src libplist/libcnary/include android/plist-config)
target_compile_definitions(plist PRIVATE HAVE_CONFIG_H=1 HAVE_STRNDUP=1 HAVE_MEMMEM=1 HAVE_GMTIME_R=1 HAVE_LOCALTIME_R=1
    HAVE_STRPTIME=1 HAVE_TM_TM_GMTOFF=1 HAVE_TM_TM_ZONE=1 HAVE_ATTRIBUTE_CONSTRUCTOR=1 PACKAGE_VERSION="2.6.0")

file(GLOB AIRPLAY_SOURCES uxplay/*.c uxplay/playfair/*.c uxplay/llhttp/*.c)
list(APPEND AIRPLAY_SOURCES uxplay/dns_sd/dns_sd.c android/dns_sd_android.c)
add_library(airplay STATIC ${AIRPLAY_SOURCES})
target_include_directories(airplay PUBLIC uxplay uxplay/playfair uxplay/llhttp android)
target_compile_definitions(airplay PRIVATE NOHOLD STANDALONE=1 TARGET_POSIX _LINUX _REENTRANT PLIST_210 PLIST_230)
target_compile_options(airplay PRIVATE -w)
target_compile_options(plist PRIVATE -w)
target_link_libraries(airplay PUBLIC plist crypto)

file(GLOB ALAC_SOURCES alac/*.c alac/*.cpp)
add_library(matinee_airplay SHARED android/airplay_jni.cpp android/alac_jni.cpp ${ALAC_SOURCES})
target_include_directories(matinee_airplay PRIVATE alac)
target_compile_options(matinee_airplay PRIVATE -Wno-everything)
target_link_libraries(matinee_airplay airplay log)
# Page-aligned segments for Android 15+ devices with 16 KB pages.
target_link_options(matinee_airplay PRIVATE -Wl,-z,max-page-size=16384)
