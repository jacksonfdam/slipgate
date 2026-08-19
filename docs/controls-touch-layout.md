# Touch controls: layout and key emulation

## Where the layout comes from

The virtual gamepad follows the ergonomic zones of Microsoft's game-streaming touch layout
guidance (Touch Adaptation Kit designer's guide):

https://learn.microsoft.com/en-us/gaming/gdk/docs/features/common/game-streaming/building-touch-layouts/game-streaming-tak-designers-guide?view=gdk-2604

The zones, applied to Slipgate:

- **Left wheel** — the movement pad (160 dp), bottom-left, under the resting left thumb.
  It reports direction, not magnitude; the session turns it into the engine's arrow keys.
- **Right wheel** — combat actions on an arc around the resting right thumb:
  the primary action innermost at the corner (Fire, 80 dp), secondary beside it (Use),
  less-frequent actions on the outer arc (weapon previous/next; Jump/Crouch for gates
  that declare them).
- **Upper slots** — utility actions that must never be hit mid-combat: Menu, Map, Enter,
  in the top-right slot row.
- Every touch target is at least 48 dp; idle buttons dim, pressed buttons brighten.

Buttons come from the gate's own `InputProfile`: Doom shows six actions plus Confirm,
and no gate carries another's clutter.

## Keyboard emulation

The touch layer emits normalised `GateAction`s; each gate translates them into the key
codes its engine actually reads (`games/mars/.../WasmSession.kt` for Doom). The result is
that the touch layout drives the engine exactly as its keyboard defaults would:

| Touch control | GateAction | Doom key |
|---|---|---|
| Movement pad | movement axis | arrow keys (turn, not strafe — Doom's default) |
| FIRE | `Fire` | right Ctrl |
| USE | `Use` | space |
| ‹ / › | `PreviousWeapon` / `NextWeapon` | `;` / `'` |
| MAP | `Map` | F8 (automap toggle in the default bindings) |
| MENU | `Menu` | Escape |
| ENTER | `Confirm` | Enter — what the engine's menus read as a choice |

Menus are therefore fully operable by touch: MENU opens them (Escape), the movement pad
walks them (arrow keys), ENTER selects, MENU backs out.

Physical keyboards map through `ControlKey` in `host/controls/.../KeyboardBindings.kt`,
which carries the same defaults (arrows/WASD, Ctrl, space, comma/period, tab, Escape,
Enter).

## Deferred to the settings build (20c) and the gamepad visual layer (20d)

- **Per-gate remapping** in Settings — let the player rebind touch controls and keys per
  game. `InputProfile` is the seam; the bindings tables above are the data to expose.
- Layout edit mode (drag to reposition, pinch to resize, per-control opacity, reset,
  save per gate), SDF button glyphs, idle fade to 35% after 4 s.
