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

## Checkpoint E — Storage Terminal (4.0.0-beta.2)

The Sorting Terminal is now the **Storage Terminal** (same block and id, so existing worlds keep it). Right-click opens
the storage view; **Rules** switches to the sorting rules screen and **Storage** switches back.

| Requirement | Status |
|---|---|
| Unified access: every chest, barrel, shulker box and crate on the pipe network as one list with totals | VERIFIED (self-test: chest + barrel + crate + double chest, 60 cobblestone across two) |
| Search by name, largest amounts first, slot usage line | Search: IMPLEMENTED — NOT VERIFIED (client); ordering and totals VERIFIED |
| Withdraw (stack to cursor, half, shift to inventory) and deposit (cursor, shift-click, Store button) | VERIFIED (self-test drives the server side with a fake player) |
| Double chests counted once and in full | VERIFIED (self-test) |
| Hands-free sorting: deposits follow the rules (listed → its chest, rest → fallback, priorities), same as pipe routing | VERIFIED (self-test); pipe routing unchanged (3.0 self-tests still pass) |
| Unassigned items without a fallback are refused with a message, never lost | VERIFIED (self-test) |
| Duplication safety: amounts decided on the server, one transaction per action | VERIFIED (self-test: withdraw exactly what existed; unknown item request changes nothing) |
| Disconnected/unloaded containers skipped; bounded network (1024 pipes, 128 inventories) | By design |
| Open one specific chest's own screen remotely | Not done: vanilla chest screens close when the player is out of reach; the combined list covers the use |
| No terminal per chest needed; one terminal serves the whole network | Yes |

## Tree Farm — second root cause (found in this round)

The 3.x farm fed **all** its bone meal to the first sapling in its list. In 4.0.0-beta.1 the corner posts stood
diagonally next to the four corner planting spots, inside the space an oak needs, so the first sapling could never
grow and every piece of bone meal went to it: the farm looked busy (Growing) and produced nothing. Fixed:

| Change | Status |
|---|---|
| Corner posts one block outside the planting area | VERIFIED (self-test: 11–16 trees felled per run, was 0–1) |
| Bone meal goes round-robin over all saplings | VERIFIED (same runs) |
| A sapling that takes 8 successful bone meals without growing is skipped; if all are stuck the farm says **No room to grow** | IMPLEMENTED — NOT VERIFIED (status path; the growth path is verified) |
| The drone clears up to 8 leaves per operation (each still paid), so a big oak no longer takes minutes | VERIFIED (self-test) |

## Checkpoint F — Kelp Farm

| Requirement | Status |
|---|---|
| Self-building through the shared framework: glass basin (6 high around a 5 × 5 water area), frame corners, frame floor only where the ground is not solid | VERIFIED (self-test: 144 wall blocks, COMPLETED) |
| Never overwrites player blocks; reports the blocked position | VERIFIED (self-test: cobblestone in the wall stays, status Construction blocked) |
| Fills the basin with water from the bottom up after the walls stand; nothing leaks | VERIFIED (self-test: 150 water cells, 0 outside) |
| Plants kelp from its kelp slots in every column | VERIFIED (self-test: 25 columns) |
| Real growth only: vanilla random ticks, bone meal (1 block each), Growth Upgrade = extra random ticks (one per level every 3 s, paid) | VERIFIED (harvest totals come only from grown blocks) |
| Harvests a column when it reaches the top layer or stops growing; the bottom block stays and regrows; real drops into the output | VERIFIED (self-test: 109 kelp in 70 s with 64 bone meal and two Growth upgrades, output = harvested, nothing dropped in the world) |
| Energy for building, water, planting, harvesting, bone meal and growth | VERIFIED (energy consumed) |
| Full output: harvesting stops, nothing lost; resumes and pushes into an adjacent chest or pipe | VERIFIED (self-test) |
| Save/load (construction, totals, upgrades) | VERIFIED (self-test) |
| Breaking the farm removes basin, water and kelp (no flooding) | VERIFIED (self-test) |
| GUI: status LCD, construction progress / blocked position, kelp count, harvest total, growth gauge, energy | IMPLEMENTED — NOT VERIFIED (client test) |
| Multiplayer | Not tested by agreement |

## Later checkpoints
G Mechanic, trades, guard, house ·
H Charging Station, powered tools, crouch-to-fly boots · I Liquid Remover pockets · J Laser Turrets · K structures and loot ·
Weather Control Machine · L release audit — all NOT STARTED.
