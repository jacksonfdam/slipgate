#!/usr/bin/env python3
"""Reads the local game-data shelf and says what is actually on it.

Files are identified by their contents, never by their names, which is the rule the app itself
follows: a file called pak0.pak that is Hexen II 1.03 is named as 1.03, and a file called anything
at all that is the registered 1.11 pak is accepted. The vocabulary is `WadInspection.kt`'s — kind,
role, flavour — so a reading here and a reading in the app are the same sentence.

This is a convenience for staging a shelf, not the authority. The app's own inspector decides at
install time; this exists so a mistake is visible while a 98 MB copy is still fresh in mind rather
than at the end of a first-run flow on a phone.

Usage:
    inspect-shelf.py [shelf-root] [--manifest]

--manifest rewrites <shelf>/manifest.json: one entry per file with its reading, size and sha256.
Exits non-zero when a file is present but unusable.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import struct
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import BinaryIO

# Quake's CRC-16: CCITT polynomial, initialised to 0xffff, no final xor (engine/h2shared/crc.c).
# uHexen2 identifies a pak by the number of files in its directory and the CRC of the directory
# bytes, which is what makes an exact version verdict possible without hashing 98 MB.
_CRC_TABLE = []
for _index in range(256):
    _value = _index << 8
    for _ in range(8):
        _value = ((_value << 1) ^ 0x1021) & 0xFFFF if _value & 0x8000 else (_value << 1) & 0xFFFF
    _CRC_TABLE.append(_value)


def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for byte in data:
        crc = ((crc << 8) & 0xFFFF) ^ _CRC_TABLE[(crc >> 8) ^ byte]
    return crc


# uHexen2's own table, from engine/h2shared/quakefs.c. The last column is this project's policy
# rather than uHexen2's: the gate needs the registered 1.11 data, and everything else is named so
# that a refusal can say why.
HEXEN2_PAKS = {
    (696, 34289, 22704056): ("registered v1.11 pak0", True),
    (523, 2995, 75601170): ("registered v1.11 pak1", True),
    (245, 1478, 49089114): ("Portal of Praevus pak3", True),
    (797, 22780, 27750257): ("demo v1.11 pak0", False),
    (697, 9787, 22720659): ("OEM v1.10 pak0, Continent of Blackmarsh", False),
    (183, 4807, 17742721): ("OEM v1.10 pak2", False),
    (697, 53062, 21714275): ("retail v1.03 pak0 — needs Raven's 1.11 patch", False),
    (525, 47762, 76958474): ("retail v1.03 pak1 — needs Raven's 1.11 patch", False),
    (701, 20870, 23537707): ("demo v0.42 pak0", False),
    (697, 43990, 22719295): ("OEM v1.08 pak0", False),
    (183, 43596, 17739969): ("OEM v1.08 pak2", False),
    (102, 41062, 10780245): ("HexenWorld pak4", False),
    (98, 25864, 10678369): ("HexenWorld pak4 v0.11", False),
    (40, 48258, 3357888): ("HexenWorld pak4 v0.09", False),
}

EPISODE_MAP = re.compile(rb"^E(\d)M\d$")
NUMBERED_MAP = re.compile(rb"^MAP\d\d$")
QUAKE_MAP = re.compile(r"^maps/e(\d)m\d\.bsp$")


@dataclass
class Reading:
    """What a file is, in the app's own vocabulary."""

    kind: str  # IWAD, PWAD or PACK
    role: str  # Bootable or AddOn
    flavour: str | None  # DoomEpisodic, DoomMapped, Heretic, Hexen, Strife, Quake, HexenII
    episodes: int = 0
    maps: int = 0
    lumps: int | None = None  # WADs count lumps
    files: int | None = None  # paks count files
    version: str | None = None  # only Hexen II data can be pinned to a release
    note: str | None = None
    usable: bool = True
    holds: str | None = None  # set when the reading came from inside an archive

    def sentence(self) -> str:
        parts = [self.flavour or "no engine named"]
        if self.episodes:
            parts.append(f"{self.episodes} episode{'s' if self.episodes > 1 else ''}")
        if self.maps:
            parts.append(f"{self.maps} map{'s' if self.maps > 1 else ''}")
        parts.append(f"{self.lumps} lumps" if self.lumps is not None else f"{self.files} files")
        if self.version:
            parts.insert(1, self.version)
        line = f"{self.kind}, {self.role}, " + ", ".join(parts)
        if self.holds:
            line = f"archive holding {self.holds} — {line}"
        return f"{line}. {self.note}" if self.note else line

    def entry(self, shelf: str, path: Path, url: str) -> dict:
        return {
            "shelf": shelf,
            "name": path.name,
            "url": url,
            "kind": self.kind,
            "role": self.role,
            "flavour": self.flavour,
            "episodes": self.episodes,
            "maps": self.maps,
            "lumps": self.lumps,
            "files": self.files,
            "version": self.version,
            "size": path.stat().st_size,
            "sha256": sha256_of(path),
        }


@dataclass
class Unreadable:
    detail: str
    usable: bool = field(default=False, init=False)

    def sentence(self) -> str:
        return self.detail


def read_wad(handle: BinaryIO, size: int) -> Reading | Unreadable | None:
    handle.seek(0)
    header = handle.read(12)
    if len(header) < 12 or header[:4] not in (b"IWAD", b"PWAD"):
        return None
    count, directory = struct.unpack("<ii", header[4:])
    if count <= 0 or directory < 0 or directory + count * 16 > size:
        return Unreadable("a WAD whose lump directory points outside the file — a truncated copy looks like this")

    handle.seek(directory)
    table = handle.read(count * 16)
    names = {table[index * 16 + 8 : index * 16 + 16].split(b"\0")[0].upper() for index in range(count)}

    kind = header[:4].decode()
    bootable = b"PLAYPAL" in names
    episodes = len({match.group(1) for match in (EPISODE_MAP.match(name) for name in names) if match})
    maps = sum(1 for name in names if NUMBERED_MAP.match(name))

    if not bootable:
        # No palette of its own, so it loads over a game rather than booting one — and which game
        # is not something the contents settle. hexdd.wad is Hexen data under an IWAD signature
        # with MAPxx names, which is also what a Doom II map pack looks like.
        note = "no maps either, so the app rejects it" if not maps and not episodes else None
        return Reading(kind, "AddOn", None, episodes, maps, lumps=count, note=note)

    if b"XLATAB" in names:
        flavour = "Strife"
    elif b"TINTTAB" in names:
        flavour = "Hexen" if maps else "Heretic"
    elif episodes:
        flavour = "DoomEpisodic"
    elif maps:
        flavour = "DoomMapped"
    else:
        flavour = None

    return Reading(kind, "Bootable", flavour, episodes, maps, lumps=count)


def read_pak(handle: BinaryIO, size: int) -> Reading | Unreadable | None:
    handle.seek(0)
    header = handle.read(12)
    if len(header) < 12 or header[:4] != b"PACK":
        return None
    directory, length = struct.unpack("<ii", header[4:])
    if length <= 0 or length % 64 != 0 or directory < 0 or directory + length > size:
        return Unreadable("a pak whose file directory points outside the file — a truncated copy looks like this")

    handle.seek(directory)
    table = handle.read(length)
    count = length // 64
    crc = crc16(table)
    names = {table[index * 64 : index * 64 + 56].split(b"\0")[0].decode("latin-1").lower() for index in range(count)}

    role = "Bootable" if "gfx/palette.lmp" in names else "AddOn"
    maps = sum(1 for name in names if name.startswith("maps/") and name.endswith(".bsp"))
    hexen2 = any(name.startswith("midi/") for name in names) or "maps/village1.bsp" in names

    known = HEXEN2_PAKS.get((count, crc, size))
    if known is not None:
        version, accepted = known
        return Reading(
            "PACK",
            role,
            "HexenII",
            maps=maps,
            files=count,
            version=version,
            note=None if accepted else "this gate needs the registered 1.11 data",
            usable=accepted,
        )

    if hexen2:
        return Reading(
            "PACK",
            role,
            "HexenII",
            maps=maps,
            files=count,
            version=f"unrecorded — crc {crc}",
            note="modified, or a release nobody wrote down; the gate will refuse it",
            usable=False,
        )

    episodes = sorted({match.group(1) for match in (QUAKE_MAP.match(name) for name in names) if match})
    if episodes or "maps/start.bsp" in names:
        # Episode 1 alone is what pak0 holds in both the shareware and the registered release, so
        # this says where the rest are rather than guessing which release this came from. All four
        # in one pak is the 2021 re-release, which repacks everything the two 1996 paks held and
        # adds its own — mapdb.json and the weapon wheel are its fingerprints.
        remaster = "mapdb.json" in names or "wwheel.txt" in names
        if remaster:
            note = "2021 re-release: everything the two 1996 paks held, in one file, plus its own additions"
        elif episodes == ["1"]:
            note = "the other episodes are in pak1"
        else:
            note = None
        return Reading("PACK", role, "Quake", episodes=len(episodes), maps=maps, files=count, note=note)

    return Reading("PACK", role, None, maps=maps, files=count, note=f"crc {crc}: no engine's fingerprints in it", usable=False)


def read(path: Path) -> Reading | Unreadable | None:
    """What this file is, or None when it is not game data at all."""
    size = path.stat().st_size
    with path.open("rb") as handle:
        reading = read_wad(handle, size) or read_pak(handle, size)
    if reading is not None:
        return reading

    if not zipfile.is_zipfile(path):
        return None

    # The free replacements are published inside a zip, and the app takes a named entry out of one,
    # so a zip on the shelf is read by what it holds.
    with zipfile.ZipFile(path) as archive:
        members = [
            member
            for member in archive.infolist()
            if not member.is_dir() and member.filename.lower().endswith((".wad", ".pak"))
        ]
        if not members:
            return None
        member = max(members, key=lambda entry: entry.file_size)
        inner = io.BytesIO(archive.read(member))
        held = read_wad(inner, member.file_size) or read_pak(inner, member.file_size)

    if held is None or isinstance(held, Unreadable):
        return Unreadable(f"an archive holding {member.filename}, which is not readable as game data")
    held.holds = member.filename
    return held


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


# What each gate cannot boot without. Doom-family gates take any one IWAD; the Quake family needs
# named paks in the engine's own directories.
REQUIRED = {
    "mars": ["*.wad"],
    "corvus": ["*.wad"],
    "korax": ["*.wad"],
    "chthon": ["id1/pak0.pak", "id1/pak1.pak"],
    "eidolon": ["data1/pak0.pak", "data1/pak1.pak"],
}


def missing_for(gate: str, directory: Path, readings: dict[str, Reading]) -> list[str]:
    missing = [pattern for pattern in REQUIRED[gate] if not any(True for _ in directory.glob(pattern))]

    # Quake's registered game is two paks in 1996 and one in the 2021 re-release, so what decides
    # whether the data is complete is the four episodes rather than the file count. A pak0 carrying
    # all four does not need a pak1 beside it, and saying otherwise would send a player looking for
    # a file their own copy does not have.
    if gate == "chthon" and missing == ["id1/pak1.pak"]:
        pak0 = readings.get("chthon/id1/pak0.pak")
        if pak0 is not None and pak0.episodes == 4:
            return []
    return missing

SKIP = {"README.md", "NOTES.txt", "manifest.json", ".DS_Store"}


def main() -> int:
    repository = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shelf", nargs="?", default=str(repository / "slipgate-server"))
    parser.add_argument("--manifest", action="store_true", help="rewrite manifest.json")
    arguments = parser.parse_args()

    shelf = Path(arguments.shelf).resolve()
    if not shelf.is_dir():
        print(f"No shelf at {shelf}. Run tooling/data-shelf/init-shelf.sh first.")
        return 2

    print(f"Shelf: {shelf}\n")
    unusable = 0
    entries = []
    readings: dict[str, Reading] = {}

    for path in sorted(shelf.rglob("*")):
        if not path.is_file() or path.name in SKIP:
            continue
        relative = path.relative_to(shelf)
        reading = read(path)

        if reading is None:
            print(f"  · {relative}\n      not game data this can read ({path.stat().st_size:,} bytes)")
            continue

        mark = "✓" if reading.usable else "✗"
        print(f"  {mark} {relative}\n      {reading.sentence()}")
        if not reading.usable:
            unusable += 1
        elif isinstance(reading, Reading):
            entries.append(reading.entry(relative.parts[0], path, "/" + relative.as_posix()))
            readings[relative.as_posix()] = reading

    print()
    for gate in REQUIRED:
        directory = shelf / gate
        if not directory.is_dir():
            continue
        missing = missing_for(gate, directory, readings)
        print(f"  {gate}: " + (f"still needs {', '.join(missing)}" if missing else "complete"))

    if arguments.manifest:
        target = shelf / "manifest.json"
        target.write_text(json.dumps({"files": entries}, indent=2) + "\n")
        print(f"\n{target.name} rewritten: {len(entries)} files")

    if unusable:
        print(f"\n{unusable} file(s) are present but not usable. The readings above say why.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
