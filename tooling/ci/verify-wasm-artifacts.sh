#!/usr/bin/env bash
# Checks every wasm module in the repository against the hash SOURCES.lock records, and refuses
# any module containing vector instructions.
#
# Chasm has no SIMD support, so a single vector opcode makes a module unloadable on Android. The
# check is here rather than in a comment because that failure appears at runtime, on a device,
# with no useful message.
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

# SIMD opcodes are the 0xFD prefix. Reading the bytes is enough: a module either contains the
# prefix in its code section or it does not, and disassembling to find out needs a toolchain CI
# would otherwise not install.
contains_vector_opcodes() {
    python3 - "$1" <<'PYTHON'
import sys

data = open(sys.argv[1], "rb").read()
sys.exit(0 if b"\xfd" in data else 1)
PYTHON
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
    if contains_vector_opcodes "${path}"; then
        echo "::error::${artifact} contains vector instructions, which Chasm cannot execute"
        failed=1
        continue
    fi
    echo "${artifact} matches the lock and is free of vector instructions"
done < <(
    awk '
        /^artifact = / { artifact = $3 }
        /^sha256 = / { print artifact, $3; artifact = "" }
    ' "${LOCK_FILE}"
)

exit "${failed}"
