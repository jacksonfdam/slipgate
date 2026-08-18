#!/usr/bin/env bash
# Advisory only: warns when a change set is large enough to be hard to review.
# Generated and locked files do not count towards the total.
# Usage: check-pr-size.sh <base-ref> <head-ref>
set -euo pipefail

BASE_REF="${1:?base ref required}"
HEAD_REF="${2:?head ref required}"
THRESHOLD=400

changed="$(
    git diff --numstat "${BASE_REF}..${HEAD_REF}" \
        -- . \
        ':(exclude)gradle/wrapper/**' \
        ':(exclude)gradlew' \
        ':(exclude)gradlew.bat' \
        ':(exclude)kotlin-js-store/**' \
        ':(exclude)**/*.lock' |
        awk '{ added += $1; removed += $2 } END { print added + removed + 0 }'
)"

echo "Reviewable changed lines: ${changed} (threshold ${THRESHOLD})"

if [ "${changed}" -gt "${THRESHOLD}" ]; then
    echo "::warning::${changed} changed lines exceed the ${THRESHOLD} line advisory threshold; consider splitting this pull request."
fi
