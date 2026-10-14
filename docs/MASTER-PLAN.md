<system-reminder>
Result of calling the Read tool:
# SOLAR QUARRY — MASTER DEVELOPMENT PLAN
## Major Upgrade | Minecraft Java Edition 26.2 | Fabric

## 1. YOUR ROLE

Act as the lead Java developer, Minecraft mod architect, gameplay designer, technical artist, UI/UX designer, performance engineer and QA engineer for Solar Quarry.

You are modifying an existing project. Your job is to inspect, improve, build, test and deliver the actual mod—not merely produce recommendations, code snippets or mockups.

The goal is a polished, balanced, standalone industrial automation mod featuring automated mining, electricity, item logistics, sorting, smelting, tree farming, security drones, a trader NPC, powered equipment and professional industrial visuals.

The finished mod must feel cohesive: every machine, upgrade, resource, NPC and energy system should work together.

## 2. NON-NEGOTIABLE DEVELOPMENT RULES

1. Target Minecraft Java Edition 26.2 and Fabric.
2. Inspect the existing project before modifying it. Preserve working features and avoid unnecessary rewrites.
3. Use compatible Java, Fabric Loader, Fabric API, Gradle and Loom versions. Verify APIs against current 26.2 documentation and working examples.
4. Keep the mod standalone. Do not require Create, TechReborn or another technology mod.
5. Do not replace real functionality with placeholders, decorative-only systems, empty buttons or fake GUI values.
6. Never claim that a build, test or gameplay feature works unless it has actually been verified.
7. Build and test after every major phase. Fix regressions before proceeding.
8. Use Git checkpoints and preserve a separate backup of the original project.
9. Do not delete user files, overwrite worlds, change Minecraft versions or perform destructive Git operations without explicit approval.
10. Keep server-authoritative logic separate from client-side rendering and animation.
11. Prioritize correctness, item safety, persistence and performance over feature count.
12. If a feature is blocked, document the exact reason and continue only where dependencies permit.
13. Do not silently omit requirements. Maintain a feature checklist throughout development.
14. Do not stop after generating code. The goal is a complete, buildable, tested release.

## 3. PROJECT AND VERSION CONTROL RULES

Use one clearly defined working directory, one original backup and one release output directory.

Before editing:
- Inspect the full repository and build configuration.
- Check the current Git status.
- Preserve any existing user changes.
- Create a baseline commit if Git is available and the working tree can safely be committed.
- Never discard uncommitted work to force a clean state.
- Record the baseline build result.

After each stable phase:
- Inspect the source diff.
- Remove accidental changes and temporary debug code.
- Run the required build and tests.
- Update the feature checklist and changelog.
- Commit the stable result with a meaningful message.

Do not create multiple confusing project copies in Downloads. Keep temporary artifacts inside the project or a dedicated build directory.

## 4. MANDATORY CHECKPOINT AND RECOVERY SYSTEM

Checkpoints are required, not optional.

### CHECKPOINT A — Initial audit

Before feature development, report:
- Existing features and their implementation status.
- Current build result and any existing failures.
- Dependency and API compatibility.
- Existing architectural problems.
- Missing assets and registrations.
- Persistence, inventory and energy risks.
- Proposed phase order and dependencies.

Do not start major feature development until the baseline has been assessed. If the project does not compile, fix the baseline first and record those changes separately.

### CHECKPOINT B — After quarry improvements

Verify:
- Quarry mining and pause/resume.
- Dynamic frame and collision.
- Two-axis rails and drill movement.
- Upgrade insertion, limits and effects.
- GUI accuracy and blocks-per-second calculation.
- Save/reload and machine pickup.
- No item loss, duplication or unexpected world modification.

Build, test, inspect the diff and commit.

### CHECKPOINT C — After energy and logistics

Verify:
- Energy generation, consumption, transfer and storage.
- Upgrade-dependent power demand.
- Pipe connectivity, transfer limits and branching.
- Sorting Terminal destination assignments and filters.
- Full inventories, disconnected routes and chunk unloading.
- Save/reload and item safety.

Build, test, inspect the diff and commit.

### CHECKPOINT D — After smelting

Verify:
- Auto-Smelter Upgrade.
- Standalone Super Smelter.
- Valid recipes and output handling.
- Power consumption and processing speed.
- Full output inventories and depleted energy.
- Automation through pipes and compatible inventories.

Build, test, inspect the diff and commit.

### CHECKPOINT E — After trader implementation

Verify:
- First-join introduction.
- Once-only starter reward.
- Reward delivery when the player's inventory is full.
- Shop placement, guard and trading.
- Sell and purchase validation.
- Trader death and replacement on the next in-game morning.
- No duplicate rewards, shops or NPCs after restart.

Build, test, inspect the diff and commit.

### CHECKPOINT F — After tree farming

Verify:
- Sapling input and valid planting.
- Bone meal consumption and growth behavior.
- Harvesting drone operation.
- Tree detection and protection of unrelated structures.
- Replanting and output collection.
- Tree Farm upgrades and energy balance.
- Chunk unloading and save/reload.

Build, test, inspect the diff and commit.

### CHECKPOINT G — After security and equipment

Verify:
- Security Upgrade and drone limits.
- Correct hostile-mob targeting.
- Drone energy use and lifecycle management.
- Rocket Boots' four tiers.
- Crouch-jump activation, fall protection and power behavior.
- Death, dimension changes and unequipping.
- Multiplayer synchronization and no permanent movement changes.

Build, test, inspect the diff and commit.

### CHECKPOINT H — Final release audit

Before declaring completion:
- Run a clean full Gradle build.
- Run all available automated tests.
- Launch a real Minecraft 26.2 Fabric instance where possible.
- Test the final JAR in a separate test profile.
- Review logs for exceptions, missing resources and repeated warnings.
- Inspect recipes, creative tab entries, models, textures, tooltips and GUIs.
- Verify the final artifact actually exists and is not an old JAR from a previous build.
- Produce a release report with confirmed results and unverified items.

If a checkpoint fails, stop adding new features. Diagnose the root cause, repair it, rerun the relevant tests and only then continue.

If a failure cannot be resolved, record the blocker and affected features. Do not pretend the checkpoint passed.

## 5. FEATURE TRACKER

Maintain a checklist with these statuses:
- NOT STARTED
- IN PROGRESS
- IMPLEMENTED, NOT VERIFIED
- VERIFIED
- BLOCKED

Track each requirement individually, not just each phase. A successful compilation does not automatically mark gameplay features as verified.

At every checkpoint, report:
1. What changed.
2. Files or systems affected.
3. Build and test results.
4. Known defects.
5. Requirements still incomplete.
6. Next phase and its dependencies.
7. Current Git checkpoint.

## 6. PHASE 1 — QUARRY, GUI AND CREATIVE TAB

### Creative tab

Create a dedicated Solar Quarry creative tab containing every custom block and item. Organize the entries logically: quarry components, energy, pipes, sorting, upgrades, smelting, tree farming, security, trader-related items and equipment.

Every item must have a proper name, icon, tooltip, registration and intended acquisition method. Use the correct current Fabric 26.2 creative-tab API.

### Quarry frame and collision

Improve the yellow quarry frame with a coherent industrial design:
- Reinforced structural beams.
- Metal joints, bolts and hazard markings.
- Appropriate status lights and detailed textures.
- A visible working boundary that matches the actual mining area.

The frame must expand or reconfigure as range and height upgrades change the operating dimensions. Its collision must prevent players from walking through structural beams while preserving legitimate access to the machine and work area.

Do not create uncontrolled numbers of temporary blocks or entities. Use suitable collision shapes and a manageable implementation that works correctly on the server.

### Two-axis rail-mounted drill

The drill assembly must visibly travel on two perpendicular rail axes, moving forward/backward and left/right, with a drill head reaching the current target.

Animation must correspond to actual mining progress. The drill must stop or change state when paused, unpowered, blocked or finished.

Use lightweight client-side animation and properly synchronized server state. Do not create a separate entity for every moving part.

### Quarry GUI

Show:
- Mining state, coordinates and current depth.
- Selected area and operating dimensions.
- Mining progress.
- Blocks mined per second.
- Energy stored, capacity, consumption and effective speed.
- Inventory and output status.
- Installed upgrades and remaining capacity.
- Filters, routing and actionable warnings.

Calculate blocks per second from successful mining operations over a sensible rolling window. Do not use hardcoded numbers or count failed attempts.

### Upgrade limits and balance

Define one authoritative specification for all upgrade categories, stacking limits and compatibility.

Proposed starting values:
- Speed: maximum 4.
- Range: maximum 2.
- Efficiency: maximum 4.
- Capacity: maximum 4.
- Fortune and Silk Touch: incompatible unless a valid, intentional mechanic permits otherwise.
- Void: maximum 1.

Reconcile these values with the existing system before implementing them.

Clearly display quantities such as `Speed 3/4`, occupied slots and remaining capacity. Reject invalid insertions safely, including shift-click and automated insertion.

Use documented formulas for speed, energy consumption, efficiency and range. Avoid extreme jumps between upgrade levels, integer overflow, negative costs or unlimited output.

### PHASE 1 EXIT GATE

The quarry must build, mine correctly, display truthful status, enforce upgrade limits, save and reload its state, and handle full output and depleted energy safely. Commit only after the relevant checks pass.

## 7. PHASE 2 — ENERGY AND PIPE NETWORK

Implement a consistent standalone energy system with solar panels, batteries and cables.

Requirements:
- Solar generation reflects daylight and sky exposure.
- Batteries respect capacity and transfer limits.
- Energy is never created or duplicated by transfers.
- Machines consume energy according to actual work.
- Higher speed and additional machines create meaningful power demand.
- Efficiency reduces consumption gradually without eliminating costs.
- Disconnected networks recover safely.
- Energy and errors are displayed clearly in machine GUIs.

Create basic, reinforced and advanced pipe tiers with progressively higher transfer rates, distinct recipes and appropriate models.

Pipes must connect to compatible pipes, quarry outputs, sorting terminals and supported inventories. Transfers must use real server-side item stacks, respect inventory rules and retain items safely when destinations are blocked or full.

Implement bounded routing, reconnection handling and loop prevention. Avoid unbounded searches and full-network scans every tick. Visual item movement must never be the authoritative inventory system.

### Sorting Terminal

Add a configurable terminal for registering connected chests and assigning items to destinations.

Support:
- Exact item and suitable tag-based filters.
- Allow/block rules.
- Multiple items per destination.
- Destination priorities.
- Fallback and overflow destinations.
- Connection status, free-space information where available, and routing warnings.

Example configuration:
- Ores and raw metals → Chest A.
- Stone and cobblestone → Chest B.
- Logs and saplings → Chest C.
- Valuables → Chest D.
- Unmatched items → a configured fallback or the source machine's buffer.

These are configurable examples, not hardcoded destinations.

Prevent routing loops, silent item deletion and duplication. Persist filters and destination rules. Recover safely when chests are broken, replaced or unloaded.

### PHASE 2 EXIT GATE

Verify energy accounting, all pipe tiers, branching, filtering, full inventories, disconnected routes, sorting persistence and chunk unload/reload behavior. Commit after passing.

## 8. PHASE 3 — SMELTING MACHINES

### Auto-Smelter Upgrade

Add a quarry-compatible upgrade that processes eligible mined materials using valid Minecraft recipes.

It must:
- Consume additional energy while operating.
- Handle recipe inputs and outputs correctly.
- Retain items when output is full.
- Route finished products into the machine buffer or connected pipes.
- Explain its behavior, power cost and compatibility in tooltips.
- Define interactions with Fortune, Silk Touch and filters.

### Super Smelter

Add a separate electric smelting machine with:
- Input, output and energy handling.
- Faster processing than a vanilla furnace.
- Valid recipe support.
- Compatible pipe automation and sided inventory access.
- A polished GUI showing energy, progress and status.
- Safe handling of full output, invalid inputs and power loss.

Define and test the speed multiplier, per-operation energy cost and any idle consumption. Faster operation must be balanced by energy demand and crafting cost.

### PHASE 3 EXIT GATE

Test recipe correctness, power depletion, full outputs, automation and save/reload. Commit after passing.

## 9. PHASE 4 — TRADER, SHOP AND STARTER KIT

Create a unique industrial trader who introduces the player to Solar Quarry.

### First arrival

When a player enters a new world for the first time, schedule a controlled introduction and give them the basic components needed to craft or assemble the starter quarry.

The reward must be claimed only once per player by default. Persist reward state and provide a safe delivery method if inventory is full.

The introduction must not depend on an arbitrary random chance that could prevent the player from receiving the starter kit.

### Permanent shop and guard

The trader establishes a small industrial shop with a guard, suitable lighting, signage, matching blocks and useful starter supplies.

Choose a safe placement. Provide a fallback if the intended location is unsuitable. Avoid obstructing structures or placing the shop inside solid blocks, lava or inaccessible terrain.

### Buying and selling

The trader must sell selected mod items and buy eligible blocks and ores at displayed, balanced prices.

Implement:
- Server-validated trade transactions.
- Correct inventory updates.
- Defined stock and restocking rules.
- Protection against duplication and infinite-money exploits.
- Clear feedback for unavailable trades.

### Death and replacement

If the trader dies, persist the absence and schedule one replacement for the following in-game morning. Prevent duplicates across restarts, unloaded chunks and repeated scheduling events. Restore the replacement's shop association and trade functionality.

The guard defends against eligible hostile mobs without arbitrarily attacking players, pets or neutral NPCs.

### PHASE 4 EXIT GATE

Verify first-join reward delivery, multiplayer behavior, trading, death, next-morning replacement and world-reload recovery. Commit after passing.

## 10. PHASE 5 — TREE FARM

Create a separate automated Tree Farm machine.

### Planting and growing

- Configurable working area with range upgrades.
- Sapling input through inventory or compatible pipes.
- Plant only in valid locations with suitable ground and space.
- Respect actual tree growth conditions.
- Accept bone meal as a separate consumable growth accelerator.
- Consume bone meal only when a valid attempt is made.
- Preserve supplies if planting or growth cannot occur.
- Replant eligible trees when saplings are available.

### Harvesting drone

Use a visible forestry drone or mechanical harvesting unit to process eligible trees.

It must:
- Identify suitable trees within the configured area.
- Harvest connected tree blocks using bounded, safe tree-detection logic.
- Avoid cutting arbitrary player-built wooden structures.
- Collect actual drops into the machine buffer.
- Replant using available saplings.
- Pause when energy is depleted or storage is full.
- Show the current target, progress and status.

### Tree Farm upgrades

Provide compatible Range, Speed, Efficiency, Storage and Growth Accelerator upgrades. Add a Harvesting Upgrade if it offers a distinct, useful mechanic.

Show all limits and quantities clearly. Prevent quarry-only upgrades from being installed in incompatible Tree Farm slots.

Tree Farm energy demand must reflect area, harvesting speed and growth acceleration. Bone meal does not replace energy.

### PHASE 5 EXIT GATE

Test different tree types, large trees, invalid planting positions, insufficient supplies, full output, harvesting boundaries, chunk unloads and reloads. Confirm that unrelated structures are preserved. Commit after passing.

## 11. PHASE 6 — SECURITY DRONES

Add a Security Upgrade that allows a quarry to deploy attack drones within a defined defensive radius.

Requirements:
- Dedicated compatible upgrade slot.
- Configurable or documented radius and maximum active drone count.
- Clear status and deployed-drone count.
- Energy consumption while operating.
- Additional demand from active drones and combat.
- Safe drone recall or deactivation.
- Server-authoritative entity AI and combat.

Drones target eligible hostile mobs while avoiding players, pets, villagers, the trader's guard and neutral entities unless an intentional rule explicitly permits otherwise.

Use bounded target searches, sensible AI update intervals and safe lifecycle handling. Drones must not duplicate after chunk reloads or accumulate indefinitely. They should return, deactivate or despawn safely when their quarry is removed or their operating conditions end.

Give drones proper models, movement, attacks, health, sounds, particles and combat feedback. Balance damage, durability, capacity and energy use.

### PHASE 6 EXIT GATE

Test target selection, multiplayer synchronization, energy depletion, quarry removal, chunk unloading and drone persistence. Commit after passing.

## 12. PHASE 7 — ROCKET BOOTS

Create Leather, Iron, Diamond and Netherite Rocket Boots.

All tiers support crouch-jump activation, increased jump height and tier-appropriate fall protection.

Progression:
- Leather: modest jump, basic protection and low capacity.
- Iron: improved jump, protection and capacity.
- Diamond: strong jump, high protection and larger capacity.
- Netherite: best performance, high durability and expensive crafting.

Define a consistent energy or recharge system and show its status in the tooltip or interface. Prevent activation when requirements are not met.

Use server-validated movement and fall-protection logic. Handle interrupted jumps, high falls, item swapping, death, logout and dimension changes. Do not permanently modify movement attributes or allow unintended infinite flight.

Give each tier a real gameplay difference, coherent texture/model and appropriate recipe.

### PHASE 7 EXIT GATE

Test activation, energy depletion, fall protection, tier differences, multiplayer corrections and equipment lifecycle. Commit after passing.

## 13. SHARED SYSTEMS, VISUALS AND COMPATIBILITY

Create reusable components where systems genuinely overlap:
- Energy storage and transfer.
- Inventories and sided automation.
- Upgrade validation.
- Persistent configuration.
- GUI status and warnings.
- Pipe connectivity and routing.
- Network synchronization.
- Machine removal and recovery.

Do not overengineer a generic framework if it makes machine behavior harder to maintain.

Use a consistent industrial design: reinforced dark metal, selective yellow hazard markings, mechanical rails, readable lights, high-quality icons and restrained accents.

Every block and item must have valid registrations, correct models and textures, useful tooltips, proper recipes and creative tab entries. Every GUI must be readable at common interface scales and support correct inventory interactions.

Use current 26.2 APIs and keep client-only rendering separate from dedicated server code.

## 14. PERFORMANCE AND DATA SAFETY

Server-side logic is authoritative for mining, harvesting, inventory movement, energy, trades, drone combat and movement-related effects.

Requirements:
- Validate client requests.
- Prevent inventory duplication and loss.
- Avoid unbounded recursion and world scans.
- Avoid excessive packets and unnecessary tick work.
- Do not force-load chunks without an explicit, safe design.
- Handle invalid saved data and missing fields.
- Handle machine breaking, replacement and connected-network changes.
- Avoid duplicate NPCs, drones, rewards or shop structures.
- Avoid repeatedly allocating expensive rendering or routing objects.
- Recover gracefully when other mods expose incompatible blocks or inventories.

Test single-player and a dedicated server if available.

## 15. AUTOMATED AND MANUAL TESTING

Create automated tests for pure logic where practical, particularly:
- Energy accounting.
- Upgrade calculations and limits.
- Item routing.
- Inventory insertion and extraction.
- Machine state transitions.
- Trader reward and replacement scheduling.
- Tree-detection boundaries.

For in-game testing, verify:
- Quarry mining, upgrades and save/reload.
- Frame collision and rail alignment.
- Power generation and depletion.
- Pipe branching, sorting and full chests.
- Smelting recipes and output handling.
- Trader reward, trades and replacement.
- Tree planting, growth, harvesting and replanting.
- Drone targeting and lifecycle.
- Rocket Boots activation and fall protection.
- GUI, tooltips, models, textures, sounds and error messages.
- World shutdown and reload.
- Multiplayer behavior and relevant logs.

If a real Minecraft test instance cannot be launched, explicitly list which tests remain manual and unverified. Do not mark those features VERIFIED.

## 16. FINAL RELEASE GATE

A release is complete only when:
- The full project builds successfully.
- The final JAR exists and was produced from the final source.
- All required registrations and resources load.
- All implemented features have a documented status.
- Critical item duplication, energy accounting and persistence cases have been tested.
- Available automated tests pass.
- The mod has been launched and tested in game where possible.
- Logs and known defects have been reviewed.
- Installation instructions and dependency versions are documented.

Deliver:
1. Complete source project.
2. Compatible Gradle wrapper and build configuration.
3. Final built JAR, if the build succeeds.
4. Exact installation instructions for Minecraft 26.2 Fabric.
5. Changelog.
6. Feature checklist.
7. Build and test report.
8. Known limitations and unverified items.

Do not provide an uncompiled ZIP as if it were a finished mod. If a dependency or environment problem prevents a verified build, disclose it and provide the most useful reproducible result without claiming completion.

## 17. REQUIRED EXECUTION ORDER

Follow this order exactly unless the audit reveals a documented dependency that requires a justified change:

1. Audit, baseline build and Git checkpoint.
2. Quarry mechanics, frame, rails, GUI, upgrade limits and creative tab.
3. Energy balance, pipe tiers and Sorting Terminal.
4. Auto-Smelter Upgrade and Super Smelter.
5. Trader introduction, starter kit, shop, guard and trading.
6. Tree Farm, harvesting drone and upgrades.
7. Security Upgrade and attack drones.
8. Rocket Boots and tier progression.
9. Shared visual polish, performance, compatibility and full regression testing.
10. Final build, artifact verification and release report.

At the end of each phase, stop and run its checkpoint. Never stack multiple untested phases on top of a failing build.

give an updated jar after each checkpoint where things are changed or added

## 18. HOW TO BEGIN

Start now with the project audit and baseline build.

Do not immediately implement every feature. First establish the current state of the code and create a reliable checkpoint. Then work through the roadmap in order.

After every checkpoint, report the actual results, commit the stable work where safe, and proceed to the next phase. Ask for approval only when a significant architectural decision, destructive operation or unavoidable trade-off requires it.

The ultimate goal is a polished, balanced, playable Solar Quarry release—not the largest possible feature list or codebase.
</system-reminder>