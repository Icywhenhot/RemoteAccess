# Side Access

A lightweight, client-side quality-of-life mod for Minecraft **26.1.2** (Fabric). It lets you tab
between nearby workstations without ever closing the screen — your whole workshop behaves like tabs
in one interface.

Open one workstation normally (say, a crafting table). Side Access detects the other reachable
workstations around you and shows them as left/right navigation targets:

```
[Cartography Table]    < your open screen >    [Anvil]
```

- **A** → previous workstation
- **D** → next workstation
- Or click the on-screen icons.

## Supported workstations

Crafting Table, Anvil (+ chipped/damaged), Enchanting Table, Cartography Table, Stonecutter,
Smithing Table, Loom, Grindstone, Furnace, Blast Furnace, Smoker, Brewing Stand, Beacon.

Storage blocks (chests, barrels, shulkers, …) are intentionally **not** supported — this is for
functional crafting interfaces only.

## How it works (and why it's server-legal)

Switching does **not** reach into the world remotely. When you switch, the mod:

1. Closes the current screen (a normal container-close packet), then
2. Replays a genuine block-use interaction via `MultiPlayerGameMode.useItemOn` — the exact packet
   vanilla sends when you right-click a block.

The **server** then validates reach and opens the menu itself, exactly as if you had clicked the
block. Candidates are also clamped client-side to `reachLimit`, so you can only ever switch to a
block you could legitimately interact with. No server-side mod or cheat permissions are required;
any vanilla-behaving server accepts it. A server that blocks normal block interaction (or moves you
out of reach) will simply reject the switch.

## Configuration

Edit `config/sideaccess.json` (created on first launch).

| Key                 | Default     | Meaning                                                        |
|---------------------|-------------|----------------------------------------------------------------|
| `searchRadius`      | `5.0`       | Cubic scan radius around the player (blocks).                  |
| `reachLimit`        | `6.0`       | Hard cap on switch distance; keeps targets within reach.       |
| `prevKey`           | `"A"`       | Key for "previous workstation" (see key names below).          |
| `nextKey`           | `"D"`       | Key for "next workstation".                                    |
| `iconSize`          | `16`        | Navigation icon frame size (px).                               |
| `sortMode`          | `ANGULAR`   | `ANGULAR` (left→right sweep), `DISTANCE`, or `POSITION`.        |
| `showSwitchMessage` | `true`      | Brief action-bar message naming the workstation switched to.   |
| `showIcons`         | `true`      | Draw the left/right navigation icons.                          |
| `slideAnimation`    | `true`      | Carousel slide-in of the icons, in the swipe direction.        |
| `playSound`         | `true`      | Directional "swipe" sound on switch (higher pitch = next).     |
| `soundVolume`       | `0.5`       | Swipe sound volume (0.0 - 1.0).                                |
| `blacklist`         | `[]`        | Block IDs to ignore, e.g. `["minecraft:beacon"]`.              |

**Key names** are human-readable: single letters/digits (`"A"`, `"5"`) or
[GLFW key names](https://www.glfw.org/docs/latest/group__keys.html) without the `GLFW_KEY_`
prefix — e.g. `"LEFT"`, `"RIGHT"`, `"SPACE"`, `"LEFT_BRACKET"`, `"UP"`. Names are
case-insensitive; an unrecognized name falls back to the default and logs a warning.

## Performance

All world scanning happens only when a workstation screen opens or you switch — never per frame.
Results are cached for the life of the screen. There is no ticking world-scan logic.

## Architecture

- `workstation/WorkstationRegistry` — single source of truth for "is this a workstation?"
- `workstation/WorkstationScanner` — cubic scan, distance/reach filtering, ordering.
- `nav/NavState` — live navigation state + the legitimate switch (close → re-interact).
- `hud/SideAccessHud` — left/right icons, hover tooltips, click targets.
- `config/SideAccessConfig` — JSON config.
- `SideAccessClient` — wires Fabric screen/keyboard/mouse/use-block events together.

## License

CC0-1.0.
