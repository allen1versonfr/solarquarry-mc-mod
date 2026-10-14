# Testing, feature checklist and known limitations (2.0.0)

## How it was tested

All results below come from the final source, built and run on the development PC (Windows, Temurin JDK 25.0.4, Gradle 9.7.1, Loom 1.18.4, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2).

| Check | Command | Result |
|---|---|---|
| Clean build | `gradlew clean build --warning-mode all` | Passed. No Gradle deprecation warnings, no Java deprecation warnings. |
| Unit tests (JUnit 5) | part of `build` | 24 / 24 passed |
| Dedicated-server self-test | `3-selftest.bat` (`runSelftest`) | 123 / 123 checks passed |
| Client game test | `5-clienttest.bat` (`runClientGameTest`) | Passed: drives every GUI, checks tooltips and recipe-book unlocks, and saves 9 screenshots |
| Client log scan | client test log | No missing models, textures, resources or exceptions |

### Unit tests
- **MiningPatternTest**: every mode visits each cell exactly once and stays inside its volume; the strip order descends layer by layer; the serpentine head moves one block per step; build-limit clamping; degenerate input; progress is monotonic and bounded.
- **QuarryUpgradesTest**: range sizes 16/32/64 with clamping; more speed means more blocks, more energy per block and more power draw; efficiency and capacity effects; costs are bounded with no overflow.
- **SolarTierTest**: no sky means no power; noon output per tier; only Elite produces at night; weather hurts lower tiers more; output rises with the sun.
- **EnergyDistributionTest**: energy is conserved; never more than the source holds; full and rate-limited receivers are respected; remainders rotate fairly; empty cases are safe.
- **RoutingRulesTest**: an allow-list match beats priority; higher priority wins; ties keep the nearer destination.

### Dedicated-server self-test (real world, real blocks)
- **Data:** 28 recipes load; every item and block has a crafting recipe; every recipe has a recipe-book unlock; loot tables and pickaxe tags exist for every block.
- **Strip mining:** 511/511 blocks mined; bedrock skipped; nothing below the target depth; filter routing; 510 cobblestone delivered with nothing lost; battery power through cables; mined counter.
- **Persistence:** energy, cursor, mode/size, mined count, run state, upgrades, lists. Version 1.0 save data migrates. Invalid saved values are clamped.
- **Wrench:** removes the machine, returns exactly one item, nothing spills, and energy, progress, run state, buffer, upgrades and void list are restored on placement.
- **Upgrades:** void deletes only listed items; silk touch; fortune/silk conflict and duplicate types rejected; range I/II resize the area.
- **Modes:** a 5×3 rectangle (exact bounds, 27 blocks); containers, spawners and skip-list blocks left alone; tunnel 3×2×5 with roof, walls and end intact; area clear 3×3×3 with blocks above kept; ore priority (ores only) mines 4 ores and leaves 60 stone; the ore list narrows targets.
- **States:** waits at unloaded chunks; Output Full mines nothing; No Power mines nothing.
- **Energy:** two consumers share one battery (12,800 E each); the battery's 256 E/t limit holds; total energy is conserved exactly; a full battery stays at capacity; a disconnected cable stops transfer; solar output at noon, night and with no sky, per tier.
- **Logistics:** tag filter (`c:raw_materials`) routing; invalid tag rejected; priority +3 fills first and overflows to the other chest (64/64); a destination removed mid-transit leads to all 256 items rerouting with none dropped; pipe contents and filter settings survive save/load.
- **Presets:** quarry settings applied without touching items, upgrades or energy; newer-format cards refused; invalid values fall back to defaults; filter presets round-trip.
- **Terminal:** finds both quarries on its cable run; can't address machines outside it; a disconnected quarry drops off the list.
- **Storage scan:** exact item and slot totals for chests; free-space percentage.

## Feature checklist

| Requirement | Status | Notes |
|---|---|---|
| Configurable rectangular area, 16/32/64 via Range upgrades | COMPLETE | |
| Layer-by-layer, predictable order | COMPLETE | Serpentine, unit tested |
| Respect bedrock, unbreakable, protected and invalid blocks | COMPLETE | Spawn protection is checked only while the owner is online (see limitations) |
| Save/reload of all state | COMPLETE | Includes ore queue and lists |
| Pause/resume without losing progress | COMPLETE | |
| Stop safely: no power, output full, unloaded, complete | COMPLETE | |
| Bounded per-tick work | COMPLETE | 1,024 skips / 2,048 ore scans per tick at most |
| Explicit state machine | COMPLETE | |
| Strip Mine / Tunnel Bore / Ore Priority / Area Clear | COMPLETE | |
| Silk Touch collection | COMPLETE | As an upgrade |
| Upgrades (speed, range, capacity, efficiency, fortune, silk, selective void) | COMPLETE | Effects shown in GUI and tooltips |
| Standalone energy: panels, batteries, cables | COMPLETE | |
| Distinct panel tiers; more speed draws more power | COMPLETE | |
| Energy feedback (stored, generation, use, limits, failures) | COMPLETE | GUI, right-click readouts, terminal |
| Real item transport with visible items | COMPLETE | |
| Filters by item and by tag; allow/block | COMPLETE | |
| Destination priorities | COMPLETE | Via filter pipes |
| No duplication or deletion; recovery from removed destinations | COMPLETE | Self-tested |
| Deterministic routing, loop-free, bounded | COMPLETE | |
| Junction blocks | PARTIAL | Any pipe branches; there is no separate junction block |
| Ore prospecting (exact vs. estimated, limited) | COMPLETE | |
| Storage totals, low-space and blocked-output warnings | COMPLETE | In the remote terminal and quarry GUI |
| Configuration presets with validation | COMPLETE | Configuration Card |
| Remote control terminal with permissions and server validation | COMPLETE | |
| Industrial art for every new block and item | COMPLETE | |
| Animation follows the actual block being mined; stops when idle | COMPLETE | |
| GUI: tooltips, keyboard focus, resizing | COMPLETE | Vanilla buttons and screens |
| Recipes for everything, shown in the recipe book | COMPLETE | Self-tested (server); the client test checks unlock on pickup |
| Dedicated server | COMPLETE | Self-test runs on a dedicated server |
| Unit tests for pure logic | COMPLETE | 24 tests |

## Known limitations and remaining risks

- **Claims and protection mods** (FTB Chunks, GriefPrevention and similar) are not detected. The quarry respects vanilla spawn protection only while its owner is online. Use the Skip list or place quarries where mining is allowed.
- **Chunk loading:** quarries never force-load chunks. A large area or long tunnel near the edge of your view distance waits with "Area Unloaded".
- **Remote terminal reach:** it sees quarries on the same cable run. Batteries and panels in the middle of a run end the terminal's link (they still pass power normally).
- **Unreadable inventories:** the storage view shows "connected (unreadable)" for inventories that expose no slots through Fabric's transfer API.
- **Prospector estimates** sample 1 in 16 columns and can differ from the real count, especially for rare ores.
- **Compatibility with your mod list** (C2ME, FerriteCore, Continuity, Entity Culling, Dynamic FPS, Complementary shaders) was not tested together. The mod adds no mixins and changes no vanilla behaviour.

## Manual checks still worth doing in your own world

1. Ore Prospector chat output in the air and on a quarry, plus the cooldown and durability.
2. Configuration Card copy (sneak-use), apply (use) and clear (sneak-use in the air) on a quarry and a filter.
3. With two players on a server: a non-owner sees "Read only" in the terminal and can't apply a card to someone else's quarry; an operator can.
4. The recipe book showing Solar Quarry recipes after you pick up iron, copper or redstone.
5. Visual check with Complementary Reimagined shaders: the gantry and pipe items are drawn with standard entity-cutout render types, which shader packs normally handle.
