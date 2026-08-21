#!/usr/bin/env python3
"""Serves the local game-data shelf over the LAN, or through a tunnel with a key.

Four things a plain static server does not give us, and each is a reason this file exists:

  CORS   — the browser target fetches game data with fetch(), and without
           Access-Control-Allow-Origin it is refused before the request is made. That missing
           header on GitHub's release assets is why the web build cannot download Freedoom today.
  Ranges — a 98 MB pak over a phone's wifi needs to resume. Python's stock handler ignores Range
           entirely and answers 200 with the whole file, which reads as success and is not.
  Index  — /shelf.index is what the app reads to learn what is here: tab separated lines, because
           the host carries no JSON parser. It is derived from manifest.json when the shelf has
           one and from the directory itself when it does not.
  Key    — optional, and off by default: on a LAN the network is the authentication, exactly as
           before. Pass --key and every path but /health needs it. That is what makes the shelf
           safe to put behind a tunnel, where the hostname is the only thing between the public
           web and a directory of retail game data.

Bind it to your LAN address rather than 0.0.0.0 if that distinction matters where you are.

Usage:
    serve-shelf.py [shelf-root] [--port 8600] [--bind 0.0.0.0] [--key <key>]
"""

from __future__ import annotations

import argparse
import hmac
import json
import mimetypes
import os
import re
import socket
from functools import partial
from http import HTTPStatus
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import parse_qs, urlparse

RANGE = re.compile(r"^bytes=(\d*)-(\d*)$")
CHUNK = 1 << 20

INDEX_PATH = "/shelf.index"
INDEX_HEADER = "slipgate-shelf 1"

# What the index lists. Anything else on the shelf — notes, the manifest, a stray screenshot — is
# still served if asked for by name, but it is not offered to a gate as game data.
DATA_SUFFIXES = frozenset({".wad", ".iwad", ".pwad", ".pk3", ".pak", ".deh", ".bex", ".lmp"})

SHELF_NOTES = {"README.md", "NOTES.txt", "manifest.json", ".DS_Store"}


def gate_of(relative: Path) -> str:
    """The gate a path files a file under, or nothing when it files it under none.

    `addons/mars/av.wad` was filed for the Doom gate and `addons/hexdd.wad` for no gate at all: an
    add-on names no engine in its contents, so a shelf that did not sort one is a shelf offering it
    to every gate.
    """
    parts = relative.parts
    if parts[0] == "addons":
        return parts[1] if len(parts) > 2 else ""
    return parts[0]


class ShelfHandler(SimpleHTTPRequestHandler):
    # Bound by main() before the server starts. Empty means no key, which is the LAN default.
    key = ""

    def end_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Range")
        self.send_header("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        self.send_header("Accept-Ranges", "bytes")
        super().end_headers()

    def do_OPTIONS(self) -> None:  # noqa: N802 — the base class spells them this way
        self.send_response(HTTPStatus.NO_CONTENT)
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.end_headers()

    def do_HEAD(self) -> None:  # noqa: N802
        if self.refused():
            return
        super().do_HEAD()

    def do_GET(self) -> None:  # noqa: N802
        route = urlparse(self.path).path
        if route == "/health":
            # Open even behind a key, so whatever supervises the server can wait on it.
            self.text(HTTPStatus.OK, b"slipgate-shelf ok\n")
            return

        if self.refused():
            return

        if route == INDEX_PATH:
            self.text(HTTPStatus.OK, self.index())
            return

        header = self.headers.get("Range")
        if not header:
            super().do_GET()
            return

        match = RANGE.match(header.strip())
        path = Path(self.translate_path(self.path))
        if match is None or not path.is_file():
            super().do_GET()
            return

        size = path.stat().st_size
        first, last = match.group(1), match.group(2)
        if first == "":
            # A suffix range: the last N bytes.
            start, end = max(0, size - int(last or 0)), size - 1
        else:
            start = int(first)
            end = int(last) if last else size - 1

        if start >= size or start > end:
            self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
            self.send_header("Content-Range", f"bytes */{size}")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        end = min(end, size - 1)
        length = end - start + 1
        kind = mimetypes.guess_type(path.name)[0] or "application/octet-stream"

        self.send_response(HTTPStatus.PARTIAL_CONTENT)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(length))
        self.end_headers()

        with path.open("rb") as handle:
            handle.seek(start)
            remaining = length
            while remaining > 0:
                block = handle.read(min(CHUNK, remaining))
                if not block:
                    break
                self.wfile.write(block)
                remaining -= len(block)


    def refused(self) -> bool:
        """Answers 401 and returns True when a key is required and was not offered correctly."""
        if not self.key or self.offered() == self.key:
            return False
        # The same answer for a missing key and a wrong one: whoever learned the hostname should
        # not also learn whether they got close.
        self.text(HTTPStatus.UNAUTHORIZED, b"a key is required\n")
        return True

    def offered(self) -> str:
        """The key the caller sent, from a header when it can set one and the query when it cannot.

        The app's download layer is three platform HTTP clients behind one `fetch(url)`, and none of
        them carries headers until the resumable rewrite lands. A query parameter is the version of
        this that works on all three today, and it travels inside the tunnel's TLS.
        """
        header = self.headers.get("Authorization", "")
        if header.lower().startswith("bearer "):
            token = header[len("bearer ") :].strip()
            if token and hmac.compare_digest(token, self.key):
                return self.key
        query = parse_qs(urlparse(self.path).query).get("key") or [""]
        return self.key if query[0] and hmac.compare_digest(query[0], self.key) else ""

    def index(self) -> bytes:
        """The shelf as lines: `file`, the gate it is filed for, its name, its role, its size, its path.

        manifest.json is the source of truth when it exists, because inspect-shelf.py read every
        file to write it. Without one the directory still answers — a shelf nobody has inspected
        yet is a shelf a player can still install from, and the app inspects what arrives anyway.
        """
        root = Path(self.directory)
        lines = [INDEX_HEADER]
        for shelf, name, role, size, url in self.listing(root):
            lines.append("\t".join(("file", shelf, name, role, str(size), url)))
        return ("\n".join(lines) + "\n").encode("utf-8")

    def listing(self, root: Path) -> list[tuple[str, str, str, int, str]]:
        manifest = root / "manifest.json"
        if manifest.is_file():
            try:
                entries = json.loads(manifest.read_text())["files"]
            except (OSError, ValueError, KeyError):
                entries = None
            if entries is not None:
                return [
                    (
                        entry.get("shelf", ""),
                        entry.get("name", ""),
                        "addon" if entry.get("role") == "AddOn" else "game",
                        int(entry.get("size", 0)),
                        entry.get("url", ""),
                    )
                    for entry in entries
                    if entry.get("url")
                ]

        found = []
        for path in sorted(root.rglob("*")):
            if not path.is_file() or path.name in SHELF_NOTES:
                continue
            if path.suffix.lower() not in DATA_SUFFIXES:
                continue
            relative = path.relative_to(root)
            # Without a reading, the layout is all there is to go on: `addons/` holds what loads over
            # a game, which is the one role the directory itself states, and the folder under it is
            # the gate it was filed for when there is one.
            role = "addon" if relative.parts[0] == "addons" else "game"
            gate = gate_of(relative)
            found.append((gate, path.name, role, path.stat().st_size, "/" + relative.as_posix()))
        return found

    def text(self, status: HTTPStatus, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)


def lan_address() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 1))  # a documentation address: routed nowhere, never dialled
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def main() -> None:
    repository = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shelf", nargs="?", default=str(repository / "slipgate-server"))
    parser.add_argument("--port", type=int, default=8600)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--key", default=os.environ.get("SLIPGATE_SHELF_KEY", ""))
    arguments = parser.parse_args()

    shelf = Path(arguments.shelf).resolve()
    if not shelf.is_dir():
        raise SystemExit(f"No shelf at {shelf}. Run tooling/data-shelf/init-shelf.sh first.")

    ShelfHandler.key = arguments.key
    handler = partial(ShelfHandler, directory=str(shelf))
    server = HTTPServer((arguments.bind, arguments.port), handler)
    print(f"Serving {shelf}")
    print(f"  http://{lan_address()}:{arguments.port}/   ← reachable from your devices")
    print(f"  http://localhost:{arguments.port}/manifest.json   ← the readings, from inspect-shelf.py --manifest")
    print(f"  http://localhost:{arguments.port}{INDEX_PATH}   ← what the app reads")
    if arguments.key:
        print("  a key is required on every path but /health")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
