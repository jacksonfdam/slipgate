#!/usr/bin/env bash
# Creates or repairs the local game-data shelf: the directories a player drops their own files into.
#
# The shelf is never tracked — .gitignore has `slipgate-server/`, and the file extensions above it
# cover the data itself. What is tracked is this script, so the layout is reproducible on another
# machine and reviewable without anyone's game data being visible.
#
# One shelf per gate at the top level, plus `addons/` for files that load over a game and
# `unsupported/` for data no gate runs yet. The two Quake-family gates keep the engine's own
# directory names inside their shelf, so an install folder copies straight in.
#
# Existing files are never touched: no data is moved, and README.md and manifest.json are left
# alone. The NOTES.txt in the pak shelves is written only if it is not already there.
#
# Usage:  ./init-shelf.sh [shelf-root]
# Default shelf root: <repository>/slipgate-server
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
repository="$(cd "${here}/../.." && pwd)"
shelf="${1:-${repository}/slipgate-server}"

for directory in \
    mars corvus korax macil \
    chthon/id1 \
    eidolon/data1 eidolon/portals \
    addons unsupported; do
    mkdir -p "${shelf}/${directory}"
done

note() {
    [ -f "$1" ] && return 0
    cat > "$1"
}

note "${shelf}/chthon/NOTES.txt" <<'EOF'
Quake, in the engine's own layout — a Quake install folder copies straight in.

  id1/pak0.pak   episode 1 and the shared data
  id1/pak1.pak   episodes 2 to 4

Both are required: this gate runs the registered game. Shareware Quake is pak0 alone, and it is
recognised so the refusal can say what is missing rather than guessing.

Episode 1 alone is what pak0 holds in both releases, so a reading of "episodes 1" against pak0 is
correct and says nothing about which release it came from. Whether pak1 is present is what
matters, and inspect-shelf.py reports that per gate.
EOF

note "${shelf}/eidolon/NOTES.txt" <<'EOF'
Hexen II, in the engine's own layout — a Hexen II install folder copies straight in.

  data1/pak0.pak    696 files, 22,704,056 bytes, md5 c9675191e75dd25a3b9ed81ee7e05eff
  data1/pak1.pak    523 files, 75,601,170 bytes, md5 c2ac5b0640773eed9ebe1cda2eca2ad0
  portals/pak3.pak  Portal of Praevus, optional.
                    245 files, 49,089,114 bytes, md5 77ae298dd0dcd16ab12f4a68067ff2c3

Both data1 paks are required, and both must be at version 1.11 — the state Raven's own patch
leaves them in, and what the numbers above describe. Paks straight off the 1997 discs are 1.03;
they are recognised, named as 1.03 and refused, because uHexen2 treats pre-1.11 data as
unsupported.

Patching them is Raven's h2patch, run outside this project: its deltas are derived from the game
data, and nothing derived from game data belongs in this repository or in anything it downloads.
EOF

echo "Shelf ready at ${shelf}"
printf '  %s\n' \
    "mars/ corvus/ korax/ macil/    one IWAD each, plus anything else that gate can boot" \
    "chthon/id1/               pak0.pak and pak1.pak" \
    "eidolon/data1/ portals/   pak0.pak, pak1.pak, and pak3.pak for the mission pack" \
    "addons/                   files that load over a game rather than booting one" \
    "unsupported/              valid data no gate runs yet"
echo
echo "Then: tooling/data-shelf/inspect-shelf.py --manifest"
