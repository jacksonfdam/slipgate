#!/usr/bin/env python3
"""Serves one directory of game data to Slipgate, over HTTP, behind a key.

The launcher already knows how to fetch a file over HTTP and validate it by contents before it
stores it. What it has never had is somewhere to fetch from that is the player's own: the free
replacements are downloads on the public web, and everything else has to be picked file by file on
each device. A player with the files on a NAS owns them once and still supplies them four times.

So this is deliberately the smallest thing that closes that gap. It lists what is on disk and hands
over bytes, and it is the standard library only, because a NAS is not a machine anyone wants to
install a web framework on.

Three properties are not optional, and each is here for a reason a comment can state:

- A key on every request. What this serves is commercial game data, and the tunnel in front of it is
  reachable by anyone who learns its hostname. An open directory of IWADs on the public web is
  redistribution, whoever set it up and however briefly.
- Cross-origin headers. The web build runs in a browser, and a browser refuses a request that the
  origin did not allow before the request is even made. This is the reason the launcher's own free
  downloads do not work on the web at all, and one header is the whole of the fix.
- Range requests. A phone on mobile data loses a 700 MB transfer halfway through, and a server that
  cannot resume makes the player start again.

Usage: slipgate-library.py --root /volume1/games/slipgate --port 8099 --key <key>
"""

from __future__ import annotations

import argparse
import hmac
import os
import secrets
import sys
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

# What a gate can be handed. Anything else in the directory is not listed and not served, so a
# library that also holds notes, screenshots or a backup does not offer them to the launcher.
DATA_SUFFIXES = frozenset({".wad", ".iwad", ".pwad", ".pk3", ".pak", ".deh", ".bex", ".lmp"})

# Add-ons live one directory down from the game they load over, which is what makes a manifest
# derivable from the directory itself rather than from a file someone has to keep in step with it.
ADD_ON_DIRECTORY = "addons"

MANIFEST_HEADER = "slipgate-library 1"
CHUNK = 1 << 16


class Library:
    """The directory, read fresh on every request.

    Nothing is cached because the point of a library is that a player drops a file into it and the
    launcher sees it. A directory listing costs nothing next to the transfer that follows it.
    """

    def __init__(self, root: Path) -> None:
        self.root = root.resolve()

    def entries(self) -> list[tuple[str, str, str, int]]:
        """Every servable file as (gate, name, role, size), ordered so two runs agree."""
        found: list[tuple[str, str, str, int]] = []
        for gate_directory in sorted(p for p in self.root.iterdir() if p.is_dir()):
            gate = gate_directory.name
            for path in sorted(gate_directory.iterdir()):
                if path.is_file() and path.suffix.lower() in DATA_SUFFIXES:
                    found.append((gate, path.name, "game", path.stat().st_size))
            add_ons = gate_directory / ADD_ON_DIRECTORY
            if add_ons.is_dir():
                for path in sorted(add_ons.iterdir()):
                    if path.is_file() and path.suffix.lower() in DATA_SUFFIXES:
                        found.append((gate, path.name, "addon", path.stat().st_size))
        return found

    def manifest(self) -> bytes:
        """The list the launcher reads, as lines rather than as JSON.

        Lines because the host has no JSON parser and no dependency that would give it one, and a
        format the launcher can read with a split is a format that cannot half-parse.
        """
        lines = [MANIFEST_HEADER]
        for gate, name, role, size in self.entries():
            path = f"/files/{gate}/{ADD_ON_DIRECTORY}/{name}" if role == "addon" else f"/files/{gate}/{name}"
            lines.append("\t".join(("file", gate, name, role, str(size), path)))
        return ("\n".join(lines) + "\n").encode("utf-8")

    def resolve(self, request_path: str) -> Path | None:
        """The file a `/files/...` path names, or None when it names something else.

        Resolved against the root and then checked to still be under it, which is what stops
        `..` and a symlink pointing off the volume from turning a game library into a file browser.
        """
        relative = unquote(request_path).removeprefix("/files/").strip("/")
        if not relative:
            return None
        candidate = (self.root / relative).resolve()
        if not candidate.is_relative_to(self.root) or not candidate.is_file():
            return None
        if candidate.suffix.lower() not in DATA_SUFFIXES:
            return None
        return candidate


class Handler(BaseHTTPRequestHandler):
    server_version = "slipgate-library/1"
    # 1.1 because every answer here carries a Content-Length, and a phone resuming a 700 MB transfer
    # over a tunnel should not also pay for a new connection per request.
    protocol_version = "HTTP/1.1"

    # Bound by serve() before the server starts.
    library: Library
    key: str

    def do_OPTIONS(self) -> None:  # noqa: N802 - the base class names it
        # A browser asks before it fetches. Answering here rather than in the fetch is what makes
        # the web build able to use a library at all.
        self.send_response(HTTPStatus.NO_CONTENT)
        self.cross_origin()
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "authorization, range")
        self.send_header("Access-Control-Max-Age", "86400")
        self.end_headers()

    def do_HEAD(self) -> None:  # noqa: N802 - the base class names it
        self.respond(body_wanted=False)

    def do_GET(self) -> None:  # noqa: N802 - the base class names it
        self.respond(body_wanted=True)

    def respond(self, *, body_wanted: bool) -> None:
        route = urlparse(self.path)
        if route.path == "/health":
            self.text(HTTPStatus.OK, b"slipgate-library ok\n", body_wanted=body_wanted)
            return

        if not self.authorised(route.query):
            # Deliberately the same answer for a missing key and a wrong one: a caller who learned
            # the hostname should not also learn whether they got close.
            self.text(HTTPStatus.UNAUTHORIZED, b"a key is required\n", body_wanted=body_wanted)
            return

        if route.path == "/manifest":
            self.text(HTTPStatus.OK, self.library.manifest(), body_wanted=body_wanted)
            return

        if route.path.startswith("/files/"):
            self.send_file(route.path, body_wanted=body_wanted)
            return

        self.text(HTTPStatus.NOT_FOUND, b"no such path\n", body_wanted=body_wanted)

    def authorised(self, query: str) -> bool:
        """The key, from a header when the caller can set one and from the query when it cannot.

        The launcher's download layer is three platform HTTP clients behind one `fetch(url)`, and
        none of them carries headers today. A query parameter is the version of this that works on
        all three, and it travels inside TLS to the tunnel like the rest of the request line.
        """
        header = self.headers.get("Authorization", "")
        offered = header.removeprefix("Bearer ").strip() if header.startswith("Bearer ") else ""
        if not offered:
            offered = (parse_qs(query).get("key") or [""])[0]
        return bool(offered) and hmac.compare_digest(offered, self.key)

    def send_file(self, request_path: str, *, body_wanted: bool) -> None:
        path = self.library.resolve(request_path)
        if path is None:
            self.text(HTTPStatus.NOT_FOUND, b"no such file\n", body_wanted=body_wanted)
            return

        size = path.stat().st_size
        start, end = self.requested_range(size)
        if start is None:
            self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            self.cross_origin()
            self.send_header("Content-Range", f"bytes */{size}")
            self.end_headers()
            return

        partial = (start, end) != (0, size - 1)
        length = end - start + 1
        self.send_response(HTTPStatus.PARTIAL_CONTENT if partial else HTTPStatus.OK)
        self.cross_origin()
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if not body_wanted:
            return
        with path.open("rb") as source:
            source.seek(start)
            remaining = length
            while remaining > 0:
                chunk = source.read(min(CHUNK, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

    def requested_range(self, size: int) -> tuple[int | None, int]:
        """The byte range asked for, clamped to the file, or (None, 0) when it cannot be met.

        One range only. A player resuming a download asks for the rest of the file, and multipart
        ranges are a video player's problem rather than a game library's.
        """
        header = self.headers.get("Range")
        if not header or not header.startswith("bytes=") or "," in header:
            return 0, size - 1
        span = header.removeprefix("bytes=").strip()
        first, _, last = span.partition("-")
        try:
            if not first:
                # A suffix range: the last N bytes.
                length = int(last)
                if length <= 0:
                    return None, 0
                return max(0, size - length), size - 1
            start = int(first)
            end = int(last) if last else size - 1
        except ValueError:
            return None, 0
        if start >= size or end < start:
            return None, 0
        return start, min(end, size - 1)

    def text(self, status: HTTPStatus, body: bytes, *, body_wanted: bool) -> None:
        self.send_response(status)
        self.cross_origin()
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if body_wanted:
            self.wfile.write(body)

    def cross_origin(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Expose-Headers", "content-length, content-range, accept-ranges")

    def log_message(self, format: str, *args: object) -> None:  # noqa: A002 - the base class names it
        # One line per request on stdout, so whatever supervises this has a log without a log file.
        sys.stdout.write(f"{self.address_string()} {format % args}\n")
        sys.stdout.flush()


def serve(root: Path, host: str, port: int, key: str) -> None:
    Handler.library = Library(root)
    Handler.key = key
    server = ThreadingHTTPServer((host, port), Handler)
    listed = Handler.library.entries()
    print(f"slipgate-library serving {len(listed)} files from {root} on http://{host}:{port}")
    for gate, name, role, size in listed:
        print(f"  {gate}/{name} {role} {size / (1 << 20):.1f} MB")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(description="Serve a directory of game data to Slipgate.")
    parser.add_argument("--root", default=os.environ.get("SLIPGATE_LIBRARY_ROOT", "library"))
    parser.add_argument("--host", default=os.environ.get("SLIPGATE_LIBRARY_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("SLIPGATE_LIBRARY_PORT", "8099")))
    parser.add_argument("--key", default=os.environ.get("SLIPGATE_LIBRARY_KEY", ""))
    arguments = parser.parse_args()

    root = Path(arguments.root)
    if not root.is_dir():
        print(f"no such directory: {root}", file=sys.stderr)
        return 1

    key = arguments.key or secrets.token_hex(16)
    if not arguments.key:
        # Generated rather than refused, so a first run works; printed, because the launcher needs it.
        print(f"no key given, using a generated one: {key}")

    serve(root, arguments.host, arguments.port, key)
    return 0


if __name__ == "__main__":
    sys.exit(main())
