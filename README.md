# FastForward Engine (Fabric, 1.21.6)

FastForward Engine accelerates, pauses, and profiles in‑game time. It can also selectively speed up common mechanics (hoppers, furnaces, composters, droppers) and apply experimental optimizations.

## Commands (highlights)
- `/fastforward <ticks>`: run a warp
- `/fastforward stop`: stop a running warp
- `/fastforward profile start|stop`: measure in‑game ticks vs wall time
- `/fastforward config show|get <key>|set <key> <value>|reset`
- Presets: `/fastforward config preset low|medium|high|ultra`
- Quick runs: `/fastforward quick low|medium|high|ultra <ticks>`

Preset scaling
- Multipliers are consistent across accelerators and scale linearly by powers of two:
  - LOW: x2
  - MEDIUM: x4
  - HIGH: x8
  - ULTRA: x16
- Applies to: hoppers, furnaces, composters, droppers, and experimental redstone passes.

## Client Headless (experimental)
Client headless reduces client rendering/HUD costs during warps (integrated singleplayer) to shift more CPU to ticking. It temporarily applies very cheap graphics options and restores them afterward.

Enable:
1) `/fastforward config set experimentalClientHeadless true`
2) `/fastforward config set clientHeadlessDuringWarp true`

Behavior:
- When a warp starts, it:
  - saves current options
  - sets render distance to 2, clouds off, particles minimal
  - shows a chat message: `[FastForward] Headless ON`
- When the warp ends, it:
  - restores original options
  - shows `[FastForward] Headless OFF`

Verify quickly:
- Start a warp and press F3 to see view distance = 2
- Look for the chat messages Headless ON/OFF

Notes:
- Singleplayer only (integrated server)
- Controlled by both flags: `clientHeadlessDuringWarp=true` and `experimentalClientHeadless=true`

## Experimental Features
- `experimentalAggressiveWarp`: time‑budgeted extra ticks per server tick
- `redstoneExperimentalEnabled`: extra redstone passes per server tick (with entity skipping optional)
- `experimentalBackgroundPrecompute`: precompute RNG‑heavy operations off‑thread
- `experimentalClientHeadless`: gates the client headless mode

## Performance Tips
- Consider lowering random ticks (or setting 0) during heavy warps
- Use presets or tune `batchSizePerServerTick` and `experimentalMaxWarpMillisPerServerTick`

## Safety
- The mod snapshots and restores modified settings after warps
- Coalesces duplicate scheduled block ticks to reduce redstone overhead

## JVM/GC Tuning (Server)
These recommendations aim for stable, low-GC-overhead server runs on Java 21.

- Memory
  - Prefer setting Xms equal to Xmx to avoid heap resizing pauses.
  - Example: `-Xms6G -Xmx6G` (adjust to your machine).
- GC: G1 (default and well-tested for MC)
  - Keep G1, add string deduplication and GC logs:
    - `-XX:+UseG1GC -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50`
    - `-Xlog:gc*,safepoint:file=logs/gc.log:time,uptime,tags,level`
  - Pre-touch and region sizing (optional, generally safe):
    - `-XX:+AlwaysPreTouch -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15`
- Misc
  - `-Dfile.encoding=UTF-8`
  - `-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC`
  - `--enable-native-access=ALL-UNNAMED` (harmless; some libs benefit)
- Windows power/CPU
  - Use the “High performance” or “Ultimate Performance” power plan.
  - Disable core parking if applicable.
  - Exclude your server folder from real-time AV scans to reduce IO stalls.

Example start (Windows .bat):

```bat
@echo off
title Minecraft Server 1.21.6
echo Starting Fabric server...

REM Memory
set RAM=6G

REM JVM flags (G1 tuned + GC logs)
set JAVA_FLAGS=-XX:+UseG1GC -XX:+UseStringDeduplication -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=50 ^
 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
 -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1HeapRegionSize=16M ^
 -Xlog:gc*,safepoint:file=logs/gc.log:time,uptime,tags,level ^
 -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED

REM Launch (Xms==Xmx recommended for stability)
java %JAVA_FLAGS% -Xms%RAM% -Xmx%RAM% -jar fabric-server-mc.1.21.6-loader.0.17.3-launcher.1.1.0.jar nogui
pause
```

ZGC note: Java 21’s ZGC is low-latency and works well on some setups, but G1 remains the safer default for MC. If you experiment:
- Replace G1 with: `-XX:+UseZGC -XX:+ZGenerational`
- Remove G1-specific flags (MaxGCPauseMillis, G1ReservePercent, G1HeapRegionSize, etc.)
- Monitor with `-Xlog:gc*,safepoint`

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

### Lag fixing (Can't keep up!)

- `/fastforward fixlag on|off`: Enable/disable extra internal server ticks to stay ahead of real time.
- `/fastforward fixlag passes <count>`: Extra ticks to run each server tick (default 20 when enabled).
- `/fastforward fixlag only-no-players <true|false>`: If true, skip lag-fixing while players are online (reduces rubber‑band risk).
- `/fastforward fixlag status`: Show current settings.

Notes:
- Works continuously, even when no warp is running.
- Uses the server’s own tick routine on the main thread; safe but can increase CPU load.
- During warp, lag warnings are suppressed automatically.

### Ticking Anchors (keep areas fully simulated)

Keep regions ticking (entities and block entities) even with no players online.

- `/fastforward anchor add here <radius>`: Create an anchor centered on your current chunk, radius 1–32 chunks.
- `/fastforward anchor list`: List all anchors.
- `/fastforward anchor remove <id>`: Remove one anchor by id.
- `/fastforward anchor clear`: Remove all anchors.

Notes:
- The mod prefers Region Tickets for full simulation and gracefully falls back to force‑loading when tickets aren’t available.
- Anchors are persisted in the config and re‑applied automatically on server tick.

### Player movement helpers

- `/fastforward player infinite-speed <true|false>`: Disable server move‑speed checks to avoid rubber‑banding at high speeds.
- `/fastforward player suppress-move-log <true|false>`: Hide “moved too quickly!” spam from the console at runtime.
- `/fastforward player status`: Show both flags.

Use with care on public servers.

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

## Engine optimizations

- Redstone coalescing: duplicate scheduled block ticks are coalesced during warp to cut redundant work.
- Save coalescing: intermediate `DimensionDataStorage` saves are deferred during warp; a single final save runs after.
- Hopper minecart coverage:
  - Profiling counts hopper minecart pulls/pushes by inventory deltas each tick.
  - Push attribution prefers a hopper below (and its facing destination) or a direct container below (chest/shulker/barrel).
  - Hopper boost (`hopperTransfersPerTick`) applies to hopper minecarts by invoking extra vanilla “suck” cycles safely.

Both are automatic; no configuration needed.

## Profiling counters

When profiling or after quick runs, the summary includes counters:

- entities+
- smelted
- composted
- chest+ (items inserted)
- crafted+ (crafter outputs only)
- hopper pull+ / hopper push+
- shulker+ (items inserted)
- dispenser shots
- dropper drops

Example tail: `| entities+ 123 | smelted 456 | composted 7 | chest+ 89 | crafted+ 42 | hopper pull+ 300 | hopper push+ 295 | shulker+ 12 | dispenser shots 5 | dropper drops 3`

## Safety notes

- All world-modifying operations run on the main thread to avoid corruption.
- Autosave is re-enabled and a final save is performed after warp.
- Use modest accelerators and passes first; extreme settings can saturate I/O or overwhelm collection systems.

## Config file

`<instance>/config/fast-forward-engine.json` is auto-created and can be edited. Use `/fastforward config show|get|set` to inspect or change keys at runtime.


