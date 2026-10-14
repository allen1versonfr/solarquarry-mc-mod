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

## Feature tracker

| # | Requirement | Status |
|---|---|---|
| 1.1 | Creative tab with every item, grouped by category | IN PROGRESS |
| 1.2 | Frame: beams, joints, bolts, hazard markings, status lights | IN PROGRESS |
| 1.3 | Frame follows range/height/mode (actual work area) | VERIFIED (2.0) |
| 1.4 | Frame collision without temporary blocks or entities | IN PROGRESS |
| 1.5 | Two-axis rails, carriage, drill reaching the target | VERIFIED (2.0, screenshots) |
| 1.6 | Drill stops / changes state when paused, unpowered, blocked, finished | VERIFIED (2.0) |
| 1.7 | GUI: measured blocks per second (rolling window, successful mines only) | IN PROGRESS |
| 1.8 | GUI: upgrade counts `n/max`, slots used | IN PROGRESS |
| 1.9 | Upgrade limits: speed 4, range 2, efficiency 4, capacity 4, fortune 3, silk 1 (exclusive), void 1 | IN PROGRESS |
| 1.10 | Invalid insertion rejected (GUI, shift-click, automation) | IMPLEMENTED, NOT VERIFIED |
| 1.11 | Save/reload, pickup, full output, no power | VERIFIED (2.0/2.1) |
| 2.x | Energy, pipe tiers, Sorting Terminal | NOT STARTED |
| 3.x | Auto-Smelter Upgrade, Super Smelter | NOT STARTED |
| 4.x | Trader, starter kit, shop, guard, trades, replacement | NOT STARTED |
| 5.x | Tree Farm, drone, upgrades | NOT STARTED |
| 6.x | Security Upgrade, drones | NOT STARTED |
| 7.x | Rocket Boots (4 tiers) | NOT STARTED |
