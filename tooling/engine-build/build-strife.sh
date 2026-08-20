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

# i_glob.c is opendir and readdir over a filesystem that has neither, so it finds nothing and reports
# failure. Every engine calls it — W_AutoLoadWADs does — but only Strife treats the failure as fatal:
# ClearTmp globs its temporary save directory at start-up and calls I_Error when the glob is null,
# which is a gate that dies before its first frame. platform/strife/sg_glob.c reads the module's own
# file table instead. Replaced here rather than for everyone so the other three modules stay
# byte-identical, since finding nothing is already what they do.
export SG_REPLACED_EXTRA="i_glob.c"

exec "$(cd "$(dirname "$0")" && pwd)/build-engine.sh" "$@"
