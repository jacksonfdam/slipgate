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
| ‹ / › | `PreviousWeapon` / `NextWeapon` | `;` / `'` — bound by the platform layer, because `key_prevweapon` and `key_nextweapon` ship as zero: vanilla chose a weapon by its number, and cycling is a source port's addition |
| MAP | `Map` | Tab — `key_map_toggle`'s default. It was F8 until it was checked against a running game: F8 is the messages toggle, so the button turned messages off |
| MENU | `Menu` | Escape |
| ENTER | `Confirm` | Enter — what the engine's menus read as a choice |

Menus are therefore fully operable by touch: MENU opens them (Escape), the movement pad
walks them (arrow keys), ENTER selects, MENU backs out.

### The controls one engine has and another does not

Heretic adds an inventory and flight, which no `GateAction` names — they belong to one engine rather
than to every engine, which is what `InputProfile.extensions` is for. A gate declares them, the pad
draws a labelled button for each along the bottom edge, and the gate's own key map is what turns a
press into a key. `games/corvus/.../WasmSession.kt` holds Heretic's, at the defaults
`m_controls.c` gives them:

| Touch control | Extension | Heretic key |
|---|---|---|
| ITEM ‹ / ITEM › | `corvus.inventory.previous` / `corvus.inventory.next` | `[` / `]` |
| USE ITEM | `corvus.inventory.use` | Enter — the same key its menus read as a choice |
| FLY UP | `corvus.fly.up` | Page Up |
| FLY DOWN | `corvus.fly.down` | Insert |

Hexen's are the same five, at the same keys, with `korax.` in place of `corvus.` — and Hexen adds the
one action the Raven engines gained with it: JUMP, at `/`, which is `key_jump`'s default.

`CorvusGateTest` and `KoraxGateTest` each assert that every extension the profile declares has a key
and every key has an extension, because a drawn button with no binding is silent rather than broken.

Heretic's look up and down are not bound yet: nothing produces a look axis — the pad has one wheel
and the session reads movement only — and the game plays without free look, as it did on a
keyboard.

Physical keyboards map through `ControlKey` in `host/controls/.../KeyboardBindings.kt`,
which carries the same defaults (arrows/WASD, Ctrl, space, comma/period, tab, Escape,
Enter).

## Reference: the classic default keymaps

The default layout for the original 90s id Software / Raven engine games (Doom, Heretic,
Hexen, Quake) relied entirely on the keyboard: movement on the arrow keys or numeric
keypad, actions across the main board. Modern source ports such as GZDoom remap movement
to WASD. See the Chocolate Doom keyboard configuration reference:
https://www.chocolate-doom.org/wiki/index.php/Setup/Keyboard_configuration

Vanilla defaults, shared across the family:

| Function | Key |
|---|---|
| Move forward / backward | Up / Down arrows |
| Turn left / right | Left / Right arrows |
| Strafe (slide) modifier | `Alt` + Left/Right (or `,` and `.`) |
| Fire weapon | `Ctrl` |
| Use / open | `Space` (`E` in modern source ports) |
| Run / fast | `Shift` |
| Map toggle | `Tab` |
| Pause / menu | `Esc` |

Game-specific keys the later gates will need:

- **Heretic and Hexen — inventory / artifacts**: `Enter` uses the current item;
  `[` / `]` (or `Shift` + Left/Right) select items.
- **Hexen and Quake — jumping**: `Space` (Doom and vanilla Heretic have no jump key).
- **Quake — look up/down and centring**: `Page Up` / `Page Down`, `End` to centre the view.

These tables are the source of truth when `corvus`, `korax` and `chthon` declare their
`InputProfile`s and key translations — inventory needs `InputFrame.extensions` or new
`GateAction`s (Confirm already covers Heretic/Hexen's "use current item" key), and jump
maps differently per engine, which is exactly why translation lives with each gate.

## Deferred to the settings build (20c) and the gamepad visual layer (20d)

- **Per-gate remapping** in Settings — let the player rebind touch controls and keys per
  game. `InputProfile` is the seam; the bindings tables above are the data to expose.
- Layout edit mode (drag to reposition, pinch to resize, per-control opacity, reset,
  save per gate), SDF button glyphs, idle fade to 35% after 4 s.
