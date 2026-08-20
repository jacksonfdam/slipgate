#!/usr/bin/env bash
# Builds the Doom gate's native libraries. The build itself is build-engine-native.sh; what lives
# here is what is true of Doom and of nothing else.
set -euo pipefail

export SG_GAME=doom
export SG_LIB=mars

exec "$(cd "$(dirname "$0")" && pwd)/build-engine-native.sh" "$@"
