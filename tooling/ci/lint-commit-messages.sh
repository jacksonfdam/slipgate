#!/usr/bin/env bash
# Validates every commit subject in a range against the project's Conventional Commits rules.
# Usage: lint-commit-messages.sh <base-ref> <head-ref>
set -euo pipefail

BASE_REF="${1:?base ref required}"
HEAD_REF="${2:?head ref required}"

TYPES='feat|fix|refactor|perf|test|docs|build|ci|chore'
SUBJECT_PATTERN="^(${TYPES})(\([a-z0-9][a-z0-9-]*\))?: [a-z].*$"
MAX_SUBJECT_LENGTH=72

failed=0

while read -r sha; do
    [ -z "${sha}" ] && continue
    subject="$(git log -1 --format=%s "${sha}")"

    if ! printf '%s' "${subject}" | grep -Eq "${SUBJECT_PATTERN}"; then
        echo "::error::${sha:0:8} subject does not match 'type(scope): subject': ${subject}"
        failed=1
        continue
    fi

    if [ "${#subject}" -gt "${MAX_SUBJECT_LENGTH}" ]; then
        echo "::error::${sha:0:8} subject is ${#subject} characters, limit is ${MAX_SUBJECT_LENGTH}: ${subject}"
        failed=1
    fi

    case "${subject}" in
        *.) echo "::error::${sha:0:8} subject ends with a period: ${subject}"; failed=1 ;;
    esac
done < <(git rev-list "${BASE_REF}..${HEAD_REF}")

if [ "${failed}" -eq 0 ]; then
    echo "All commit subjects conform."
fi

exit "${failed}"
