#!/usr/bin/env bash
# Puts the local data shelf on the public web and tells the app where it went.
#
# Three things happen here, in this order, and the order is the whole design:
#
#   1. serve-shelf.py comes up on localhost, with a key. It never listens on the network directly,
#      because the thing that should be reachable from outside is the tunnel and not the NAS. The
#      key is what makes that safe: a tunnel hostname is the only thing between the public web and
#      a directory of retail game data, and a hostname is not a secret.
#   2. A tunnel is opened in front of it, and its public hostname is read back out. A quick tunnel
#      gets a different hostname every time it starts, which is exactly why step 3 exists.
#   3. The hostname and the key are published to the beacon on the site. That is the one address
#      the app is configured with, and it is the reason a player never has to retype a hostname
#      after a power cut.
#
# On a LAN none of this is needed: serve-shelf.py on its own is the shelf, and the network is the
# authentication. This is the same shelf reached from outside it.
#
# Configuration is environment variables, optionally read from a file, so the same script runs from a
# terminal, from a systemd unit and from a Synology scheduled task without arguments.
#
# Usage: publish-shelf.sh [--config path/to/shelf.env]
set -euo pipefail

CONFIG="${SLIPGATE_SHELF_CONFIG:-}"
if [ "${1:-}" = "--config" ]; then
    CONFIG="${2:?--config needs a path}"
fi
if [ -n "${CONFIG}" ]; then
    # shellcheck disable=SC1090 - the operator's own file, named by them
    . "${CONFIG}"
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ROOT="${SLIPGATE_SHELF_ROOT:-$(cd "${HERE}/../.." && pwd)/slipgate-server}"
PORT="${SLIPGATE_SHELF_PORT:-8600}"
KEY="${SLIPGATE_SHELF_KEY:-}"
TUNNEL="${SLIPGATE_TUNNEL:-cloudflared}"
PUBLIC_URL="${SLIPGATE_PUBLIC_URL:-}"
BEACON_URL="${SLIPGATE_BEACON_URL:-}"
BEACON_TOKEN="${SLIPGATE_BEACON_TOKEN:-}"
REPUBLISH_SECONDS="${SLIPGATE_REPUBLISH_SECONDS:-900}"
STATE_DIR="${SLIPGATE_STATE_DIR:-${TMPDIR:-/tmp}/slipgate-shelf}"

mkdir -p "${STATE_DIR}"
SERVER_LOG="${STATE_DIR}/server.log"
TUNNEL_LOG="${STATE_DIR}/tunnel.log"

if [ -z "${KEY}" ]; then
    # A shelf with no key would be an open directory of game data behind a public hostname, so one
    # is generated and kept. Kept rather than regenerated per boot, because the app holds it too.
    KEY_FILE="${STATE_DIR}/key"
    if [ ! -s "${KEY_FILE}" ]; then
        python3 -c 'import secrets; print(secrets.token_hex(16))' > "${KEY_FILE}"
        chmod 600 "${KEY_FILE}"
        echo "generated a shelf key in ${KEY_FILE}"
    fi
    KEY="$(cat "${KEY_FILE}")"
fi

SERVER_PID=""
TUNNEL_PID=""
# Set by open_tunnel. A global rather than something printed and captured, because a command
# substitution runs in a subshell and the tunnel's process id has to survive in this one for the
# supervision loop below to have anything to watch.
TUNNEL_URL=""

cleanup() {
    for pid in "${TUNNEL_PID}" "${SERVER_PID}"; do
        if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
            kill "${pid}" 2>/dev/null || true
        fi
    done
}
trap cleanup EXIT INT TERM

start_server() {
    python3 "${HERE}/serve-shelf.py" "${ROOT}" \
        --bind 127.0.0.1 --port "${PORT}" --key "${KEY}" > "${SERVER_LOG}" 2>&1 &
    SERVER_PID=$!

    for _ in $(seq 1 40); do
        if curl -fsS "http://127.0.0.1:${PORT}/health" > /dev/null 2>&1; then
            echo "shelf up on http://127.0.0.1:${PORT} (pid ${SERVER_PID})"
            return 0
        fi
        if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
            echo "the shelf did not start:" >&2
            cat "${SERVER_LOG}" >&2
            return 1
        fi
        sleep 0.5
    done
    echo "the shelf did not answer /health within 20 seconds" >&2
    return 1
}

# Reads the public hostname out of whatever the tunnel wrote, because both tools announce it in
# their own log rather than on a promise anyone can wait on.
wait_for_url() {
    local pattern="$1"
    local found
    for _ in $(seq 1 60); do
        found="$(grep -Eo "${pattern}" "${TUNNEL_LOG}" 2>/dev/null | head -n 1 || true)"
        if [ -n "${found}" ]; then
            TUNNEL_URL="${found}"
            return 0
        fi
        if ! kill -0 "${TUNNEL_PID}" 2>/dev/null; then
            echo "the tunnel exited before it published a URL:" >&2
            tail -n 20 "${TUNNEL_LOG}" >&2
            return 1
        fi
        sleep 1
    done
    echo "the tunnel did not publish a URL within 60 seconds" >&2
    return 1
}

start_cloudflared() {
    : > "${TUNNEL_LOG}"
    if [ -n "${SLIPGATE_CLOUDFLARED_TUNNEL:-}" ]; then
        # A named tunnel keeps its hostname across restarts. The beacon is still published, because
        # the app also learns the key from it, and because a hostname that never changes is a
        # property of this deployment rather than of the app.
        cloudflared tunnel --no-autoupdate run \
            --url "http://127.0.0.1:${PORT}" "${SLIPGATE_CLOUDFLARED_TUNNEL}" > "${TUNNEL_LOG}" 2>&1 &
        TUNNEL_PID=$!
        if [ -z "${PUBLIC_URL}" ]; then
            echo "SLIPGATE_PUBLIC_URL must name the hostname routed to ${SLIPGATE_CLOUDFLARED_TUNNEL}" >&2
            return 1
        fi
        TUNNEL_URL="${PUBLIC_URL}"
        return 0
    fi

    cloudflared tunnel --no-autoupdate --url "http://127.0.0.1:${PORT}" > "${TUNNEL_LOG}" 2>&1 &
    TUNNEL_PID=$!
    wait_for_url 'https://[a-z0-9-]+\.trycloudflare\.com'
}

start_ngrok() {
    : > "${TUNNEL_LOG}"
    ngrok http "${PORT}" --log stdout > "${TUNNEL_LOG}" 2>&1 &
    TUNNEL_PID=$!
    # ngrok's own local API is the reliable answer; the log line format has changed between versions.
    local found
    for _ in $(seq 1 60); do
        found="$(curl -fsS http://127.0.0.1:4040/api/tunnels 2>/dev/null |
            python3 -c 'import json,sys; print(next((t["public_url"] for t in json.load(sys.stdin)["tunnels"] if t["public_url"].startswith("https")), ""))' 2>/dev/null || true)"
        if [ -n "${found}" ]; then
            TUNNEL_URL="${found}"
            return 0
        fi
        if ! kill -0 "${TUNNEL_PID}" 2>/dev/null; then
            echo "ngrok exited before it published a URL:" >&2
            tail -n 20 "${TUNNEL_LOG}" >&2
            return 1
        fi
        sleep 1
    done
    echo "ngrok did not publish a URL within 60 seconds" >&2
    return 1
}

open_tunnel() {
    case "${TUNNEL}" in
        cloudflared) start_cloudflared ;;
        ngrok) start_ngrok ;;
        none)
            if [ -z "${PUBLIC_URL}" ]; then
                echo "SLIPGATE_TUNNEL=none needs SLIPGATE_PUBLIC_URL to say where the shelf is reachable" >&2
                return 1
            fi
            TUNNEL_URL="${PUBLIC_URL}"
            ;;
        *)
            echo "unknown SLIPGATE_TUNNEL: ${TUNNEL} (cloudflared, ngrok or none)" >&2
            return 1
            ;;
    esac
}

# The document the app reads. Lines rather than JSON, because the app parses it with a split and no
# JSON parser exists in the host; see docs/data-shelf.md for the format.
pointer() {
    printf 'slipgate-beacon 1\nurl\t%s\nkey\t%s\nupdated\t%s\n' \
        "$1" "${KEY}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}

publish() {
    local url="$1"
    if [ -z "${BEACON_URL}" ]; then
        echo "no SLIPGATE_BEACON_URL set, so nothing was published; point the app at ${url}?key=${KEY} by hand"
        return 0
    fi
    if [ -z "${BEACON_TOKEN}" ]; then
        echo "SLIPGATE_BEACON_URL is set but SLIPGATE_BEACON_TOKEN is not; the beacon will refuse this" >&2
        return 1
    fi
    if pointer "${url}" | curl -fsS -X POST \
        -H "authorization: Bearer ${BEACON_TOKEN}" \
        -H 'content-type: text/plain' \
        --data-binary @- "${BEACON_URL}" > /dev/null; then
        echo "published ${url} to the beacon"
    else
        # Not fatal: the shelf is up and reachable, and the next republish may well succeed. A boot
        # that died here would take working game data down with an unreachable website.
        echo "could not publish to the beacon; the shelf is still serving on ${url}" >&2
    fi
}

start_server
open_tunnel
URL="${TUNNEL_URL}"
echo "shelf reachable at ${URL}"
publish "${URL}"

# The loop is the only thing that keeps the script in the foreground, which is what a supervisor
# expects, and it earns its keep twice: it refreshes the beacon so a stale pointer is visible as
# stale, and it notices a quick tunnel that reconnected under a new hostname.
while true; do
    sleep "${REPUBLISH_SECONDS}" &
    wait $! || true

    if [ -n "${SERVER_PID}" ] && ! kill -0 "${SERVER_PID}" 2>/dev/null; then
        echo "the shelf stopped; exiting so the supervisor can restart it" >&2
        exit 1
    fi
    if [ -n "${TUNNEL_PID}" ] && ! kill -0 "${TUNNEL_PID}" 2>/dev/null; then
        echo "the tunnel stopped; exiting so the supervisor can restart it" >&2
        exit 1
    fi

    if [ "${TUNNEL}" = "cloudflared" ] && [ -z "${SLIPGATE_CLOUDFLARED_TUNNEL:-}" ]; then
        LATEST="$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "${TUNNEL_LOG}" | tail -n 1 || true)"
        if [ -n "${LATEST}" ] && [ "${LATEST}" != "${URL}" ]; then
            echo "the tunnel hostname changed to ${LATEST}"
            URL="${LATEST}"
        fi
    fi

    publish "${URL}"
done
