#!/usr/bin/env bash
# Nothing ships as an asset unless it is one of the painted-interface files listed below.
# No other image, vector, font, audio, video or animation-bundle file may be tracked anywhere
# in the repository. Shader source and Kotlin-authored geometry are code; game data is the
# user's own.
#
# The exceptions, each a deliberate decision rather than a loophole:
#   docs/screenshots/                                   device captures documenting the running app;
#                                                       no built app may reference it — the Pages
#                                                       workflow copies it beside the web build for
#                                                       the gallery page, which is not part of a build
#   ui/src/commonMain/composeResources/files/backdrops/ the painted interface: backdrops and covers
#   android/src/main/res/mipmap*                        the launcher icon, rendered per density
#   ios/Slipgate/Assets.xcassets/                       the same icon for the iOS bundle
#   web/src/wasmJsMain/resources/favicon.png            the same icon for the browser tab
set -euo pipefail

PATTERN='\.(png|jpe?g|webp|gif|bmp|ico|svg|ttf|otf|woff2?|eot|mp3|wav|ogg|flac|aac|m4a|mid|midi|mp4|webm|mov|avi|mkv|lottie|riv)$'

ALLOWED='^docs/screenshots/|^ui/src/commonMain/composeResources/files/backdrops/|^android/src/main/res/mipmap|^ios/Slipgate/Assets\.xcassets/|^web/src/wasmJsMain/resources/favicon\.png$'

hits="$(git ls-files | grep -Ei "${PATTERN}" | grep -Ev "${ALLOWED}" || true)"

if [ -n "${hits}" ]; then
    echo "::error::asset files are forbidden in this repository; generate it from code instead:"
    echo "${hits}"
    exit 1
fi

echo "No asset files tracked outside the allowed set."
