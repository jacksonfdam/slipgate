# Screenshots

Captures from a real device, kept here so a pull request or the README can point at what the app
actually does rather than describe it.

`android-mars-running.jpg` shows Freedoom, the freely licensed replacement the app offers to
download. Freedoom's data is distributed under a three-clause BSD licence by the Freedoom project;
the game data itself is never part of this repository, and this is a picture of it running.

`web-gate-menu.png` shows the menu a player opens over a running gate, captured from the web build.
The gate behind it is the test pattern, which is what that build ships until a browser-side engine
driver exists.

`web-attract-background.png` shows the attract background behind the rack, captured from the web
build at the Minimal tier — the tier that is given no noise octaves at all, so what is visible there
is the shape the fire keeps when it cannot afford to burn.

`web-needs-data-mask.png` shows the rack with one gate installed and one not: the Doom card keeps its
portrait behind a wash and a still speckle, and the test pattern beside it is clean. No game data is
in the picture — the portrait is a shader, and that build had no IWAD.

`web-launch-warp.png` is the launch transition: streaks pulling toward the centre, the tear walking
down the frame, and the rack still behind it being drawn in. Captured with the transition held far
longer than it ships at — it runs for 900 ms, which is too fast to photograph through a debugging
protocol.


## The launcher and the gates

The eight captures below were taken from the Android build on an emulated Pixel 6 Pro running
API 36, in landscape, with the tube effect on and the detail tier left on Automatic. The game data
in them is Freedoom and Blasphemer, downloaded by the app's own first-run flow — the same data the
`android-mars-running.jpg` photo above shows, and the only kind that may be pictured here.

`android-rack.jpg` is the rack with two gates ready and two waiting: Mars and Corvus carry their
portraits at full strength, while Korax and Macil sit behind the wash and speckle a gate wears when
it has no data. The banner above the row belongs to whichever card holds the focus.

`android-mars-freedoom.jpg` is Freedoom running under the `mars` gate, with the virtual pad around
it — the stick to the left, the two turn buttons and fire to the right, and the utility row at the
top for use, map and the automap toggle.

`android-corvus-blasphemer.jpg` is Blasphemer running under the `corvus` gate. Heretic's status bar
is a different shape from Doom's, which is one reason the pad is laid out around the picture rather
than over it.

`android-gate-menu.jpg` is the menu a player opens over a running gate: resume, leave, and the
display settings that are worth changing while a game is in front of them. The gate behind it has
stopped stepping and holds the frame it stopped on.

`android-settings.jpg` is the Settings screen, coloured by the gate the player last had focus on.
Detail is the measured tier and the four it can be overridden to; picture shape is how a 320 by 200
frame meets a modern screen; the tube effect and its curvature follow.

`android-credits.jpg` is the Credits screen, which names the architecture, the engines, the freely
licensed data and the licences all of them carry.

`android-free-replacement.jpg` is the data screen for a gate that has a free replacement to offer —
Doom, here — with the download and the file picker side by side and the replacement named as a
replacement.

`android-own-copy-only.jpg` is the same screen for Hexen, which has no free replacement: one route
in, and a sentence saying why.
