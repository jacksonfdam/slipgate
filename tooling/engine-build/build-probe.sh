#!/usr/bin/env bash
# Builds the exception handling probe and records its hash.
#
# The probe answers the question the whole engine port rests on — whether Chasm can run a module
# built with wasm exceptions and wasm longjmp — so it is built the same way the engines will be.
set -euo pipefail

WORKDIR="$(cd "$(dirname "$0")" && pwd)"
EMSDK_DIR="${WORKDIR}/.toolchain/emsdk"
SOURCE="${WORKDIR}/verification/longjmp_probe.c"
OUTPUT="${WORKDIR}/../../host/backend/wasm/src/jvmTest/resources/longjmp_probe.wasm"

# Emscripten needs Python 3.10 or newer, and macOS ships 3.9 through the Xcode tools. Setting this
# is the difference between a working build and an error that reads like the SDK is broken.
export EMSDK_PYTHON="${EMSDK_PYTHON:-$(command -v python3.13 || command -v python3.12 || command -v python3)}"

if [ ! -d "${EMSDK_DIR}" ]; then
    echo "error: the Emscripten SDK is missing; see tooling/engine-build/README.md" >&2
    exit 1
fi

# shellcheck disable=SC1091
source "${EMSDK_DIR}/emsdk_env.sh" > /dev/null 2>&1

# WASM_LEGACY_EXCEPTIONS=0 is not optional. Emscripten still emits the legacy exception handling
# opcodes by default, and Chasm implements the final proposal, so a module built without this flag
# fails to decode with UnknownInstruction(byte=6) before a single instruction runs.
emcc "${SOURCE}" \
    -o "${OUTPUT}" \
    -fwasm-exceptions \
    -sSUPPORT_LONGJMP=wasm \
    -sWASM_LEGACY_EXCEPTIONS=0 \
    -sSTANDALONE_WASM \
    --no-entry \
    -Oz

printf 'built %s\n' "${OUTPUT}" >&2
if command -v shasum > /dev/null 2>&1; then
    shasum -a 256 "${OUTPUT}" | awk '{ print "sha256 =", $1 }' >&2
fi
