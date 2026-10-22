#!/usr/bin/env bash
# Builds a static OpenSSL (libcrypto only) for every Android ABI Matinee ships, into app/src/main/cpp/openssl/<abi>.
# Usage: scripts/build-openssl.sh <path to an unpacked OpenSSL 3.0.x source tree>
set -euo pipefail

SRC=${1:?path to OpenSSL source}
NDK=${ANDROID_NDK_HOME:-$(ls -d "$HOME"/Library/Android/sdk/ndk/* 2>/dev/null | tail -1)}
OUT="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/cpp/openssl"
HOST=$(ls "$NDK/toolchains/llvm/prebuilt")
export PATH="$NDK/toolchains/llvm/prebuilt/$HOST/bin:$PATH"
export ANDROID_NDK_ROOT="$NDK"
MIN_API=26
JOBS=$(sysctl -n hw.ncpu 2>/dev/null || nproc)

build() { # abi openssl-target
  local abi=$1 target=$2 work
  work=$(mktemp -d)
  cp -R "$SRC/." "$work"
  (
    cd "$work"
    ./Configure "$target" -D__ANDROID_API__=$MIN_API no-shared no-tests no-comp no-asm \
      --prefix="$OUT/$abi" --openssldir=/dev/null
    make -j"$JOBS" build_libs
  )
  mkdir -p "$OUT/$abi/lib" "$OUT/include"
  cp "$work/libcrypto.a" "$OUT/$abi/lib/"
  cp -R "$work/include/openssl" "$OUT/include/"
  rm -rf "$work"
}

build arm64-v8a android-arm64
build armeabi-v7a android-arm
build x86_64 android-x86_64
