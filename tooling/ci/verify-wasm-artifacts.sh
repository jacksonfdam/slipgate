#!/usr/bin/env bash
# Checks every wasm module in the repository against the hash SOURCES.lock records, and checks that
# no build script asks for SIMD.
#
# Chasm has no vector instruction support, so a module containing one is unloadable on Android and
# on the JVM harness. The obvious check — scanning the binary for the 0xFD prefix — does not work:
# in the Doom module that byte appears 510 times in the data section and 44 times inside code
# section immediates, none of them instructions. Decoding the whole code section to tell them apart
# would be reimplementing a wasm decoder in CI.
#
# So the check is split. Here it is the build flags, which is where SIMD would be asked for. The
# instructions themselves are validated by Chasm: `host/backend/wasm` loads each module, and Chasm
# rejects an opcode it cannot execute at decode time, which is exactly the failure this is meant to
# catch.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
LOCK_FILE="${ROOT}/tooling/engine-build/SOURCES.lock"

failed=0

hash_of() {
    if command -v shasum > /dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{ print $1 }'
    else
        sha256sum "$1" | awk '{ print $1 }'
    fi
}

while read -r artifact expected; do
    [ -z "${artifact}" ] && continue
    path="${ROOT}/${artifact}"
    if [ ! -f "${path}" ]; then
        echo "::error::${artifact} is recorded in SOURCES.lock but missing"
        failed=1
        continue
    fi
    actual="$(hash_of "${path}")"
    if [ "${actual}" != "${expected}" ]; then
        echo "::error::${artifact} is ${actual}, but SOURCES.lock records ${expected}"
        echo "          rebuild it and update the lock, or restore the recorded module"
        failed=1
        continue
    fi
    echo "${artifact} matches the lock"
done < <(
    awk '
        /^artifact = / { artifact = $3 }
        /^sha256 = / { print artifact, $3; artifact = "" }
    ' "${LOCK_FILE}"
)

if grep -rn -- "-msimd128" "${ROOT}/tooling" --include='*.sh' > /dev/null 2>&1; then
    echo "::error::a build script asks for -msimd128, which Chasm cannot execute"
    failed=1
else
    echo "no build script asks for SIMD"
fi

exit "${failed}"
