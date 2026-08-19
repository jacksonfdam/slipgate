#!/usr/bin/env bash
# The interface cannot be opened in Figma: nothing ships as an asset. No image, vector,
# font, audio, video or animation-bundle file may be tracked anywhere in the repository.
# Shader source and Kotlin-authored geometry are code; game data is the user's own.
#
# docs/screenshots is the one exception: device captures documenting the running app.
# Nothing in the build may reference that directory.
set -euo pipefail

PATTERN='\.(png|jpe?g|webp|gif|bmp|ico|svg|ttf|otf|woff2?|eot|mp3|wav|ogg|flac|aac|m4a|mid|midi|mp4|webm|mov|avi|mkv|lottie|riv)$'

hits="$(git ls-files | grep -Ei "${PATTERN}" | grep -v '^docs/screenshots/' || true)"

if [ -n "${hits}" ]; then
    echo "::error::asset files are forbidden in this repository; generate it from code instead:"
    echo "${hits}"
    exit 1
fi

echo "No asset files tracked."
