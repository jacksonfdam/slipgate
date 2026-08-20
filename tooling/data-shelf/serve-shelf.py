#!/usr/bin/env python3
"""Serves the local game-data shelf over the LAN.

Two things a plain static server does not give us, and both are the reason this file exists:

  CORS   — the browser target fetches game data with fetch(), and without
           Access-Control-Allow-Origin it is refused before the request is made. That missing
           header on GitHub's release assets is why the web build cannot download Freedoom today.
  Ranges — a 98 MB pak over a phone's wifi needs to resume. Python's stock handler ignores Range
           entirely and answers 200 with the whole file, which reads as success and is not.

No authentication, deliberately: this serves your own network, from your own machine, and the
token model in docs/specification/08-addendum-06.md is for an origin that faces the internet.
Bind it to your LAN address rather than 0.0.0.0 if that distinction matters where you are.

Usage:
    serve-shelf.py [shelf-root] [--port 8600] [--bind 0.0.0.0]
"""

from __future__ import annotations

import argparse
import mimetypes
import re
import socket
from functools import partial
from http import HTTPStatus
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

RANGE = re.compile(r"^bytes=(\d*)-(\d*)$")
CHUNK = 1 << 20


class ShelfHandler(SimpleHTTPRequestHandler):
    def end_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Range")
        self.send_header("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        self.send_header("Accept-Ranges", "bytes")
        super().end_headers()

    def do_OPTIONS(self) -> None:  # noqa: N802 — the base class spells them this way
        self.send_response(HTTPStatus.NO_CONTENT)
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802
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
    arguments = parser.parse_args()

    shelf = Path(arguments.shelf).resolve()
    if not shelf.is_dir():
        raise SystemExit(f"No shelf at {shelf}. Run tooling/data-shelf/init-shelf.sh first.")

    handler = partial(ShelfHandler, directory=str(shelf))
    server = HTTPServer((arguments.bind, arguments.port), handler)
    print(f"Serving {shelf}")
    print(f"  http://{lan_address()}:{arguments.port}/   ← reachable from your devices")
    print(f"  http://localhost:{arguments.port}/manifest.json   ← the readings, from inspect-shelf.py --manifest")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
