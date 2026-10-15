# Master plan — feature tracker

Statuses: NOT STARTED · IN PROGRESS · IMPLEMENTED, NOT VERIFIED · VERIFIED · BLOCKED.
VERIFIED means an automated in-world check (dedicated-server self-test or client game test) or a unit test exercises it. Compiling is not verification.

## Checkpoint A — audit of 2.1.0 (2026-10-14)

**Baseline:** 2.1.0, commit `698dcaa`. Clean build without warnings, 31/31 unit tests, 155/155 self-test checks, client game test passed, plus a run with the 166 mods of the Fabulously Optimized instance. Backup: `Documents\SolarQuarry-backup\SolarQuarry-2.1.0-source.zip` and the 2.1.0 jar.

**Versions:** Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.159.0+26.2, Loom 1.18.4, Gradle 9.7.1, Java 25. All current for 26.2; no changes needed.

**Already in place:** quarry with 4 modes, state machine, 8 upgrade types, rendered gantry with posts, rails, bridge (forward axis), carriage (lateral axis) and drill shaft, solar/battery/cable energy with exact accounting, item and filter pipes with tags and priorities, prospector, presets, remote terminal, chunk loader, server config, one creative tab.

**Gaps against the plan:**
- Frame has no collision (render only); no bolts, joints or post lights.
- GUI shows theoretical speed (blocks/min), not measured blocks per second; upgrade counts are not shown as `n/max`.
- Efficiency limit is 3 (plan: 4).
- Creative tab is in registration order, not grouped.
- One pipe tier only; no Sorting Terminal.
- No smelting, trader, tree farm, security drones or rocket boots.

**Risks noted:** the frame collision needs a hook into entity movement (a small mixin), because block shapes cannot cover a frame up to 64 × 64 without placing blocks in the world. Entities (trader, guard, drones) need careful duplicate prevention across chunk reloads and restarts.

**Architecture decisions:**
- Frame collision: one MixinExtras hook on `Entity#collide` adds the frame's beam boxes; no blocks or entities are created.
- Trading: the vanilla merchant screen, so every trade is validated by the game's own server logic; emeralds as currency.
- Tree-farm drone: rendered by the machine (no entity), like the quarry drill.
- Security drones and the trader/guard: real server-side entities with persistent ownership and duplicate checks.
- Rocket boots: store energy (E) charged from Solar Quarry batteries, consistent with the rest of the mod.

## Checkpoint B — Phase 1 (commit after `e0d468f`)

- **Changed:** shared frame geometry (`QuarryFrame`) used by the renderer and a collision hook (`EntityCollisionMixin`); steel joints, base plates, braces and status lights on the posts; near posts kept clear of the machine; measured blocks per second (`RateWindow`); upgrade counts `n/max` and slots used in the upgrade tooltip; efficiency limit 4; grouped creative tab.
- **Results:** clean build, no warnings; 39/39 unit tests; 168/168 self-test checks; client game test passed with 9 screenshots.
- **Jar:** `release/checkpoints/solarquarry-3.0.0-beta.1.jar`.
- **Known defects:** none found.

## Feature tracker

| # | Requirement | Status |
|---|---|---|
| 1.1 | Creative tab with every item, grouped by category | VERIFIED (self-test: every item exactly once, grouped order) |
| 1.2 | Frame: beams, joints, base plates, braces, hazard markings, status lights on the posts | VERIFIED (client screenshots) |
| 1.3 | Frame follows range/height/mode (actual work area) | VERIFIED (2.0) |
| 1.4 | Frame collision without temporary blocks or entities | VERIFIED (self-test: a moving entity stops at the post; open area has none; removed with the quarry) |
| 1.5 | Two-axis rails, carriage, drill reaching the target | VERIFIED (2.0, screenshots) |
| 1.6 | Drill stops / changes state when paused, unpowered, blocked, finished | VERIFIED (2.0) |
| 1.7 | GUI: measured blocks per second (10 s rolling window, successful mines only) | VERIFIED (self-test: 2.0 b/s measured at a 2.0 b/s limit) |
| 1.8 | GUI: upgrade counts `n/max`, slots used | VERIFIED (client test reads "Speed 2/4", "Slots used: 4/6") |
| 1.9 | Upgrade limits: speed 4, range 2, efficiency 4, capacity 4, fortune 3, silk 1 (exclusive), void 1 | VERIFIED (self-test) |
| 1.10 | Invalid insertion rejected (GUI slot, duplicate types, automation) | VERIFIED (self-test); shift-click uses the same slot rules |
| 1.11 | Save/reload, pickup, full output, no power | VERIFIED (2.0/2.1) |
| 1.12 | Quarry lights the pit with torches from a torch slot | VERIFIED (self-test: 8 wall torches, 56 left) |
| 1.13 | Legs grow to the ground; support under a floating machine | VERIFIED (self-test; client screenshot 12) |
| 1.14 | Drill stays on the frame after a range change | VERIFIED (client screenshot 11) |
| 1.15 | Printer-style gantry: bridge on both rails, carriage on the bridge | VERIFIED (client screenshots) |
| 1.16 | Mining experience collected by the drill, taken with a button | VERIFIED (self-test) |
| 1.17 | Prospector lists every ore | VERIFIED (self-test) |
| 2.1 | Energy: solar, batteries, cables, exact accounting | VERIFIED (2.x self-tests, still passing) |
| 2.2 | Pipe tiers basic / reinforced / advanced | VERIFIED (self-test: timed delivery) |
| 2.3 | Sorting Terminal: listed/except/fallback, tags, priority, overflow, persistence, recovery | VERIFIED (self-test, client screenshot 10) |
| 2.4 | Liquid Remover Upgrade, fluid pipe, Liquid Tank (buckets; void deletes) | VERIFIED (self-test: 18 buckets pumped, tank, bucket, void; client screenshots 13-14) |
| 2.5 | Storage crates 54/108/162 slots | VERIFIED (self-test: paging, automation; client screenshot 15) |
| 3.1 | Auto-Smelter Upgrade | VERIFIED (self-test: 18 ore -> 18 ingots, stone untouched, XP) |
| 3.2 | Super Smelter (3 lanes, 4x, 16 E/t per lane, no idle draw) | VERIFIED (self-test: speed, exact energy, no power, full output, sided automation, save/load; screenshot 16) |
| 3.3 | Fabrication Bench with recipe catalogue | VERIFIED (self-test: catalogue, craft all, tags; screenshot 17) |
| 3b.1 | Helionite ore (Overworld deepslate), ingot, block | VERIFIED (self-test: tags, smelting, feature registered) |
| 3b.2 | Helionite tools and armour = netherite stats | VERIFIED (self-test compares durability and attributes) |
| 3b.3 | No Nether items in any recipe | VERIFIED (unit test scans every recipe) |
| 4.1 | First-join starter kit, once per player, parcel when full | IMPLEMENTED, NOT VERIFIED |
| 4.2 | Shop with guard, lighting, sign, bench, supply chest | IMPLEMENTED, NOT VERIFIED |
| 4.3 | Buy/sell through the vanilla merchant screen, daily restock, no buy-sell loop | IMPLEMENTED, NOT VERIFIED |
| 4.4 | Death, next-morning replacement, no duplicates | IMPLEMENTED, NOT VERIFIED |
| 5.x | Tree Farm, drone, growth/harvest upgrades | IMPLEMENTED, NOT VERIFIED |
| 6.x | Security Upgrade drones; miner drone | NOT STARTED |
| 7.x | Rocket Boots (leather, iron, diamond, helionite) | NOT STARTED |
