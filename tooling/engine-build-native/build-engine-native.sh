#!/usr/bin/env bash
# Builds one gate's native shared library from the same pinned sources and the same platform layer
# as the wasm module — ../engine-build owns both; nothing here duplicates them. The exported
# surface is identical to the module's (nm shows the same slipgate_* names), which is what lets
# host/runtime see one shape regardless of backend.
#
# Targets:
#   android-arm64    libSG_LIB.so for arm64-v8a, API 24, 16 KB pages
#   android-x86_64   libSG_LIB.so for x86_64 (the emulator), API 24
#   host             libSG_LIB.dylib (or .so) for the build machine, which is what the JVM
#                    parity and determinism tests load headless
#
# Called through a per-game script, like build-engine.sh. The stdio reroute the wasm link does
# with --wrap is done here by force-including sg_stdio_redirect.h into every compilation unit
# except sg_files.c — see that header for why.
set -euo pipefail

: "${SG_GAME:?SG_GAME must name a directory under src/ and under platform/}"
: "${SG_LIB:?SG_LIB must name the library to write, without prefix or extension}"

WORKDIR="$(cd "$(dirname "$0")" && pwd)"
ENGINE_BUILD="${WORKDIR}/../engine-build"
SOURCE_DIR="${ENGINE_BUILD}/.sources/chocolate-doom/src"
GAME_DIR="${SOURCE_DIR}/${SG_GAME}"
PLATFORM_DIR="${ENGINE_BUILD}/platform"
ENGINE_PLATFORM_DIR="${PLATFORM_DIR}/${SG_GAME}"
BUILD_DIR="${WORKDIR}/.build/${SG_GAME}"
OUT_DIR="${1:-${WORKDIR}/.build/out}"

# One NDK, recorded in SOURCES.lock next to the artifacts built with it.
NDK_VERSION="28.0.12916984"
NDK="${ANDROID_NDK_HOME:-${HOME}/Library/Android/sdk/ndk/${NDK_VERSION}}"
NDK_BIN="${NDK}/toolchains/llvm/prebuilt/darwin-x86_64/bin"
API=24

if [ ! -d "${GAME_DIR}" ]; then
    echo "error: ${SG_GAME} is missing from the engine sources; run ../engine-build/fetch-sources.sh first" >&2
    exit 1
fi

# The same replacement list the wasm build reads, from the same file.
REPLACED_SOURCES=()
while read -r line; do
    case "${line}" in
        ''|'#'*) continue ;;
    esac
    REPLACED_SOURCES+=("${line}")
done < "${ENGINE_BUILD}/replaced-sources.txt"

is_replaced() {
    local candidate="$1"
    for replaced in "${REPLACED_SOURCES[@]}"; do
        [ "${candidate}" = "${replaced}" ] && return 0
    done
    return 1
}

sources=()
while read -r file; do
    is_replaced "$(basename "${file}")" || sources+=("${file}")
done < <(find "${SOURCE_DIR}" -maxdepth 1 -name '*.c' | sort)
while read -r file; do
    sources+=("${file}")
done < <(find "${GAME_DIR}" -maxdepth 1 -name '*.c' | sort)
while read -r file; do
    sources+=("${file}")
done < <(find "${PLATFORM_DIR}" -maxdepth 1 -name '*.c' | sort)
while read -r file; do
    sources+=("${file}")
done < <(find "${ENGINE_PLATFORM_DIR}" -maxdepth 1 -name '*.c' | sort)

CFLAGS=(
    -I "${PLATFORM_DIR}/include"
    -I "${PLATFORM_DIR}"
    -I "${SOURCE_DIR}"
    -I "${GAME_DIR}"
    -DSLIPGATE=1
    -ffile-prefix-map="${ENGINE_BUILD}"=slipgate
    -ffile-prefix-map="${WORKDIR}"=slipgate-native
    -fvisibility=hidden
    -fPIC
    -Oz
    -Wno-unused-command-line-argument
)

build_one() {
    local target="$1" cc="$2" ldflags="$3" output="$4" strip_tool="$5"
    local objdir="${BUILD_DIR}/${target}"
    mkdir -p "${objdir}" "$(dirname "${output}")"
    local objects=()
    for source in "${sources[@]}"; do
        local base
        base="$(basename "${source}" .c)"
        local object="${objdir}/${base}.o"
        local extra=()
        # sg_files.c is the reroute's destination and must keep the real stdio.
        if [ "${base}" != "sg_files" ]; then
            extra+=(-include "${WORKDIR}/sg_stdio_redirect.h")
        fi
        # The wasm link's --wrap=M_FileCaseExists, as a macro: callers reach the mount-aware
        # wrapper in sg_wad_file.c. The definition in m_misc.c and the wrapper itself keep the
        # real name in sight.
        if [ "${base}" != "m_misc" ] && [ "${base}" != "sg_wad_file" ]; then
            extra+=(-DM_FileCaseExists=__wrap_M_FileCaseExists)
        fi
        "${cc}" -c "${source}" -o "${object}" "${CFLAGS[@]}" "${extra[@]}"
        objects+=("${object}")
    done
    # shellcheck disable=SC2086
    "${cc}" -shared "${objects[@]}" -o "${output}" ${ldflags}
    # shellcheck disable=SC2086
    ${strip_tool} "${output}"
    printf 'built %s\n' "${output}" >&2
    shasum -a 256 "${output}" | awk '{ print "sha256 =", $1 }' >&2
}

# SG_TARGETS narrows the build ("host" while iterating); unset means everything.
# shellcheck disable=SC2206
TARGETS=(${SG_TARGETS:-android-arm64 android-x86_64 host})

for target in "${TARGETS[@]}"; do
    case "${target}" in
        android-arm64)
            build_one "${target}" \
                "${NDK_BIN}/aarch64-linux-android${API}-clang" \
                "-Wl,-z,max-page-size=16384 -lm" \
                "${OUT_DIR}/android/arm64-v8a/lib${SG_LIB}.so" \
                "${NDK_BIN}/llvm-strip"
            ;;
        android-x86_64)
            build_one "${target}" \
                "${NDK_BIN}/x86_64-linux-android${API}-clang" \
                "-Wl,-z,max-page-size=16384 -lm" \
                "${OUT_DIR}/android/x86_64/lib${SG_LIB}.so" \
                "${NDK_BIN}/llvm-strip"
            ;;
        host)
            host_output="${OUT_DIR}/host/lib${SG_LIB}.dylib"
            case "$(uname -s)" in
                Linux) host_output="${OUT_DIR}/host/lib${SG_LIB}.so" ;;
            esac
            build_one "${target}" cc "-lm" "${host_output}" "strip -x"
            ;;
        *)
            echo "error: unknown target ${target}" >&2
            exit 1
            ;;
    esac
done

# The native surface must be the wasm surface. nm is the cheap proof.
for expected in slipgate_init slipgate_step slipgate_framebuffer slipgate_palette \
    slipgate_alloc slipgate_free slipgate_arg_push slipgate_push_event \
    slipgate_audio_drain slipgate_play_demo slipgate_save_state slipgate_set_host; do
    found=0
    for lib in "${OUT_DIR}"/android/arm64-v8a/lib"${SG_LIB}".so "${OUT_DIR}"/host/lib"${SG_LIB}".*; do
        [ -f "${lib}" ] || continue
        if nm -gU "${lib}" 2>/dev/null | grep -q "[T ]_\{0,1\}${expected}\$"; then
            found=1
        fi
    done
    if [ "${found}" = 0 ]; then
        echo "error: ${expected} is missing from the exported surface" >&2
        exit 1
    fi
done
echo "the exported surface matches the wasm module's" >&2
