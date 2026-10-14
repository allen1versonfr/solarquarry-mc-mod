# Changelog

## 2.0.0

### Quarry core
- Explicit state machine: Idle, Running, Scanning, Paused, No Power, Output Full, Area Unloaded, Complete. Each state has an actionable hint in the GUI.
- Configurable rectangular area (width and length set separately, limited by the range tier: 16/32/64).
- Linear, serpentine work order: the drill moves one block at a time and every other layer runs back, so the head never jumps across the area.
- Safety rules: containers, unbreakable blocks, blocks outside the world border, spawn protection (owner online), the mod's own blocks, a skip list and the data-pack tag `solarquarry:quarry_ignored` are never mined.
- Waits (status Area Unloaded) instead of loading chunks or skipping blocks.
- The quarry records its owner on placement (used for remote control permissions).
- Save data migrates from 1.0 (square area, layer/row/col cursor); invalid saved values are clamped.

### Mining modes
- Strip Mine, Tunnel Bore (width × height × length, up to 4× the area limit), Area Clear (upward to a set height) and Ore Priority (scans and queues ore positions, mines them first, with an optional Ores Only setting and an Ore list to narrow the targets).

### Upgrades
- Upgrade effects are shown in the GUI (hover over UPGRADES): speed, energy per block, power draw, capacity, area limit, Fortune/Silk Touch, Void.
- The area follows the range limit when it was at full size; otherwise it is clamped.

### Energy
- Solar tiers behave differently: Basic 8 E/t, Advanced 40 E/t, Elite 180 E/t with about 22 E/t at night. Weather only applies where it actually rains (not in deserts), and higher tiers lose less to it.
- Right-click readouts: panels (output and reason), batteries (charge, in/out per tick, limit), cables (whole-network summary).
- The quarry GUI shows power in and power used per tick.
- The energy distribution core is a pure function, unit-tested for conservation, limits and fairness.

### Logistics
- Filter pipes: up to 3 item-tag rules (validated, with the matching item count shown), priority −5 to +5.
- Routing: Allow-list match, then priority, then distance, then a fixed face order. Bounded search, no loops.
- A short-lived route cache for machine output, invalidated whenever pipes, filters or their neighbours change.

### New items and blocks
- Remote Terminal: monitors and controls up to 32 quarries on its cable network, with a storage overview and owner/operator permissions; all requests are validated on the server.
- Ore Prospector: exact chunk ore counts and sampled quarry-volume estimates; uses durability and has a cooldown.
- Configuration Card: copy and apply quarry and filter presets (settings only, versioned, validated).

### GUI
- Quarry screen: 9-line status display (status, mode, progress, position, depth, area, speed, energy, power flow), a configuration page (mode, width, length, depth/height, Ores Only, Restart), Void/Skip/Ore lists and a buffer counter. Fits 1366×768 at the default GUI scale.
- Filter screen: Allow/Block, Item only/Exact, priority, three tag fields with validation.
- All buttons are vanilla buttons with tooltips (keyboard focus and narration work).

### Recipes
- 28 recipes. Every item and block is craftable in a crafting table and appears in the recipe book when you pick up one of its ingredients.
- New recipes: Remote Terminal, Ore Prospector, Configuration Card (×4).

### Testing
- 24 JUnit tests (mining patterns, upgrade math, solar output, energy accounting, routing rules) run with `gradlew build`.
- A dedicated-server self-test with about 120 in-world checks.
- A client game test that drives the GUIs, checks the recipe-book unlocks and saves screenshots.

## 1.0.0
- First release: 16×16 quarry with upgrades, solar panels, batteries, cables, item and filter pipes, wrench pickup and an animated gantry.
