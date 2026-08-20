#!/usr/bin/env bash
# Builds the Strife gate's module. The build itself is build-engine.sh; what lives here is what is
# true of Strife and of nothing else.
#
# 128 MiB rather than Hexen's 96: strife1.wad is 27 MB against Hexen's 19, and the whole IWAD lands
# inside module memory because that is where the engine reads it from. Memory still grows on demand;
# this is what it starts with, so the first minutes do not spend themselves growing.
set -euo pipefail

export SG_GAME=strife
export SG_MODULE=macil.wasm
export SG_INITIAL_MEMORY=134217728

exec "$(cd "$(dirname "$0")" && pwd)/build-engine.sh" "$@"
