# Testing, feature checklist and known limitations (2.1.0)

## How it was tested

All results below come from the final source, built and run on the development PC (Windows, Temurin JDK 25.0.4, Gradle 9.7.1, Loom 1.18.4, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2).

| Check | Command | Result |
|---|---|---|
| Clean build | `gradlew clean build --warning-mode all` | Passed. No Gradle deprecation warnings, no Java deprecation warnings. |
| Unit tests (JUnit 5) | part of `build` | 31 / 31 passed |
| Dedicated-server self-test | `3-selftest.bat` (`runSelftest`) | @@SELFTEST@@ |
| Client game test | `5-clienttest.bat` (`runClientGameTest`) | @@CLIENT@@ |
| Compatibility run | self-test and client test with the mods from your Fabulously Optimized instance | @@COMPAT@@ |

### Unit tests
- **MiningPatternTest**: every mode visits each cell exactly once and stays inside its volume; the strip order descends layer by layer; the serpentine head moves one block per step; build-limit clamping; degenerate input; progress is monotonic and bounded.
- **QuarryUpgradesTest**: range sizes 16/32/64 with clamping; more speed means more blocks, more energy per block and more power draw; efficiency and capacity effects; costs are bounded with no overflow.
- **SolarTierTest**: no sky means no power; noon output per tier; only Elite produces at night; weather hurts lower tiers more; output rises with the sun.
- **EnergyDistributionTest**: energy is conserved; never more than the source holds; full and rate-limited receivers are respected; remainders rotate fairly; empty cases are safe.
- **RoutingRulesTest**: an allow-list match beats priority; higher priority wins; ties keep the nearer destination.
- **SolarQuarryConfigTest** (new): missing entries use defaults; out-of-range and wrong-type values are clamped or ignored; NaN falls back; JSON round trip; the file is created and missing keys are written back; a broken file is never overwritten; the multipliers and the area cap take effect.

### Dedicated-server self-test (real world, real blocks)
- **Data:** 29 recipes load; every item and block has a crafting recipe; every recipe has a recipe-book unlock; loot tables and pickaxe tags exist for every block.
- **Strip mining:** 511/511 blocks mined; bedrock skipped; nothing below the target depth; filter routing; 510 cobblestone delivered with nothing lost; battery power through cables; mined counter.
- **Persistence:** energy, cursor, mode/size, mined count, run state, upgrades, lists. Version 1.0 save data migrates. Invalid saved values are clamped.
- **Wrench:** removes the machine, returns exactly one item, nothing spills, and energy, progress, run state, buffer, upgrades and void list are restored on placement.
- **Upgrades:** void deletes only listed items; silk touch; fortune/silk conflict and duplicate types rejected; range I/II resize the area.
- **Modes:** a 5×3 rectangle (exact bounds, 27 blocks); containers, spawners and skip-list blocks left alone; tunnel 3×2×5 with roof, walls and end intact; area clear 3×3×3 with blocks above kept; ore priority (ores only) mines 4 ores and leaves 60 stone; the ore list narrows targets.
- **States:** waits at unloaded chunks (no upgrade); Output Full mines nothing; No Power mines nothing.
- **Energy:** two consumers share one battery (12,800 E each); the battery's 256 E/t limit holds; total energy is conserved exactly; a full battery stays at capacity; a disconnected cable stops transfer; solar output at noon, night and with no sky, per tier.
- **Logistics:** tag filter (`c:raw_materials`) routing; invalid tag rejected; priority +3 fills first and overflows to the other chest (64/64); a destination removed mid-transit leads to all 256 items rerouting with none dropped; pipe contents and filter settings survive save/load.
- **Presets:** quarry settings applied without touching items, upgrades or energy; newer-format cards refused; invalid values fall back to defaults; filter presets round-trip.
- **Terminal:** finds both quarries on its network; can't address machines outside it; a disconnected quarry drops off the list; **the link passes through a battery** in the middle of the run, and removing that battery cuts it.
- **Storage scan:** exact item and slot totals for chests; free-space percentage.
- **Protection (new):** a stand-in claim mod refuses one block through `PlayerBlockBreakEvents.BEFORE`; the quarry leaves that block, mines the rest, and the check sees a fake player carrying the offline owner's id.
- **Prospector (new):** exact count of the 5 ores in a quarry volume (an ore just outside is not counted), nothing reported as unloaded.
- **Config (new):** `/solarquarry config` and `reload` are registered; settings survive the network codec; `energyCostMultiplier` 1.5 turns 83 E into 124 E per stone block; `solarOutputMultiplier` 0.5 halves an Advanced panel from 40 to 20 E/t; settings are restored afterwards.
- **Chunk Loader (new), with no player online:** the quarry keeps mining after its forced chunk is released; the dimension remembers it; it finishes a 40-block tunnel whose last chunk is 3 chunks away (outside its own area) and mines exactly the 21 stone blocks; it switches itself off when done and is forgotten; its chunks and the mined chunks unload on their own; a remembered quarry gets its chunk back through the server-start restore; an unpowered quarry keeps its own chunks (waiting for power) but does not load the chunks it would mine.

## Feature checklist

| Requirement | Status | Notes |
|---|---|---|
| Configurable rectangular area, 16/32/64 via Range upgrades | COMPLETE | Server can cap it (`maxRangeTier`) |
| Layer-by-layer, predictable order | COMPLETE | Serpentine, unit tested |
| Respect bedrock, unbreakable, protected and invalid blocks | COMPLETE | Spawn protection and Fabric-event claim checks, owner online or offline |
| Save/reload of all state | COMPLETE | Includes ore queue and lists |
| Pause/resume without losing progress | COMPLETE | |
| Stop safely: no power, output full, unloaded, complete | COMPLETE | |
| Bounded per-tick work | COMPLETE | 1,024 skips / 2,048 ore scans per tick; prospector 65,536 blocks per tick |
| Explicit state machine | COMPLETE | |
| Strip Mine / Tunnel Bore / Ore Priority / Area Clear | COMPLETE | |
| Silk Touch collection | COMPLETE | As an upgrade |
| Upgrades (speed, range, capacity, efficiency, fortune, silk, selective void, chunk loader) | COMPLETE | Effects shown in GUI and tooltips |
| Standalone energy: panels, batteries, cables | COMPLETE | |
| Distinct panel tiers; more speed draws more power | COMPLETE | |
| Energy feedback (stored, generation, use, limits, failures) | COMPLETE | GUI, right-click readouts, terminal |
| Real item transport with visible items | COMPLETE | |
| Filters by item and by tag; allow/block | COMPLETE | |
| Destination priorities | COMPLETE | Via filter pipes |
| No duplication or deletion; recovery from removed destinations | COMPLETE | Self-tested |
| Deterministic routing, loop-free, bounded | COMPLETE | |
| Junction blocks | PARTIAL | Any pipe branches; there is no separate junction block |
| Ore prospecting | COMPLETE | Exact for chunks and quarry volumes |
| Storage totals, low-space and blocked-output warnings | COMPLETE | In the remote terminal and quarry GUI |
| Configuration presets with validation | COMPLETE | Configuration Card |
| Remote control terminal with permissions and server validation | COMPLETE | Link passes through machines |
| Chunk loading | COMPLETE | Chunk Loader Upgrade, timed tickets, restored at server start |
| Server config, synced to clients, reload command | COMPLETE | `config/solarquarry.json` |
| Industrial art for every new block and item | COMPLETE | |
| Animation follows the actual block being mined; stops when idle | COMPLETE | |
| GUI: tooltips, keyboard focus, resizing | COMPLETE | Vanilla buttons and screens |
| Recipes for everything, shown in the recipe book | COMPLETE | Self-tested (server); the client test checks unlock on pickup |
| Dedicated server | COMPLETE | Self-test runs on a dedicated server |
| Unit tests for pure logic | COMPLETE | 31 tests |

## Known limitations and remaining risks

- **Protection mods that don't use Fabric's break event.** Claim mods that only hook player packets or their own mixins cannot be seen by any machine. Spawn protection and mods using `PlayerBlockBreakEvents.BEFORE` are covered. Use the Skip list for anything else.
- **Server restart restore** was tested by calling the restore step directly in a running server (the self-test cannot restart itself). The data it reads is a standard Fabric world attachment saved with the world.
- **Unreadable inventories:** the storage view shows "connected (unreadable)" for inventories that expose no slots through Fabric's transfer API. This is a limit of those inventories, not something the mod can read around.
- **Recipe viewers** (JEI/REI/EMI) were not installed during testing. All recipes are standard crafting recipes, which those viewers read automatically.
@@COMPATLIMIT@@

## Manual checks still worth doing in your own world

1. Ore Prospector in the air and on a quarry: chat output, cooldown, durability.
2. Configuration Card copy (sneak-use), apply (use) and clear (sneak-use in the air) on a quarry and a filter.
3. With two players on a server: a non-owner sees "Read only" in the terminal and can't apply a card to someone else's quarry; an operator can.
4. If you use a claim mod on a server: claim a block inside a quarry's area as another player and check the quarry skips it.
5. Visual check with your shader pack.
