# Major upgrade (4.0) — feature tracker

Spec: the "Solar Quarry master development plan — major upgrade" (Checkpoints A–L plus the Weather Control Machine).
Statuses: NOT STARTED · IN PROGRESS · IMPLEMENTED — NOT VERIFIED · VERIFIED · BLOCKED.

**VERIFIED** means an automated check exercised it: the dedicated-server self-test (`3-selftest.bat`, in-world
scenarios) or a unit test. Compiling is not verification. Rendering, GUI layout and sounds are only verified by the
client game test (`5-clienttest.bat`, screenshots), which needs a real client.

**How this round was tested.** Ali was away from his PC, so builds and server tests ran in a cloud workspace using the
exact jars from his PC's Gradle cache (Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2). That workspace
only has Java 21, so its copies of the jars were adapted to Java 21 for testing (class-file version, two small API
shims); the mod source is unchanged and still targets Java 25. Every checkpoint below lists what passed there. The
Gradle build, the client game test (screenshots) and the modpack compatibility run still need `!run.bat` on the PC.

## Checkpoint A — audit (2026-10-15)
See [UPGRADE-AUDIT.md](UPGRADE-AUDIT.md). Baseline 3.0.0: 302/302 self-test checks, also reproduced in the cloud workspace.

## Checkpoint B — shared construction framework, Quarry, Tree Farm (4.0.0-beta.1)

| Requirement | Status |
|---|---|
| Framework: states IDLE / VALIDATING / BUILDING / PAUSED / COMPLETED / ERROR, resolved blueprints, bounded work per tick | VERIFIED (self-test) |
| Validation: air, grass, flowers, snow and other wild plants are cleared; anything else stops construction and is never overwritten | VERIFIED (self-test: cobblestone in the mast's path is reported with its position and left in place) |
| Pauses for missing power, obstructions and unloaded chunks; never loads chunks | VERIFIED (power, obstruction); unloaded chunks: by design (position check), not separately tested |
| Progress and plan persist across save/load; resuming never places a block twice | VERIFIED (self-test: copy of the block entity, `placedCount == total`) |
| Repairs: a completed structure checks one block per second and rebuilds missing ones | IMPLEMENTED — NOT VERIFIED |
| Machine-built blocks (Industrial Frame / Industrial Glass) have no item and drop nothing; removed with their machine | VERIFIED (self-test) |
| Quarry: frame raised in paid steps before mining (posts rise, rails extend, beams span); only finished parts are solid | VERIFIED (self-test: status, partial collision, no mining while building, exact step count) |
| Quarry: waits for power while building; resumes when powered | VERIFIED (self-test) |
| Quarry: frames of quarries from 3.x are kept (no rebuild after updating) | VERIFIED (self-test: data without the construction section) |
| Quarry: frame construction animation and GUI progress line | IMPLEMENTED — NOT VERIFIED (client test adds screenshot `00_quarry_frame_construction`) |
| Tree Farm "does nothing" — root cause | Fixed: it only planted where the spot was pure air with air above it, so grass, flowers or snow on a spot made it unusable and the farm showed Idle with no reason; it also only looked one block up or down. Now it clears wild plants, searches 2 up / 3 down for ground, and reports **No ground**, **Construction blocked** (with coordinates) and **Building** |
| Tree Farm: builds a mast and four corner posts first, then plants, grows and fells on grassy ground powered through a cable | VERIFIED (self-test: 11 frame blocks, plants through grass and flowers, fells trees, power through a cable) |
| Tree Farm: construction progress / blocked position in the GUI | IMPLEMENTED — NOT VERIFIED (client test screenshot `19_tree_farm_construction`) |
| Kelp Farm / Mechanic's house use the same framework | NOT STARTED (Checkpoints F, G) |
| Multiplayer | Not tested by agreement (solo play); all construction runs on the server |

Results: 326/326 self-test checks (302 existing + 24 new), 62/62 unit tests, packaged jar loads from a mods folder and passes the 4.0 scenarios.

## Checkpoint C — creative tab and progression

| Requirement | Status |
|---|---|
| Solar Quarry creative tab, every item exactly once, grouped by category, quarry icon | VERIFIED (self-test, unchanged since 3.0) |
| Progression tiers documented ([PROGRESSION.md](PROGRESSION.md)) | Done |
| Helionite has a purpose beyond its own gear: Elite Solar Panel and Elite Battery need it | VERIFIED (unit test `ProgressionTest`) |
| No Nether materials in any recipe | VERIFIED (unit test) |

## Checkpoint D — Filtered Splitter

| Requirement | Status |
|---|---|
| New block; Filter Pipe kept and unchanged | VERIFIED (self-test: both) |
| Takes items from pipes/hoppers/machines on its input side; outputs never accept items | VERIFIED (self-test) |
| Selected items (item templates or tags) out of the green side, everything else out of the orange side | VERIFIED (self-test: 20 iron + 10 logs vs 30 cobblestone) |
| Whitelist / blacklist, exact-components option | VERIFIED (self-test: blacklist swaps sides); components: same rule as the Filter Pipe |
| Input / matching / other faces configurable and never overlapping; block shows the two outputs (green/orange plates) | VERIFIED (unit test `SplitterConfigTest`, self-test block state) |
| Full output or nothing attached: items wait in the 9-slot buffer, nothing deleted; status reports it; resumes afterwards | VERIFIED (self-test) |
| Settings persist; wrench pickup keeps settings | VERIFIED (save/load); wrench: by design (Configured flag), not separately tested |
| Bounded work (16 items per output every 4 ticks), so loops cannot cause unbounded processing | By design |
| GUI: 18 filter slots, 2 tag boxes, mode, sides, top-down diagram, search panel, clear/reset, status line, tooltips | IMPLEMENTED — NOT VERIFIED (needs the client test) |

## Later checkpoints
E Storage Terminal · F Kelp Farm · G Mechanic, trades, guard, house ·
H Charging Station, powered tools, crouch-to-fly boots · I Liquid Remover pockets · J Laser Turrets · K structures and loot ·
Weather Control Machine · L release audit — all NOT STARTED.
