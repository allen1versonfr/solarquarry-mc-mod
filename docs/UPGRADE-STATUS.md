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
| Kelp Farm / Mechanic's house use the same framework | Done (Checkpoints F, G) |
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

## Checkpoint G — the Mechanic, his trades, his guard and his house

| Requirement | Status |
|---|---|
| The trader becomes the Mechanic (name, greeting, starter kit unchanged) | VERIFIED (self-test: phase 4 scenarios still pass) |
| House: trading hall, living room, workshop; a 13 × 11 blueprint built through the shared construction framework | VERIFIED (self-test: every block of the plan in place, bench, beds, door, slab floor, light inside) |
| Site search: flat, open, near spawn; strict search, then a relaxed one, then a fallback | VERIFIED (site found and started in the flat test world); other terrain: by design |
| Built step by step, Mechanic walks to the work holding a wrench, puts it away when done | VERIFIED (self-test) |
| Never replaces a player's block; pauses with the position, resumes after it is cleared | VERIFIED (self-test: OBSTRUCTED, block left in place, house finished afterwards) |
| Construction survives a restart (saved per server) | VERIFIED (self-test: saved site reloaded with the same plan and progress) |
| A finished house is not maintained (cannot be farmed for blocks) | VERIFIED (self-test: a removed anvil stays removed) |
| Supply chest stocked once, sign written | VERIFIED (self-test) |
| Sells every Solar Quarry item except ores, spawn eggs and loot-only items | VERIFIED (self-test: 79 offers, none missing; 867 construction steps) |
| Prices from the recipe (crafting value × 1.5), never below value; expensive items in emerald blocks over both price slots | VERIFIED (self-test). Found in this round: the Weather Control Machine was capped at 64 emerald blocks (576 emeralds, below its value of 719); the second price slot now carries the rest |
| Unlimited stock, fixed prices; buy offers restock every morning | VERIFIED (self-test: 500 purchases, prices unchanged; restock scenario) |
| Guard: dark dyed leather with a gold sentry trim, armour 12, defends the Mechanic, hostile mobs only | VERIFIED (equipment and trim checked); combat: the phase 4 guard scenario |
| Existing worlds: an old shop keeps working; new worlds get the house | By design (`ShopState.house` defaults to the old shop); not separately tested |
| House screenshot | IMPLEMENTED — NOT VERIFIED (client test `18_trader_shop`, `19_trader_trades`) |

## Checkpoint H — Charging Station, powered tools, crouch-to-fly boots

| Requirement | Status |
|---|---|
| Charging Station: 200k E, up to 1,000 E/t, input/output slots, statuses, energy conserved | VERIFIED (self-test: energy into the item = energy out of the station, rate limit, no damage or enchantment changes, save/load, half-charged item keeps its charge, full items move out, no power stops it) |
| Station GUI | IMPLEMENTED — NOT VERIFIED (client test `22_charging_station`) |
| Rocket Boots: hold crouch in the air to fly (server-side, no key binding or packet); energy per tick; faster and longer per tier; fall absorption kept | VERIFIED (self-test: empty pair does nothing, no thrust without crouching, crouching on the ground only sneaks, exact thrust and cost, no free flight when nearly empty, fall damage absorbed and paid, taking them off ends everything, tiers ordered) |
| Ore Prospector on energy (20k E, 400 E per scan) instead of durability | VERIFIED (self-test: refuses when empty, exact cost, cooldown costs nothing) |
| Liquid Prospector | VERIFIED (self-test: 3 water + 1 lava sources and the nearest of each, exact cost) |
| Slime Prospector | VERIFIED (self-test: matches an independent copy of the game's slime-chunk rule over 441 chunks; direction) |
| Scanner Helmet: light rule, 2 E/t, off when empty | VERIFIED (self-test: dark enclosed spot allows spawns, a torch makes it safe; drain) |
| Scanner Helmet HUD | IMPLEMENTED — NOT VERIFIED (client) |
| Batteries charge any powered item | VERIFIED (self-test) |

## Checkpoint I — Liquid Remover pockets

| Requirement | Status |
|---|---|
| Finds the whole connected liquid pocket the drill opens (bounded: 8,192 cells, 1,024 mapped per tick) | VERIFIED (unit test `LiquidPocketTest`, self-test flooded cavern) |
| Drains top layer first, budget scales with Speed upgrades, flowing liquid costs a fifth of a source | VERIFIED (self-test: cavern drained and mined, ground under the water mined too) |
| Inflow back-off (600 ticks) and at most 4 passes | VERIFIED (unit test; self-test pass count) |
| Pocket progress in the quarry screen; saved with the quarry | Save/load VERIFIED; screen IMPLEMENTED — NOT VERIFIED (client) |

## Checkpoint J — Laser Turrets

| Requirement | Status |
|---|---|
| Three tiers (range 12/18/24, damage 4/7/12, 20/14/10 ticks per shot, 150/400/1,000 E per shot) | VERIFIED (unit-level values; self-test with the Basic turret) |
| Hostile mobs only: never villagers, wolves, iron golems, the Mechanic's Guard; creepers only in all-hostiles mode | VERIFIED (self-test) |
| Range and line of sight respected | VERIFIED (self-test: husk at 20 blocks and husk behind a wall untouched). Found in this round: the sight line started inside the turret's own block, so it never fired; it now starts at the edge |
| Every shot paid, nothing else uses energy; no energy → No power; off → no shots; wrecked structure turret never fires | VERIFIED (self-test: 750 E for 5 shots) |
| Settings, energy and shot count survive save/load | VERIFIED (self-test) |
| Beam rendering, GUI | IMPLEMENTED — NOT VERIFIED (client test `23_laser_turret`) |

## Checkpoint K — equipment and industrial structures

| Requirement | Status |
|---|---|
| Portable Power Cell: charges powered gear in the inventory, never other cells, energy conserved | VERIFIED (self-test) |
| Industrial Repair Kit: repairs the most worn Solar Quarry item by 300, used up, vanilla tools untouched | VERIFIED (self-test) |
| Emergency Energy Injector: +20k E into the emptiest powered item, used up (loot only) | VERIFIED (self-test) |
| Mining Helmet: Haste below y=50 only | VERIFIED (self-test) |
| Night-Vision Goggles: Night Vision only while charged, 3 E/t | VERIFIED (self-test) |
| Advanced Powered Pickaxe: area mode toggle, 3 × 3 facing the player, 100 E per extra block, never bedrock or harder blocks, normal pickaxe without energy | VERIFIED (self-test) |
| Industrial Power Hammer, Reinforced Mining Pickaxe, Advanced Mining Scanner | Registered with recipes (recipe self-test); scanner use: IMPLEMENTED — NOT VERIFIED |
| Prototype Battery (1M E, loot only) | Registered, in loot tables (loot self-test) |
| Industrial Shield | NOT DONE: a working shield needs a custom shield renderer and model on the client; left out rather than shipped broken |
| Security Drone Controller | NOT DONE: security drones are part of the quarry (Security Upgrade, paid by the quarry); a free-roaming controller would need a separate drone owner and power source |
| Ten structures (Mining Facility, Power Station, Research Laboratory, Automated Factory, Tree Farm, Supply Bunker, Substation, Prototype Facility, Train Depot, Solar Array), code-driven pieces, deterministic per origin | VERIFIED (self-test: all ten built chunk by chunk like world generation, 658–4,010 blocks each) |
| Registered from the data pack (structure, structure set, biome tag for each) | VERIFIED (self-test: 10/10) |
| Themed loot: 11 loot tables load; every container filled and rolled exactly once; no unknown items | VERIFIED (self-test: 36 containers incl. the freight-car minecart) |
| Guarded structures have Laser Turrets, wrecked or with finite energy | VERIFIED (self-test: Mining Facility, Power Station, Research Laboratory, Prototype Facility) |
| Generates through the game's own structure code | VERIFIED (self-test: the Substation finds a site and is placed with its chest) |
| Spacing, rarity and look in a real world | IMPLEMENTED — NOT VERIFIED (needs exploring a world on the PC) |

## Weather Control Machine

| Requirement | Status |
|---|---|
| Clear / rain / thunderstorm through the server's own weather; startup sequence before the sky changes | VERIFIED (self-test: real thunderstorm in the server weather data after the sequence) |
| One-time change, then shutdown; busy machine refuses a second request at no cost | VERIFIED (self-test) |
| Without enough energy nothing starts and nothing is taken | VERIFIED (self-test) |
| Clearing costs ×1.5 against rain, ×2 against a thunderstorm | VERIFIED (self-test). Found in this round: the check read the sky's fade-in level, which lags seconds behind; it now reads the server's weather state |
| Dimensions without weather are refused | VERIFIED (self-test: the End). Found in this round: the End has sky light in 26.2, so the old sky-light test let it through; it now uses the game's own `canHaveWeather()` |
| Sustained weather (5–40 min) with upkeep every tick; survives save/load | VERIFIED (self-test: exact upkeep over 40 ticks; copy holds stage, operation, remaining time, energy) |
| Out of energy: controlled shutdown with the reason | VERIFIED (self-test: POWER_LOST) |
| Time preset jumps the world clock; total game time untouched; exact cost | VERIFIED (self-test) |
| Gradual time change, step by step, paid per step | VERIFIED (self-test). Found in this round: the last step landed on the target and the clock's own tick moved it one past, so the machine chased it forever; it now finishes on the step that lands |
| Energy scale: 5M E buffer, 8,192 E/t input; start costs 200k–2M E, upkeep 200–2,000 E/t | Scaled to the mod's energy (the plan's numbers assumed another mod's energy unit); see README |
| Recipe needs Helionite, Elite panel and battery, Advanced Circuit, Super Smelter, Machine Frame | VERIFIED (unit test `ProgressionTest`, now also covering the Elite Laser Turret and this machine) |
| 16 original sounds (synthesised, generator script in `docs/weather-sounds-gen.py`), subtitles | Registered (the server starts with all 16 sound events); how they sound and the loops on the client: NOT VERIFIED |
| Animated renderer (rings, core, beam), GUI with presets and manual time | IMPLEMENTED — NOT VERIFIED (client test `24_weather_machine_gui`, `25_weather_machine_charging`, `26_weather_machine_storm`) |
| Multiplayer | Not tested by agreement |

## Checkpoint L — release audit (4.0.0-beta.3)

| Item | Status |
|---|---|
| Full dedicated-server self-test in the cloud workspace | VERIFIED: 474/474 checks (363 from beta.2 plus 111 new for G–K and the Weather Control Machine) |
| Unit tests | 71/71 |
| No Nether materials in any recipe; Helionite in every tier 4 recipe | VERIFIED (unit tests) |
| Every item in the creative tab exactly once, has a recipe (except the two loot-only items), model, texture and name | VERIFIED (self-test data checks) |
| Packaged jar `release/solarquarry-4.0.0-beta.3.jar` | VERIFIED: loaded from a mods folder, 474/474 self-test checks pass from the jar (the test copy only had `"java": ">=21"` for the workspace; the release jar keeps `>=25`). Classes are compiled for Java 21, which Java 25 runs; the Gradle build on the PC replaces this with a Java 25 jar |
| Gradle build on the PC (`2-build.bat`) | NOT RUN — needs the PC |
| Client game test with screenshots (`5-clienttest.bat`), now also covering the Charging Station, Laser Turret and Weather Control Machine | NOT RUN — needs the PC |
| Run alongside the Fabulously Optimized modpack | NOT RUN — needs the PC |
| Multiplayer | Not tested by agreement (solo play) |

**What `!run.bat` on the PC should do next:** build with Gradle (Java 25), run `3-selftest.bat` to confirm the cloud
result on the real toolchain, run `5-clienttest.bat` and look at screenshots 18–26, then load the jar in the
Fabulously Optimized instance on a copy of a world.
