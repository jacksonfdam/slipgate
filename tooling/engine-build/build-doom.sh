#!/usr/bin/env bash
# Builds the Doom gate's WebAssembly module from the pinned Chocolate Doom sources.
#
# The upstream tree is never patched. Everything platform-specific comes from platform/, which
# replaces the i_* layer, and the shared sources that still reach for SDL get a shim header rather
# than an edit.
set -euo pipefail

WORKDIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${WORKDIR}/.sources/chocolate-doom/src"
PLATFORM_DIR="${WORKDIR}/platform"
EMSDK_DIR="${WORKDIR}/.toolchain/emsdk"
OUTPUT="${1:-${WORKDIR}/.build/mars.wasm}"

# Emscripten needs Python 3.10 or newer, and macOS ships 3.9 through the Xcode tools.
export EMSDK_PYTHON="${EMSDK_PYTHON:-$(command -v python3.13 || command -v python3.12 || command -v python3)}"

if [ ! -d "${SOURCE_DIR}" ]; then
    echo "error: engine sources are missing; run fetch-sources.sh first" >&2
    exit 1
fi

if [ ! -d "${EMSDK_DIR}" ]; then
    echo "error: the Emscripten SDK is missing; see README.md" >&2
    exit 1
fi

# shellcheck disable=SC1091
source "${EMSDK_DIR}/emsdk_env.sh" > /dev/null 2>&1

# The files that talk to SDL, a window system, a sound card or the text-mode setup screen. Each has
# either a replacement in platform/ or no place in a gate, and leaving them out is what makes the
# port a layer rather than a fork. d_dedicated.c is the standalone server's entry point and brings
# its own D_DoomMain and zone stubs, which is why it cannot be in a build that has the game's.
# z_native.c is the malloc-backed zone, an alternative to z_zone.c rather than a companion, and
# w_file_win32.c is the Windows file backend, and w_file_stdc.c is replaced by the memory-backed
# one in platform/, because a wasm module's filesystem is inside the module and the host cannot put
# a file into it from outside.
REPLACED_SOURCES=(
    d_dedicated.c
    i_cdmus.c
    i_endoom.c
    i_flmusic.c
    i_input.c
    i_joystick.c
    i_main.c
    i_musicpack.c
    i_oplmusic.c
    i_pcsound.c
    i_sdlmusic.c
    i_sdlsound.c
    i_sound.c
    i_system.c
    i_timer.c
    i_video.c
    i_videohr.c
    net_gui.c
    net_sdl.c
    w_file_stdc.c
    w_file_win32.c
    z_native.c
)

is_replaced() {
    local candidate="$1"
    for replaced in "${REPLACED_SOURCES[@]}"; do
        [ "${candidate}" = "${replaced}" ] && return 0
    done
    return 1
}

shared_sources=()
while read -r file; do
    is_replaced "$(basename "${file}")" || shared_sources+=("${file}")
done < <(find "${SOURCE_DIR}" -maxdepth 1 -name '*.c' | sort)

doom_sources=()
while read -r file; do
    doom_sources+=("${file}")
done < <(find "${SOURCE_DIR}/doom" -maxdepth 1 -name '*.c' | sort)

platform_sources=()
while read -r file; do
    platform_sources+=("${file}")
done < <(find "${PLATFORM_DIR}" -maxdepth 1 -name '*.c' | sort)

mkdir -p "$(dirname "${OUTPUT}")"

# WASM_LEGACY_EXCEPTIONS=0 is not optional: Emscripten still emits the legacy exception opcodes by
# default and Chasm implements the final proposal, so a module built without it fails to decode
# before a single instruction runs. host/backend/wasm carries the probe that proves it.
#
# INITIAL_MEMORY covers Doom's zone plus its WAD; Hexen will need more, and that number belongs in
# its own build script rather than being shared and guessed.
emcc \
    "${shared_sources[@]}" \
    "${doom_sources[@]}" \
    "${platform_sources[@]}" \
    -o "${OUTPUT}" \
    -I "${PLATFORM_DIR}/include" \
    -I "${PLATFORM_DIR}" \
    -I "${SOURCE_DIR}" \
    -I "${SOURCE_DIR}/doom" \
    -DSLIPGATE=1 \
    -fwasm-exceptions \
    -sSUPPORT_LONGJMP=wasm \
    -sWASM_LEGACY_EXCEPTIONS=0 \
    -sSTANDALONE_WASM \
    -sINITIAL_MEMORY=67108864 \
    -sALLOW_MEMORY_GROWTH=1 \
    -sERROR_ON_UNDEFINED_SYMBOLS=1 \
    -Wl,--wrap=M_FileCaseExists \
    --no-entry \
    -Oz \
    -Wno-unused-command-line-argument

printf 'built %s\n' "${OUTPUT}" >&2
if command -v shasum > /dev/null 2>&1; then
    shasum -a 256 "${OUTPUT}" | awk '{ print "sha256 =", $1 }' >&2
fi
