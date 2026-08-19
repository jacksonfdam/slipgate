#!/usr/bin/env bash
# Builds the Doom gate's module. The build itself is build-engine.sh; what lives here is what is
# true of Doom and of nothing else.
#
# 64 MiB covers Doom's zone plus the largest IWAD a player is likely to supply.
set -euo pipefail

export SG_GAME=doom
export SG_MODULE=mars.wasm
export SG_INITIAL_MEMORY=67108864

exec "$(cd "$(dirname "$0")" && pwd)/build-engine.sh" "$@"
