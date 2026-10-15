# Solar Quarry 3.0

A standalone Fabric mod for Minecraft 26.2: a solar-powered quarry with four mining modes and a 3D-printer style gantry, an energy network (solar panels, batteries, cables), item logistics (pipe tiers, filter pipes, a Sorting Terminal, storage crates), liquid handling, smelting machines, a Tree Farm, security and miner drones, a trader with a shop and starter kit, Helionite gear and Rocket Boots. No other tech mod is needed, and no recipe needs anything from the Nether.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version and [docs/PLAN-STATUS.md](docs/PLAN-STATUS.md) for how each feature was verified. Back up your world before updating from 2.x.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.159.0+26.2 (or newer for 26.2) |
| Java | 25 |

Nothing else is required. Install the mod on the server **and** on every client.

## Download

The mod is [`release/solarquarry-3.0.0.jar`](release/solarquarry-3.0.0.jar), also attached to the [3.0.0 release](https://github.com/allen1versonfr/solarquarry-mc-mod/releases/tag/v3.0.0). Put it in your `mods` folder; that's all you need to play. The rest of this repository is the Gradle project (source, assets, tests, build scripts), only needed to build or change the mod.

## Building from source

```
gradlew.bat build        (Windows)
./gradlew build          (Linux / macOS)
```

The jar is written to `build/libs/solarquarry-3.0.0.jar`. `build` also runs the unit tests.

Helper scripts (Windows): `0-get-jdk.bat` downloads a portable JDK 25 into `.jdk/` if you don't have Java 25. `2-build.bat` builds and logs to `logs/build.log`. `3-selftest.bat` runs the dedicated-server self-test (about 300 automated in-world checks; results in `logs/selftest.log`). `5-clienttest.bat` runs the client game test, which saves screenshots to `run-clienttest/screenshots`. `4-client.bat` starts a development client with the mod loaded.

## Getting started

1. On your first visit the Solar Quarry trader gives you a starter kit (parts for a first quarry, solar panels, cables, pipes, a chest, torches, a Wrench and a **Fabrication Bench**) and tells you where his shop is. Use the Fabrication Bench to look up and craft everything else: a **Quarry**, **Basic Solar Panels**, a **Basic Battery**, **Energy Cables** and **Item Pipes**. Every recipe also works in a normal crafting table and appears in the vanilla recipe book.
2. Place the quarry facing you. Its work area starts one block **behind** it (on the side away from you) and is centred on the machine.
3. Run energy cables from the panels and battery to the quarry. Run item pipes from the quarry to a chest (or put the chest directly against the quarry).
4. Right-click the quarry, open **Config** to choose a mode and size, then press **Start**.

## The quarry

**Modes** (Config → Mode button; Shift-click to go back):

| Mode | What it does |
|---|---|
| Strip Mine | Mines the whole area layer by layer, from the quarry's level down to the target depth. |
| Tunnel Bore | Bores a straight tunnel (width × height) back from the quarry, starting at its level. The length can reach 4× the area limit. |
| Area Clear | Clears the area from the quarry's level up to the set height, top layer first. Good for flattening terrain. |
| Ore Priority | Scans the strip-mine volume, mines ores first, then everything else. Turn on **Ores Only** to leave everything except ores. |

**Area:** width and length are set separately (−/+, Shift for bigger steps), up to 16, or 32/64 with Range upgrades. Changing the mode or size restarts the scan; blocks already mined are air and are skipped quickly.

**Lists** (Config page, top-right button cycles through them). Click a slot with an item to set it, or with an empty hand to clear it. These are templates; your items aren't used up.
- **Void**: drops on this list are deleted, but only while a Void Upgrade is installed. With an empty list, nothing is deleted.
- **Skip**: blocks on this list are never mined, in any mode.
- **Ores**: if you list anything here, Ore Priority targets only those blocks. When it's empty, every block tagged `c:ores` counts.

**Always skipped:** air, unbreakable blocks, anything outside the world border, Solar Quarry's own blocks, containers (so chests are never broken open), and the data-pack tag `solarquarry:quarry_ignored` (spawners, portals and similar).

**Claims and protection:** before breaking each block, the quarry asks the world whether its owner may break it, whether or not the owner is online. It acts as a Fabric fake player carrying the owner's profile, so vanilla spawn protection applies, and claim mods that use Fabric's standard block-break event (`PlayerBlockBreakEvents.BEFORE`) can refuse the break. Refused blocks are skipped and left alone. A server can turn this off with `respectProtection` (see Server settings).

**Frame and gantry:** while running, the quarry builds a steel frame around the work area. The posts and rails are solid (you and mobs bump into them), but no blocks are placed in the world. A bridge rides both side rails and the drill carriage runs along the bridge, like a 3D printer. A quarry placed in the air grows its legs down to the first solid block (up to 96 blocks).

**Torches:** put torches in the torch slot and the quarry places them on the pit walls every 4 layers (every 4 slices in Tunnel Bore), so the pit stays lit.

**Experience:** XP from mining (and from the Auto-Smelter) is stored in the quarry. Press the **XP** button in its screen to collect it.

**Drone slot:** holds a **Miner Drone** (see Drones).

**Status** is shown in the GUI, on the front lamp and on the Remote Terminal. The bottom-left line of the GUI tells you what to do:

| Status | Meaning |
|---|---|
| Running / Scanning (green) | Working. Scanning means Ore Priority is looking for ores. |
| Paused (yellow) | You paused it. Resume continues from the same block. |
| No Power (red) | Not enough stored energy for the next block. |
| Output Full (red) | The 27-slot buffer is full. Attach a pipe or chest. |
| Area Unloaded (red) | The next block is in an unloaded chunk. Without a Chunk Loader Upgrade the quarry waits until a player is nearby. |
| Complete (blue) | The volume is finished (and dry, with a Liquid Remover). Start runs it again. |

**Moving a quarry:** right-click it with the **Wrench**. You get one quarry item that keeps everything: progress, mode, size, depth, energy, buffer, upgrades, lists and run state. Breaking it with a pickaxe instead drops its contents.

## Upgrades

Put them in the six upgrade slots on the quarry's main page. Hover over the **UPGRADES** label to see their combined effect; the screen shows each as `count/max`.

| Upgrade | Max | Effect |
|---|---|---|
| Speed | 4 | Faster: 120 → 200 → 300 → 600 → 2400 blocks/min. Each level also costs +25% energy per block, so power draw rises steeply. |
| Energy Capacity | 4 | +100k E internal storage each. |
| Energy Efficiency | 4 | −15% energy per block each (at most −60%). |
| Fortune | 3 | Fortune I–III drops (+20% energy per level). |
| Silk Touch | 1 | Blocks drop themselves (+25% energy). Can't be combined with Fortune. |
| Range I / Range II | 1 | Area limit 32 × 32 / 64 × 64. |
| Void | 1 | Enables the Void list. |
| Chunk Loader | 1 | Keeps the quarry working with nobody nearby (see below). Costs 8 E/t while active. |
| Liquid Remover | 1 | A second arm pumps water and lava out ahead of the drill (see Liquids). |
| Auto-Smelter | 1 | Smelts ores and raw metals before they reach the buffer, 50 E per item (less with Efficiency). Fortune still multiplies; Silk Touch ore blocks smelt to one ingot. |
| Security | 1 | Deploys up to 3 drones against hostile mobs within 16 blocks (see Drones). |

Each upgrade type fits in one slot only. The Tree Farm uses Speed, Energy Capacity, Energy Efficiency and Range, plus its own Growth and Harvest upgrades.

**Chunk Loader Upgrade:** while the quarry is running (not paused or finished) and can pay the upkeep, it keeps the 3 × 3 chunks around itself ticking, so solar panels and batteries placed there keep working, and it loads whichever chunk it is mining. The chunk tickets last 5 seconds and are renewed by the running quarry, so everything unloads on its own once the quarry stops, runs out of power, loses the upgrade or is removed. After a server restart, quarries that were loading chunks get their chunks back automatically. Hover over UPGRADES to see whether chunk loading is active. Servers can disable it (`allowChunkLoading`).

## Energy

| Block | Output / capacity | Notes |
|---|---|---|
| Basic Solar Panel | 8 E/t at noon | Nothing at night; rain cuts output to 40%. |
| Advanced Solar Panel | 40 E/t at noon | Nothing at night; rain cuts output to 60%. |
| Elite Solar Panel | 180 E/t at noon | About 22 E/t at night; rain only cuts output to 80%. |
| Basic / Advanced / Elite Battery | 250k / 1.2M / 6M E | Transfer limit 256 / 1,024 / 4,096 E/t. |

Panels need open sky directly above them. Right-click a panel, battery or cable to see what it's doing. A cable shows a summary of its whole network.

Energy cables connect to each other and to panels, batteries, quarries and terminals. Batteries charge from panels and feed quarries. Power is shared fairly between all consumers on a network, and transfers are exact: energy is never created or lost. For example, a base 16 × 16 quarry mining stone draws about 8 E/t; with four Speed upgrades it draws about 330 E/t.

## Item pipes and filters

Pipes connect automatically to each other, to quarries and to chests, barrels, hoppers, furnaces and any other Fabric-compatible inventory. Items visibly travel through the glass. When a destination is full or removed, items reroute to another destination or wait in the pipe. They are never deleted, and breaking a pipe drops what's inside it.

**Filter pipes** (right-click to open):
- **Allow / Block**: Allow lets only listed items pass; Block stops them.
- **Item only / Exact**: whether the item's data (names, enchantments) must also match.
- **Templates**: up to 9 items in the grid.
- **Item tags**: up to 3, typed in, e.g. `c:ores`, `c:raw_materials`, `minecraft:logs`. The screen shows how many items each tag covers.
- **Priority** (−5 to +5).

**Routing rules**, in order:
1. Destinations behind an Allow filter that lists the item win.
2. Higher priority wins. Use this for overflow: priority +3 into the main chest, a plain branch to the overflow chest.
3. The nearest destination wins.
4. Remaining ties always go the same way, so routing is deterministic.

## Pipe tiers, Sorting Terminal and crates

| Pipe | Speed | Items per batch |
|---|---|---|
| Item Pipe | 1x | 16 |
| Reinforced Item Pipe | 2x | 32 |
| Advanced Item Pipe | 5x | 64 |

**Sorting Terminal:** connect it to a pipe network and register the chests on it. For each chest choose what it receives: **only** listed items and tags, **everything except** listed ones, or **fallback** for leftovers and overflow, plus a priority. The screen shows each chest's free space. Settings are saved and come back when a chest is broken and replaced or its chunk reloads.

**Storage Crates:** 54, 108 and 162 slots (use the page buttons). They work with pipes and hoppers, and the Wrench picks them up with everything inside.

## Liquids

With a **Liquid Remover Upgrade**, a pump arm on the gantry clears liquid ahead of the drill into the quarry's 16-bucket pump tank. Connect **Fluid Pipes** from the quarry to a **Liquid Tank** (64 buckets; fill or empty it with buckets, and a comparator reads its level). With a Void Upgrade installed, the liquid is deleted instead. Before reporting Complete, the quarry makes a final pass and removes any liquid that flowed back into the pit.

## Machines

**Super Smelter:** an electric furnace with three lanes, each 4x furnace speed, using 16 E/t per working lane and nothing while idle. Only smeltable items go in. Pipes and hoppers insert from the top and sides and extract from the bottom. You get the XP when you take the results.

**Fabrication Bench:** a searchable catalogue of every Solar Quarry recipe. Pick an item to see what it needs (have / need) and craft it straight from your inventory.

**Tree Farm:** plants saplings every third block in a square in front of it (9 x 9, or 13 x 13 and 17 x 17 with Range upgrades) on grass, dirt, podzol, moss or mud. It uses bone meal only on saplings that can take it, and its drone fells grown trees and collects the real drops. Saplings from the leaves refill its sapling slots. Trees touching built blocks (log cabins, beams) are never cut. **Growth** (max 2) adds growth ticks; **Harvest** cuts the leaves too. Powered by energy cables like the quarry.

## Drones

- **Security drones** (Security Upgrade): when hostile mobs come within 16 blocks of the quarry, it sends out up to 3 drones that fly to them and zap them. They cost 8 E/t each while out plus 60 E per hit, paid by the quarry. They never attack players, pets, villagers, the shop guard or neutral mobs, return when it's calm or power runs out, and disappear with their quarry.
- **Miner Drone** (drone slot): once the quarry's own area is finished, it tunnels from the bottom of the pit to ores within 24 blocks and sends them through the quarry's output, using the quarry's power and upgrades.

## The trader and his shop

The first time a player joins, the trader greets them and hands over the starter kit once; anything that doesn't fit arrives as soon as there is room. His shop is built once per world near spawn (sign, lanterns, a supply chest and a Fabrication Bench), with a guard who fights hostile mobs only. He sells Solar Quarry parts and buys ores and blocks for emeralds through the normal trading screen, and his stock refills every morning. If the trader or the guard dies, exactly one replacement arrives the next morning.

## Helionite and Rocket Boots

**Helionite** is an Overworld ore found deep in the deepslate. Smelt the raw helionite into ingots for tools and armour with netherite's stats; no Nether trip needed.

**Rocket Boots** (leather, iron, diamond, helionite): crouch and jump to launch. Charge them by using them on a Solar Quarry battery. Each jump costs 400 E, and the boots absorb part of your fall damage using their stored energy.

| Tier | Energy | Fall damage absorbed |
|---|---|---|
| Leather | 20k E | 40% |
| Iron | 50k E | 60% |
| Diamond | 120k E | 80% |
| Helionite | 300k E | 100% |

Higher tiers also launch you higher.

## Ore Prospector

- **Use in the air:** a list of every ore type and an exact count of the ore blocks in the chunk you're standing in, from your feet down to the world bottom.
- **Use on a quarry:** an exact count of the ores left in the part of its work volume it has not reached yet. Big volumes are scanned over a few ticks (a 64 × 64 quarry down to bedrock takes about a second), and the result arrives in chat. Blocks in unloaded chunks are reported separately, not guessed.

Each use costs 1 durability (128 total; repair with copper ingots in an anvil) and has a 5-second cooldown (server setting).

## Configuration Card

- **Sneak + use** on a quarry or filter pipe: copies its settings.
- **Use**: applies them.
- **Sneak + use in the air**: clears the card.

Quarry presets hold the mode, size, depth (relative to the quarry) and lists. Filter presets hold the mode, priority, tags and templates. Cards never copy items, energy or upgrades. Invalid or unknown values fall back to safe defaults, and cards from a newer format are refused.

## Remote Terminal

Connect it to your quarries with energy cables. The link also passes through batteries, panels and other machines on the way, so anything on the same power network is reachable. It lists up to 32 quarries (server setting), nearest first, with status, progress, energy, power flow, buffer and the storage they deliver to (inventories, used/free slots, low-space warnings). Buttons: Start, Pause, Resume, Restart, next Mode, Deeper/Shallower (±10 blocks).

You can control quarries you placed; operators can control any. Everyone else gets a read-only view. Every request is re-checked on the server. "No signal" means the data is more than 3 seconds old.

## Server settings

The server reads `config/solarquarry.json` (created with the defaults on first start). Values outside the allowed range are clamped, and a file that isn't valid JSON is left untouched while the defaults apply. The server sends its values to every player on join, so GUI numbers and tooltips match the server.

| Setting | Default | Range | Effect |
|---|---|---|---|
| `energyCostMultiplier` | 1.0 | 0.1 – 10 | Multiplies the energy every mined block costs. |
| `solarOutputMultiplier` | 1.0 | 0.1 – 10 | Multiplies solar panel output. |
| `maxRangeTier` | 2 | 0 – 2 | Largest area allowed: 0 = 16 × 16, 1 = 32 × 32, 2 = 64 × 64. |
| `allowChunkLoading` | true | | Lets the Chunk Loader Upgrade work. |
| `chunkLoaderUpkeep` | 8 | 0 – 10,000 | Energy per tick a chunk-loading quarry pays. |
| `respectProtection` | true | | Spawn protection and claim-mod checks for every mined block. |
| `prospectorCooldownTicks` | 100 | 20 – 1,200 | Ore Prospector cooldown. |
| `terminalMaxQuarries` | 32 | 1 – 64 | Quarries listed per Remote Terminal. |
| `machineSoundVolume` | 1.0 | 0 – 1 | Volume of quarry and pipe sounds (0 = silent). |

Commands: `/solarquarry config` shows the active values to anyone; `/solarquarry reload` (operators) re-reads the file and sends the new values to every player.

## Recipe viewers

All recipes are standard crafting-table recipes, so recipe viewers that read vanilla recipes (JEI, REI, EMI) should list them without any extra integration; the Fabrication Bench shows them all in game anyway.

## Known limitations

Solar Quarry 3.0 is built and tested for single-player use. Automated tests cover every feature in a dedicated-server world and a game client, including a run alongside the Fabulously Optimized modpack (166 mods). Multiplayer servers with several players and recipe viewer mods were not tested.
