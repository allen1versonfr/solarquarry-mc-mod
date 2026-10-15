# Major upgrade — Checkpoint A audit (2026-10-15)

Baseline: **3.0.0** (`release/checkpoints/solarquarry-3.0.0.jar`). Last recorded run on Ali's PC: clean build, all unit tests, 302/302 self-test checks, client game test passed, modpack compat run passed (logs dated 2026-10-15).
Toolchain unchanged: Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2, Loom 1.18, Java 25.

**Build status this session: not re-run.** This session can read and write the project folder but cannot run commands on the PC, so `!run.bat` has to be started by hand (it applies `sync.zip`, builds, runs the self-test and client test, copies the jar). Nothing below has been re-verified since the 3.0.0 run.

## Feature inventory against the upgrade spec

| Spec § | Feature | What exists in 3.0.0 | Gap |
|---|---|---|---|
| 3 | Shared self-building framework | None. Quarry "construction" is the frame renderer + collision mixin (`QuarryFrame`, `EntityCollisionMixin`) — no blocks are placed, by design. Shop is placed instantly by `ShopBuilder`. | Entire framework is new. Quarry has nothing to migrate block-wise; integration = lifecycle/progress states only. |
| 10 | Tree Farm | Full implementation (planting grid, bone meal, upgrades, renderer drone, structure-safe `TreeScan`), self-test passes. | **Real-world failure — see below.** No self-building. |
| 4 | Filtered Splitter | Filter Pipe with tags/priorities (`FilterPipeMenu`, `PipeRouter`). | New block + GUI needed; keep Filter Pipe. |
| 5 | Storage Terminal | **Sorting Terminal** already does assignment (only/except/fallback), tags, priorities, overflow, persistence. Remote Terminal is quarry monitoring, not storage. | Missing: unified browse/search/withdraw/deposit across chests. Plan: extend the Sorting Terminal rather than add a second block. |
| 6 | Creative tab | One grouped tab, self-test checks every item appears exactly once. | Mostly done; custom icon/polish only. |
| 7 | Progression | Recipes exist for everything; no Nether items (unit-tested). | Tier document + recipe rebalance. |
| 8 | Mechanic | "Solar Quarry trader" with shop, guard, starter kit, emerald trades, daily restock, no buy-sell loop, one replacement after death. | Rename to Mechanic, trades for every item, unlimited stock, guard model, visible house construction. |
| 9 | Kelp Farm | None. | New. Blocked on §3 gate. |
| 12 | Charging Station | None. Boots charge by using them on a battery. | New. |
| 13 | Rocket Boots crouch-to-fly | 4 tiers (leather/iron/diamond/helionite — **no netherite tier**; helionite fills that slot), crouch + jump launch, server-validated, fall protection. | Change to crouch-to-fly. Spec lists Netherite; existing equivalent is Helionite (netherite stats, no Nether items). |
| 14 | Light-level helmet | None. | New. |
| 15 | Powered Ore Prospector | Exists, unpowered, cooldown only. | Add charge. |
| 16–17 | Liquid / Slime Prospector | None. | New. |
| 18 | Liquid Remover Arm | Exists as Liquid Remover Upgrade (pumps ahead of the drill, final pass before Complete; self-test pumps 18 buckets). | Large-pocket flood-fill + progress/GUI reporting. |
| 19 | Laser Turrets | None (security drones exist). | New. |
| 20–21 | 10 structures + new loot items | No structures except the shop; no chest loot tables. | All new. Largest single chunk of work. |
| 26 | Weather Control Machine | None. Sounds: only 5 .ogg files exist. | New; needs ~16 original sound assets. |

## Tree Farm "does nothing" — diagnosis (from code; not yet confirmed in game)

The self-test passes because its setup is unlike a real world: it **clears every block above the ground to air** and **fills the farm's energy directly** (`restoreEnergy`). In a normal world:

1. **Vegetation blocks every planting spot (most likely cause).** `TreeFarmBlockEntity.groundSpot()` only accepts a spot whose block is exactly air with air above it. Short grass, tall grass, flowers, ferns, leaf litter, bushes or snow layers make the spot invalid. On grassland nearly every spot fails, so `plantNext()` reports no free spot and `work()` falls through to `boneMealNext()`, which returns **IDLE** — the GUI shows Idle and nothing happens, with no explanation.
2. **Spots are fixed to the machine's own height ±1.** On a slope or if the farm sits on a block above ground, spots fall outside that band and are skipped silently.
3. **Power via cables is never exercised by the test.** Probably fine (direct adjacency and cables both reach `EnergyNode`s), but unverified for this machine.

Planned fix (Checkpoint B): treat replaceable plants (`canBeReplaced()` non-fluid) as clearable, clear them as part of planting; search ground per spot within a small vertical window; add explicit statuses ("Spot blocked", "No valid ground", "No power") and a self-test scenario on natural grassy terrain powered through a cable.

## Spec points that conflict with standing project rules

- **Multiplayer / dedicated-server testing:** the mod is for solo play on the Fabulously Optimized instance; the existing self-test already runs on a dedicated server, which covers server/client separation. No multiplayer test passes planned.
- **"Netherite" tier anywhere in the spec → Helionite** (Ali, 2026-10-15). Recipes never use Nether items.
- **Unlimited Mechanic stock vs. exploits:** unlimited only for *sell* offers (player buys parts); *buy* offers (player sells ores) keep a cap or price floor so emeralds can't loop.
- **Git checkpoints:** the PC folder has no `.git`; commits are made in a clone of github.com/allen1versonfr/solarquarry-mc-mod and pushed after each checkpoint passes `!run.bat` (approved by Ali, 2026-10-15).

## Proposed order

A (this) → B framework + Quarry lifecycle + Tree Farm fix → C tab/progression → D Filtered Splitter → E Storage Terminal → F Kelp Farm → G Mechanic → H Charging Station + powered tools + boots → I Liquid Remover → J Turrets → K structures/loot (one at a time) → Weather Control Machine → L release audit.
Each checkpoint ships as a `sync.zip` in the project folder, then `!run.bat` builds and tests it.
