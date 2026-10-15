# Progression

Solar Quarry is played in four tiers. Each tier's recipes need materials from the one before it, plus more energy to
run. Nothing is gated behind a research system: a recipe appears in the recipe book (and the Fabrication Bench) as
soon as you hold one of its key ingredients. No recipe needs anything from the Nether; **Helionite** (deep Overworld
ore, netherite-level gear) takes the place netherite has in vanilla.

| Tier | Key materials | What it unlocks |
|---|---|---|
| **1 — Basic industry** | iron, copper, redstone, glass, Circuit Board | Quarry, Basic Solar Panel and Battery, Energy Cable, Item Pipe, Wrench, Fabrication Bench, Ore Prospector, Basic Crate, Leather Rocket Boots, Industrial Power Hammer, Portable Power Cell, Industrial Repair Kit, Mining Helmet |
| **2 — Automation** | gold, lapis, Upgrade Base, Machine Frame | Speed / Capacity / Efficiency / Void upgrades, Reinforced Pipe, Filter Pipe, Filtered Splitter, Reinforced Crate, Tree Farm, Kelp Farm, Auto-Smelter Upgrade, Liquid Remover Upgrade, Charging Station, Iron Rocket Boots, Reinforced Mining Pickaxe |
| **3 — Advanced power and production** | diamond, amethyst, Advanced Circuit, ender pearls, obsidian | Advanced Solar Panel and Battery, Advanced Pipe, Sorting/Storage Terminal, Super Smelter, Fortune / Silk Touch / Range / Chunk Loader / Security upgrades, Miner Drone, Advanced Crate, Remote Terminal, Diamond Rocket Boots, powered prospectors and helmet, Night-Vision Goggles, Advanced Powered Pickaxe, Advanced Mining Scanner, Basic and Advanced Laser Turrets |
| **4 — High-end** | **Helionite**, Elite parts | Elite Solar Panel and Battery, Helionite gear and Rocket Boots, Elite Laser Turret, Weather Control Machine |

## Rules

- Early automation stays cheap: a working quarry with power and a pipe to a chest needs only Tier 1.
- Tier 4 recipes always contain Helionite (unit test `ProgressionTest`).
- Upgrades stack within fixed limits (speed 4, efficiency 4, capacity 4, fortune 3, range 2, silk touch 1 and exclusive with fortune, void 1); the GUI shows `n/max`.
- Energy grows with tier: Tier 1 machines run on a few basic panels; Tier 4 machines (Weather Control) need large arrays and Elite batteries.
- The Mechanic sells parts for emeralds as a shortcut, priced above their crafting value, and never as the only source.
- Loot only (never crafted or sold): Prototype Battery, Emergency Energy Injector.
- Structure loot is themed: common finds are Tier 1–2, rare finds Tier 3, very rare Tier 4.
