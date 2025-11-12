# FastForward Engine (Fabric 1.21.6)

Fast-forward Minecraft server ticks while preserving farm fidelity. Designed for singleplayer and dedicated servers.

## Commands

- `/fastforward <ticks>`: Run fast-forward for N ticks.
- `/fastforward stop`: Cancel an active run.
- `/fastforward profile start|stop`: Measure wall-time/TPS vs in-game ticks.
- `/fastforward help`: Brief usage summary.
- `/fastpause on|off|status`: Freeze/unfreeze the world while allowing the player to move.
- Quick presets with auto-profile and automatic config restore:
  - `/fastforward quick low <ticks>`
  - `/fastforward quick medium <ticks>`
  - `/fastforward quick high <ticks>`
  - `/fastforward quick ultra <ticks>`

### Config management

- `/fastforward config show`: Pretty-print current config.
- `/fastforward config get <key>`: Read a single key.
- `/fastforward config set <key> <value>`: Update a key (use `null` or `-1` for `randomTickSpeedOverride` to clear).
- `/fastforward config preset <low|medium|high|ultra>`: Apply curated presets (saved to file).
- `/fastforward config reload`: Reload `fast-forward-engine.json`.
- `/fastforward config reset`: Reset file to defaults.

### Tunable groups

- `/fastforward config warp aggressive <true|false>`
- `/fastforward config warp budgetMs <10..10000>`
- `/fastforward config warp players <true|false>` (suppress player ticks during warp)
- `/fastforward config hopper <rate>`; `always <true|false>`
- `/fastforward config furnace <rate>`; `always <true|false>`
- `/fastforward config redstone enabled <true|false>`; `passes <1..16>`; `skipEntities <true|false>`
- `/fastforward config composter <rate>`; `always <true|false>`
- `/fastforward config dropper <shots>`; `always <true|false>`

## Recommended usage

- Conservative: `preset low` or manually set small multipliers (2–4) and disable experimental redstone.
- Throughput: `preset high`, then consider `warp players true` for headless mode (player tick suppressed during warp).
- Measure with `/fastforward profile start`, run your warp, then `/fastforward profile stop`.

## How it works

- Injects extra world ticks on the main server thread (safe/deterministic).
- Disables non-essential entity ticking during warp; essentials include items, XP, hopper minecarts (configurable behavior for players).
- Temporarily disables autosaving and optionally random ticks to reduce overhead.
- Optional targeted accelerators for hoppers/furnaces/composters/droppers.
- Experimental redstone passes accelerate scheduled block updates/comparators (optionally skipping entity ticks during these passes).

## Safety notes

- All world-modifying operations run on the main thread to avoid corruption.
- Autosave is re-enabled and a final save is performed after warp.
- Use modest accelerators and passes first; extreme settings can saturate I/O or overwhelm collection systems.

## Config file

`<instance>/config/fast-forward-engine.json` is auto-created and can be edited. Use `/fastforward config show|get|set` to inspect or change keys at runtime.


