#!/usr/bin/env bash
# Builds the Heretic gate's module. The build itself is build-engine.sh; what lives here is what is
# true of Heretic and of nothing else.
#
# 64 MiB, the same as Doom: Heretic's zone and its IWAD are the same order of size. Hexen is the one
# that needs more, and it will say so in its own script.
set -euo pipefail

export SG_GAME=heretic
export SG_MODULE=corvus.wasm
export SG_INITIAL_MEMORY=67108864

exec "$(cd "$(dirname "$0")" && pwd)/build-engine.sh" "$@"
