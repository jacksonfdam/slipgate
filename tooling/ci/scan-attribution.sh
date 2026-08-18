#!/usr/bin/env bash
# Fails when assistant or AI attribution text appears in commit messages, tracked files,
# or a supplied pull request body. The repository must read as written by a single author.
# Usage: scan-attribution.sh <base-ref> <head-ref> [pull-request-body-file]
set -euo pipefail

BASE_REF="${1:?base ref required}"
HEAD_REF="${2:?head ref required}"
BODY_FILE="${3:-}"

PATTERN='co-authored-by: *(claude|chatgpt|copilot|gemini)|generated (with|by) *(claude|chatgpt|copilot|ai)|claude code|anthropic\.com|as an ai (language )?model|written by (an )?ai'

failed=0

report() {
    echo "::error::AI attribution found in $1:"
    echo "$2"
    failed=1
}

messages="$(git log --format='%H%n%B' "${BASE_REF}..${HEAD_REF}")"
if hit="$(printf '%s' "${messages}" | grep -Ein "${PATTERN}")"; then
    report "commit messages" "${hit}"
fi

changed_files="$(git diff --name-only --diff-filter=ACMR "${BASE_REF}..${HEAD_REF}")"
while read -r file; do
    [ -z "${file}" ] && continue
    [ -f "${file}" ] || continue
    # This script has to spell the forbidden strings out to search for them.
    [ "${file}" = "tooling/ci/scan-attribution.sh" ] && continue
    if hit="$(grep -Ein "${PATTERN}" "${file}")"; then
        report "${file}" "${hit}"
    fi
done < <(printf '%s\n' "${changed_files}")

if [ -n "${BODY_FILE}" ] && [ -f "${BODY_FILE}" ]; then
    if hit="$(grep -Ein "${PATTERN}" "${BODY_FILE}")"; then
        report "pull request body" "${hit}"
    fi
fi

if [ "${failed}" -eq 0 ]; then
    echo "No attribution text found."
fi

exit "${failed}"
