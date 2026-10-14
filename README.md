# Solar Quarry 2.0

A standalone Fabric mod for Minecraft 26.2: a solar-powered quarry with four mining modes, an energy network (solar panels, batteries, cables), real item logistics (pipes, filter pipes with tags and priorities), an ore prospector, configuration presets and a remote control terminal. No other tech mod is needed.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.159.0+26.2 (or newer for 26.2) |
| Java | 25 |

Nothing else is required. Install the mod on the server **and** on every client.

## Which file do I want?

- **`solarquarry-2.0.0.jar`**: the mod. Put it in your `mods` folder. That's all you need to play.
- **`SolarQuarry-2.0.0-source.zip`**: the full Gradle project (source code, assets, tests, build scripts). Only needed if you want to build or change the mod.

## Building from source

```
gradlew.bat build        (Windows)
./gradlew build          (Linux / macOS)
```

The jar is written to `build/libs/solarquarry-2.0.0.jar`. `build` also runs the unit tests.

Helper scripts (Windows): `0-get-jdk.bat` downloads a portable JDK 25 into `.jdk/` if you don't have Java 25. `2-build.bat` builds and logs to `logs/build.log`. `3-selftest.bat` runs the dedicated-server self-test (about 120 automated in-world checks; results in `logs/selftest.log`). `5-clienttest.bat` runs the client game test, which saves screenshots to `run-clienttest/screenshots`.

## Getting started

1. Craft a **Quarry**, a few **Basic Solar Panels**, a **Basic Battery**, **Energy Cables**, **Item Pipes** and a chest. Every recipe appears in the vanilla recipe book once you pick up one of its ingredients.
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

**Always skipped:** air, unbreakable blocks, anything outside the world border or inside spawn protection (when the owner is online), Solar Quarry's own blocks, containers (so chests are never broken open), and the data-pack tag `solarquarry:quarry_ignored` (spawners, portals and similar).

**Status** is shown in the GUI, on the front lamp and on the Remote Terminal. The bottom-left line of the GUI tells you what to do:

| Status | Meaning |
|---|---|
| Running / Scanning (green) | Working. Scanning means Ore Priority is looking for ores. |
| Paused (yellow) | You paused it. Resume continues from the same block. |
| No Power (red) | Not enough stored energy for the next block. |
| Output Full (red) | The 27-slot buffer is full. Attach a pipe or chest. |
| Area Unloaded (red) | The next block is in an unloaded chunk. The quarry waits; it never loads chunks itself. |
| Complete (blue) | The volume is finished. Start runs it again. |

**Moving a quarry:** right-click it with the **Wrench**. You get one quarry item that keeps everything: progress, mode, size, depth, energy, buffer, upgrades, lists and run state. Breaking it with a pickaxe instead drops its contents.

## Upgrades

Put them in the six upgrade slots on the quarry's main page. Hover over the **UPGRADES** label to see their combined effect.

| Upgrade | Max | Effect |
|---|---|---|
| Speed | 4 | Faster: 120 → 200 → 300 → 600 → 2400 blocks/min. Each level also costs +25% energy per block, so power draw rises steeply. |
| Energy Capacity | 4 | +100k E internal storage each. |
| Energy Efficiency | 3 | −15% energy per block each. |
| Fortune | 3 | Fortune I–III drops (+20% energy per level). |
| Silk Touch | 1 | Blocks drop themselves (+25% energy). Can't be combined with Fortune. |
| Range I / Range II | 1 | Area limit 32 × 32 / 64 × 64. |
| Void | 1 | Enables the Void list. |

Each upgrade type fits in one slot only.

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

## Ore Prospector

- **Use in the air:** an exact count of the ore blocks in the chunk you're standing in, from your feet down to the world bottom.
- **Use on a quarry:** an *estimate* of the ores left in its remaining work volume, sampled from 1 in 16 columns and clearly labelled as an estimate.

Each use costs 1 durability (128 total; repair with copper ingots in an anvil) and has a 5-second cooldown.

## Configuration Card

- **Sneak + use** on a quarry or filter pipe: copies its settings.
- **Use**: applies them.
- **Sneak + use in the air**: clears the card.

Quarry presets hold the mode, size, depth (relative to the quarry) and lists. Filter presets hold the mode, priority, tags and templates. Cards never copy items, energy or upgrades. Invalid or unknown values fall back to safe defaults, and cards from a newer format are refused.

## Remote Terminal

Place it on the **same cable run** as your quarries; machines don't pass the link through, so connect the terminal's cable directly to each quarry's cable. It lists up to 32 quarries, nearest first, with status, progress, energy, power flow, buffer and the storage they deliver to (inventories, used/free slots, low-space warnings). Buttons: Start, Pause, Resume, Restart, next Mode, Deeper/Shallower (±10 blocks).

You can control quarries you placed; operators can control any. Everyone else gets a read-only view. Every request is re-checked on the server. "No signal" means the data is more than 3 seconds old.
