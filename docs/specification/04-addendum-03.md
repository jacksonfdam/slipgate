# Addendum 03 — Custom maps and the launch options behind them

This amends [01-foundation.md](01-foundation.md), which described a shelf holding exactly
one file per gate: the IWAD it boots from. That was never enough. Doom's thirty years of
map packs are the reason most people still install it, and a launcher that cannot load one
is a launcher for three games rather than for a genre.

## What landed

A shelf now holds a game and any number of add-ons beside it.

**Role is read from the palette, not the signature.** A file carrying `PLAYPAL` supplies
every colour the screen needs and can stand alone; a file without one borrows the colours
of whatever it loads over. That is the whole rule, and it resolves two long-standing
quirks the signature gets wrong in both directions: Chex Quest ships a complete game under
a `PWAD` header, and Hexen's Deathkings expansion ships an add-on under an `IWAD` one. The
engines have always looked at contents rather than headers here; so does Slipgate.

**Add-ons are stored under an `addon.` prefix.** No platform in the target set allows a
separator in a stored name, so the shelf is one flat namespace and the marker has to live
in the name. A prefix cannot drift out of step with the files the way an index would.

**Add-ons carry no flavour.** A map replacement holding `MAP01` is a Doom add-on, a Hexen
add-on or a Strife add-on, and nothing inside it decides which. The gate it was installed
on is better evidence than a guess, and the gate already knows.

**Strife has a flavour of its own.** It is Doom's engine with Rogue's translucency table,
so `XLATAB` separates it exactly the way `TINTTAB` already separates Raven's two. Before
this, the Doom gate accepted `strife1.wad` as Doom data and then could not boot it.

**Gates pass what is on the shelf to `-file`**, in name order, so two add-ons replacing the
same map override each other the same way every time.

## What is queued

### Launch options, after `dsda-launcher`

[dsda-launcher](https://github.com/Pedro-Beirao/dsda-launcher) is the reference for what a
launcher should let a player set before a gate opens. Its window is one screen: IWAD,
compatibility level, episode and level, difficulty, the four gameplay toggles, a WAD list,
a demo list, and a free-text argument box. Slipgate should reach the same place through
Settings and the gate card rather than a modal, but the set of things a player can decide
is the right set.

In rough order of what each one is worth against what it costs:

| Option | Engine switch | Notes |
|---|---|---|
| Warp to episode and level | `-warp` | The inspector already collects `mapNames`, so the picker can offer exactly the maps the shelf holds rather than a pair of free number fields. This is the one worth doing first: installing a map pack and then playing to it through the original campaign is the current experience. |
| Difficulty | `-skill 1..5` | Trivial once a launch-options surface exists. |
| No monsters, fast monsters, respawning monsters | `-nomonsters`, `-fast`, `-respawn` | Three booleans, no host work beyond passing them. |
| Add-on load order | argument order after `-file` | Currently alphabetical. Two packs touching the same lump need the player to decide, which means a reorderable list. |
| Per-gate add-on enable/disable | which names reach `-file` | Installing and loading are different decisions; a player with five packs wants to pick two. Cheaper than it sounds — it is a stored set of names, not another copy of the files. |
| Demo playback and recording | `-playdemo`, `-record` | Playback already exists in the host as `EngineInstance.playDemo`; what is missing is a list of the demos a shelf holds and something to draw it. Recording needs the written file to survive session close, the way saves already do. |
| Compatibility level | `-complevel` | Chocolate Doom is vanilla-faithful by construction and does not take this switch; it belongs to Boom-family ports. Out of scope unless a second engine family is ever added, and the gate card should not offer a control that cannot work. |
| Free-text arguments | passed verbatim | Useful for exactly the people who do not need a launcher. Worth having behind a diagnostics disclosure, not on the main surface. |
| Resolution and fullscreen | — | Slipgate already answers these through the quality tier and scaling mode. No new control. |

The host is mostly ready for this: `startEngine` already takes an arbitrary argument list,
and `engineArguments` in `host/backend/wasm` is the single place a switch has to be added
so that all three gates get it. What does not exist is a surface for a player to make the
choices on, and somewhere to keep them per gate between sessions.

### Shareware refuses add-ons

Chocolate Doom stops with `You cannot -file with the shareware version` and Heretic does
the same. Inside wasm that is a fatal error rather than a message. The gate should refuse
before boot, using what the inspector already knows — a Doom IWAD with one episode is
shareware — rather than letting the engine die.

### A Strife gate

Chocolate Doom 3.1.1 builds `chocolate-strife`, so the fourth gate is a build target and a
key map rather than new architecture. It needs `strife1.wad`, optionally `voices.wad`
beside it, and an input profile with Strife's inventory and dialogue keys. No free data
replacement exists, so its card would say user-supplied only, the way Hexen's does.
