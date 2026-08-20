#!/usr/bin/env bash
# Builds the Hexen gate's module. The build itself is build-engine.sh; what lives here is what is
# true of Hexen and of nothing else.
#
# 96 MiB rather than Doom's 64: Hexen's zone holds a hub of levels rather than one, its IWAD is larger,
# and its scripting keeps state for maps the player is not standing in. Memory still grows on demand;
# this is what it starts with, so the first minutes do not spend themselves growing.
set -euo pipefail

export SG_GAME=hexen
export SG_MODULE=korax.wasm
export SG_INITIAL_MEMORY=100663296

exec "$(cd "$(dirname "$0")" && pwd)/build-engine.sh" "$@"
