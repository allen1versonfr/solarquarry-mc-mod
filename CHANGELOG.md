# Changelog

## 3.0.0 (2026-10-15)

First full release of 3.0; the same features as 3.0.0-beta.3 (earlier betas: 3.0.0-beta.1 to beta.3).

### Quarry
- **Frame you can bump into:** posts, rails and end beams are solid (no blocks or entities are placed); steel joints, base plates, braces and status lights on the posts.
- **3D-printer style gantry:** a bridge rides both side rails on two trucks, and the carriage runs along the bridge, so the drill moves on two axes.
- **Legs reach the ground:** a quarry placed in the air grows its legs down to the first solid block (up to 96), and a support column holds up the machine.
- **Drill stays on the frame** after a range change (it no longer floats outside the work area).
- **Torches:** put torches in the new torch slot and the quarry lights the pit walls every 4 layers (tunnels: every 4 slices).
- **Experience:** mining experience is collected by the drill; take it with the XP button in the quarry screen.
- **Measured speed** in blocks per second (10 s average), upgrade counts shown as `n/max`, efficiency limit 4.
- **Ore Prospector** lists every ore found (no "and N more").

### New upgrades
- **Liquid Remover Upgrade:** a second arm on the gantry pumps liquid ahead of the drill. Sources go through **Fluid Pipes** into a **Liquid Tank** (64 buckets, empty it with buckets); with a Void Upgrade the liquid is deleted. The quarry only reports Complete after a final pass finds no liquid in the pit.
- **Auto-Smelter Upgrade:** ores and raw metals are smelted (furnace recipes) before they reach the buffer, for 50 E per item (less with Efficiency). Fortune gives more ingots; Silk Touch ore blocks smelt to one ingot; void-listed items are deleted first. Smelting experience is stored with the mining experience.

### Logistics and storage
- **Pipe tiers:** Reinforced (2x speed, 32 per batch) and Advanced (5x speed, 64 per batch) Item Pipes.
- **Sorting Terminal:** register chests on a pipe network and choose what each receives: listed items and tags only, everything except listed, or fallback for leftovers and overflow; priorities; free-space display; settings persist and recover when chests are broken or unloaded.
- **Storage Crates:** 54, 108 and 162 slots (pages), pipe and hopper compatible, picked up full with the wrench.

### Machines
- **Super Smelter:** electric furnace with three lanes at 4x furnace speed, 16 E/t per working lane and nothing while idle. Inputs accept only smeltable items; automation inserts from the top and sides and extracts from the bottom.
- **Fabrication Bench:** a workbench with a searchable catalogue of every Solar Quarry recipe. Pick an item to see what it needs (have / need), then craft it straight from your inventory. The normal crafting table recipes still work too.

- **Tree Farm:** plants saplings on a grid in front of it, uses bone meal (only when a sapling can take it), and a drone fells grown trees and collects the real drops; saplings from the leaves refill its sapling slots. Trees touching built blocks (log cabins, beams) are never cut. Growth Upgrade (extra growth ticks) and Harvest Upgrade (cuts the leaves too); quarry-only upgrades do not fit.

### Drones
- **Security Upgrade:** when hostile mobs come near the quarry it deploys up to 3 drones that fly out and zap them, paid from the quarry's energy (upkeep and per hit). They never touch players, pets, villagers, the shop guard or neutral mobs, are recalled when things calm down or power runs out, and vanish with their quarry (never saved, so no duplicates after reloads).
- **Miner Drone:** put it in the quarry's drone slot; once the quarry's own area is finished, it tunnels from the bottom of the pit to nearby ores and sends them through the quarry's output, using the quarry's power and upgrades.

### Trader
- On a player's first visit the Solar Quarry trader hands over a starter kit (parts for the first quarry, power, pipes, a wrench and a Fabrication Bench) once; if the inventory is full the rest arrives as soon as there is room.
- His shop (sign, lanterns, supply chest, bench) is built once per world near spawn, with a guard who fights hostile mobs only. He sells parts and buys ores and blocks for emeralds through the normal trading screen; stock refills every morning.
- If the trader or guard dies, exactly one replacement arrives the next morning.

### Equipment
- **Helionite:** a new Overworld ore deep in the deepslate; tools and armour with netherite's stats.
- **Rocket Boots** (leather, iron, diamond, helionite): crouch and jump to fire; each tier jumps higher, stores more energy and absorbs more fall damage. Charge them by using them on a Solar Quarry battery.

### Fixes
- A quarry whose frame legs reached into a chunk that was still loading could freeze the server; leg checks no longer load chunks.
- Tree Farm planting and the shop's site search accept grass, podzol, moss and mud (Minecraft 26.2 moved grass out of the dirt tag).
- Security drones strike from where they hover (they used to stay just out of reach).

### Recipes
- Nothing from the Nether is needed anywhere: quartz, comparators and ender eyes were replaced (amethyst shards, lapis, repeaters, ender pearls). A unit test checks every recipe.

### Interface
- Creative tab grouped by category.
- Sorting Terminal screen fits small windows (240 px tall).

## 2.1.0

### Fixed limitations from 2.0
- **Claims and protection:** every block is checked as the owner before it is broken, online or offline. The quarry uses a Fabric fake player with the owner's profile, so vanilla spawn protection applies and claim mods that use Fabric's `PlayerBlockBreakEvents.BEFORE` can refuse the break. In 2.0 spawn protection only applied while the owner was online.
- **Chunk loading:** new **Chunk Loader Upgrade**. While running and powered, a quarry keeps its 3 × 3 chunks ticking and loads the chunk it is mining, at 8 E/t. Tickets expire on their own when it stops, and loading quarries are restored after a server restart.
- **Remote Terminal reach:** the link now passes through batteries, panels and other machines, not only cables.
- **Ore Prospector:** quarry scans are exact instead of a 1-in-16 estimate, spread over a few ticks so large volumes never stall the server. Unloaded blocks are reported, not guessed.

### Server settings
- New `config/solarquarry.json`: energy cost and solar output multipliers, maximum area tier, chunk loading on/off and upkeep, protection checks, prospector cooldown, terminal list size and machine sound volume. All values are validated and clamped.
- The server sends its settings to every player on join, so GUI numbers and tooltips match the server.
- `/solarquarry config` shows the settings; `/solarquarry reload` (operators) re-reads the file and re-syncs players.
- Lowering the area limit shrinks existing quarry areas to fit.

### Other
- Upgrade summary tooltip shows whether chunk loading is active.
- Recipe count is now 29 (Chunk Loader Upgrade: obsidian, ender pearls, an advanced circuit and an upgrade base). It appears in the recipe book when you pick up an ender pearl, an advanced circuit or an upgrade base.
- `4-client.bat` (start a development client) is now part of the project.

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
