#!/usr/bin/env bash
# Fetches the engine sources Slipgate builds from, at exactly the revision SOURCES.lock records.
#
# The tree is never vendored: it is cloned into .sources/, which is gitignored, so the repository
# holds a pin rather than a copy. Run with --update to move the pin to a new tag.
set -euo pipefail

WORKDIR="$(cd "$(dirname "$0")" && pwd)"
SOURCES_DIR="${WORKDIR}/.sources"
LOCK_FILE="${WORKDIR}/SOURCES.lock"

CHOCOLATE_DOOM_REPO="https://github.com/chocolate-doom/chocolate-doom.git"
# Verified against the upstream releases page. Override to move the pin, then run with --update.
CHOCOLATE_DOOM_REF="${CHOCOLATE_DOOM_REF:-chocolate-doom-3.1.1}"

update_lock=0
if [ "${1:-}" = "--update" ]; then
    update_lock=1
fi

log() {
    printf '%s\n' "$*" >&2
}

# Reads a field for a source from the lock file, or an empty string when it is not recorded.
lock_field() {
    local source_name="$1" field="$2"
    [ -f "${LOCK_FILE}" ] || return 0
    awk -v source="${source_name}" -v field="${field}" '
        $1 == "[" source "]" { in_section = 1; next }
        /^\[/ { in_section = 0 }
        in_section && $1 == field { print $3 }
    ' "${LOCK_FILE}"
}

clone_at() {
    local repo="$1" ref="$2" destination="$3"
    rm -rf "${destination}"
    git clone --quiet --depth 1 --branch "${ref}" "${repo}" "${destination}"
}

checkout_commit() {
    local destination="$1" commit="$2"
    # A shallow clone of a tag may not contain the recorded commit if the tag moved, which is
    # exactly the case worth failing on.
    if ! git -C "${destination}" cat-file -e "${commit}^{commit}" 2>/dev/null; then
        log "error: ${commit} is not in the fetched tree; the tag moved since the lock was written"
        exit 1
    fi
    git -C "${destination}" checkout --quiet "${commit}"
}

fetch_source() {
    local source_name="$1" repo="$2" ref="$3"
    local destination="${SOURCES_DIR}/${source_name}"
    local locked_commit
    locked_commit="$(lock_field "${source_name}" commit)"

    log "fetching ${source_name} at ${ref}"
    clone_at "${repo}" "${ref}" "${destination}"
    local head_commit
    head_commit="$(git -C "${destination}" rev-parse HEAD)"

    if [ "${update_lock}" -eq 1 ] || [ -z "${locked_commit}" ]; then
        write_lock "${source_name}" "${repo}" "${ref}" "${head_commit}"
        log "locked ${source_name} at ${head_commit}"
        return
    fi

    if [ "${head_commit}" != "${locked_commit}" ]; then
        log "error: ${ref} now points at ${head_commit}, but SOURCES.lock records ${locked_commit}"
        log "       run with --update after checking what changed upstream"
        exit 1
    fi
    checkout_commit "${destination}" "${locked_commit}"
    log "verified ${source_name} at ${locked_commit}"
}

write_lock() {
    local source_name="$1" repo="$2" ref="$3" commit="$4"
    mkdir -p "${WORKDIR}"
    cat > "${LOCK_FILE}" <<LOCK
# Engine sources Slipgate builds from. Written by fetch-sources.sh --update.
#
# The commit is what actually gets built; the tag is recorded so a human can see which release it
# is. CI fails when a fetched tree does not match this file, so a moved tag cannot slip through.

[${source_name}]
repository = ${repo}
tag = ${ref}
commit = ${commit}
LOCK
}

mkdir -p "${SOURCES_DIR}"
fetch_source chocolate-doom "${CHOCOLATE_DOOM_REPO}" "${CHOCOLATE_DOOM_REF}"
log "sources are in ${SOURCES_DIR}"
