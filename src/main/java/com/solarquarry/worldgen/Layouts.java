package com.solarquarry.worldgen;

import com.solarquarry.construction.ConstructionBlocks;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.turret.TurretTier;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The buildings of the ten structures, in local coordinates (see {@link IndustrialPiece.Draw}). Surface buildings have
 * their floor at y = -1 and a foundation under it; underground ones are dug out of the rock around y = 0.
 * Variations (which rooms are wrecked, where turrets stand, which panels still work) come from the structure's own
 * random, so every copy looks a little different.
 */
public final class Layouts {
	static final BlockState YELLOW = Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
	static final BlockState BLACK = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
	static final BlockState GRAY = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
	static final BlockState LIGHT_GRAY = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	static final BlockState WHITE = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	static final BlockState FRAME = ConstructionBlocks.INDUSTRIAL_FRAME.defaultBlockState();
	static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
	static final BlockState HANGING = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

	private Layouts() {
	}

	static ResourceKey<LootTable> loot(String name) {
		return ModStructures.loot(name);
	}

	public static void build(Layout layout, IndustrialPiece.Draw d) {
		switch (layout) {
			case MINING_FACILITY -> miningFacility(d);
			case POWER_STATION -> powerStation(d);
			case RESEARCH_LABORATORY -> researchLab(d);
			case AUTOMATED_FACTORY -> factory(d);
			case TREE_FARM -> treeFarm(d);
			case SUPPLY_BUNKER -> bunker(d);
			case SUBSTATION -> substation(d);
			case PROTOTYPE_FACILITY -> prototype(d);
			case TRAIN_DEPOT -> trainDepot(d);
			case SOLAR_ARRAY -> solarArray(d);
		}
	}

	/** Surface yard: a floor at y = -1, foundation below, everything above cleared (trees, grass). */
	private static void yard(IndustrialPiece.Draw d, Layout l, BlockState floor, int clearHeight) {
		d.foundation(0, 0, l.sizeX - 1, l.sizeZ - 1, Blocks.COBBLESTONE.defaultBlockState());
		d.box(0, -1, 0, l.sizeX - 1, -1, l.sizeZ - 1, floor);
		d.air(0, 0, 0, l.sizeX - 1, clearHeight, l.sizeZ - 1);
	}

	/** Iron-bar fence with a gate gap on the north side. */
	private static void fence(IndustrialPiece.Draw d, int x0, int z0, int x1, int z1, int gateX) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge || (z == z0 && Math.abs(x - gateX) <= 1)) {
					continue;
				}

				boolean post = (x - x0) % 4 == 0 && (z == z0 || z == z1) || (z - z0) % 4 == 0 && (x == x0 || x == x1);
				if (post) {
					d.set(x, 0, z, GRAY);
					d.set(x, 1, z, GRAY);
				} else if (!d.chance(0.12F)) {
					d.set(x, 0, z, Blocks.IRON_BARS);
					d.set(x, 1, z, Blocks.IRON_BARS);
				}
			}
		}
	}

	/** A small concrete building with a door gap on its north wall, a flat roof with a hazard rim and a lantern. */
	private static void shed(IndustrialPiece.Draw d, int x0, int z0, int x1, int z1, int height, BlockState wall) {
		for (int y = 0; y < height; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					d.set(x, y, z, edge ? wall : Blocks.AIR.defaultBlockState());
				}
			}
		}

		int door = (x0 + x1) / 2;
		d.air(door, 0, z0, door, 1, z0);
		d.hazard(x0, height, z0, x1, z0);
		d.hazard(x0, height, z1, x1, z1);
		d.box(x0, height, z0 + 1, x1, height, z1 - 1, GRAY);
		d.set(door, height - 1, (z0 + z1) / 2, HANGING);
	}

	// ---- 1. Abandoned Mining Facility (underground) ----

	private static void miningFacility(IndustrialPiece.Draw d) {
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		// Central chamber.
		d.ruinedRoom(8, 0, 8, 22, 8, 22, brick, 0.15F);
		d.box(9, 0, 9, 21, 0, 21, Blocks.POLISHED_ANDESITE);
		d.hazard(9, 0, 9, 21, 9);
		// Ruined quarry frame: four posts, beams across, a drill shaft down into a pit.
		for (int[] c : new int[][] {{10, 10}, {20, 10}, {10, 20}, {20, 20}}) {
			d.box(c[0], 1, c[1], c[0], 6, c[1], FRAME);
		}

		d.box(10, 7, 10, 20, 7, 10, FRAME);
		d.box(10, 7, 20, 20, 7, 20, FRAME);
		if (d.chance(0.7F)) {
			d.box(15, 7, 10, 15, 7, 20, FRAME);
		}

		d.air(13, -3, 13, 17, 0, 17);
		d.box(15, -2, 15, 15, 6, 15, Blocks.IRON_BARS.defaultBlockState());
		d.set(15, -3, 15, Blocks.ANVIL);
		d.set(10, 1, 15, Blocks.CRAFTING_TABLE);
		d.set(10, 1, 16, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.EAST));
		d.set(20, 1, 15, Blocks.SMITHING_TABLE);
		for (int[] c : new int[][] {{12, 12}, {18, 12}, {12, 18}, {18, 18}}) {
			d.set(c[0], 7, c[1], HANGING);
		}

		// Tunnels to the four rooms.
		for (int i = 0; i <= 8; i++) {
			d.air(i, 1, 14, i, 3, 16);
			d.air(22 + i, 1, 14, 22 + i, 3, 16);
			d.air(14, 1, i, 16, 3, i);
			d.air(14, 1, 22 + i, 16, 3, 22 + i);
			d.set(i, 1, 15, Blocks.RAIL);
			if (i % 4 == 0) {
				d.box(i, 1, 14, i, 3, 14, Blocks.OAK_FENCE);
				d.box(i, 1, 16, i, 3, 16, Blocks.OAK_FENCE);
				d.box(i, 4, 14, i, 4, 16, Blocks.OAK_PLANKS);
				d.set(i, 3, 15, HANGING.setValue(LanternBlock.HANGING, true));
			}
		}

		// Storage room (north).
		d.ruinedRoom(10, 0, 0, 20, 5, 6, brick, 0.1F);
		d.air(14, 1, 6, 16, 3, 6);
		d.chest(11, 1, 1, Direction.SOUTH, loot("mining_facility"));
		d.chest(19, 1, 1, Direction.SOUTH, loot("mining_facility"));
		d.set(12, 1, 1, Blocks.BARREL);
		d.set(18, 1, 1, Blocks.BARREL);
		d.set(15, 4, 3, HANGING);
		// Workshop (south).
		d.ruinedRoom(10, 0, 24, 20, 5, 30, brick, 0.1F);
		d.air(14, 1, 24, 16, 3, 24);
		d.set(11, 1, 29, ModBlocks.FABRICATION_BENCH);
		d.set(12, 1, 29, Blocks.BLAST_FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
		d.chest(19, 1, 29, Direction.NORTH, loot("mining_facility"));
		d.set(15, 4, 27, HANGING);
		// Optional security: a wrecked turret, sometimes a working one with a little charge left.
		d.turret(9, 1, 21, TurretTier.BASIC, true, 0);
		if (d.chance(0.35F)) {
			d.turret(21, 1, 9, TurretTier.BASIC, false, 0.3F);
		}
	}

	// ---- 2. Abandoned Power Station (surface) ----

	private static void powerStation(IndustrialPiece.Draw d) {
		Layout l = Layout.POWER_STATION;
		yard(d, l, Blocks.GRAVEL.defaultBlockState(), 8);
		fence(d, 0, 0, l.sizeX - 1, l.sizeZ - 1, 6);
		// Solar arrays: mostly wrecked frames, a few panels still work.
		for (int x = 2; x <= 12; x += 2) {
			for (int z = 3; z <= 17; z += 3) {
				d.set(x, 0, z, FRAME);
				if (d.chance(0.18F)) {
					d.set(x, 1, z, ModBlocks.SOLAR_PANEL_BASIC);
				} else if (d.chance(0.5F)) {
					d.set(x, 1, z, Blocks.IRON_BARS);
				}
			}
		}

		// Exposed wiring from the arrays to the battery room.
		d.box(13, 0, 4, 15, 0, 4, ModBlocks.ENERGY_CABLE.defaultBlockState());
		// Battery room.
		shed(d, 16, 2, 22, 8, 4, GRAY);
		d.set(17, 0, 7, ModBlocks.BATTERY_BASIC);
		if (d.chance(0.3F)) {
			d.set(21, 0, 7, ModBlocks.BATTERY_ADVANCED);
		}

		d.chest(21, 0, 3, Direction.WEST, loot("power_station"));
		// Control building.
		shed(d, 16, 11, 24, 18, 4, LIGHT_GRAY);
		d.chest(17, 0, 17, Direction.NORTH, loot("power_station"));
		d.set(18, 0, 17, Blocks.LECTERN);
		d.box(20, 0, 17, 23, 0, 17, Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
		d.set(20, 4, 14, d.chance(0.5F) ? Blocks.AIR.defaultBlockState() : GRAY);
		d.turret(20, 5, 14, TurretTier.BASIC, d.chance(0.5F), 0.5F);
		// Transformer yard.
		for (int x = 2; x <= 10; x += 4) {
			d.box(x, 0, 19, x + 1, 1, 19, Blocks.IRON_BLOCK);
			d.set(x, 2, 19, Blocks.COPPER_BLOCK.waxed().unaffected());
		}
	}

	// ---- 3. Underground Research Laboratory ----

	private static void researchLab(IndustrialPiece.Draw d) {
		BlockState wall = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		// Reinforced shell and a corridor ring.
		d.room(0, 0, 0, 24, 6, 24, wall);
		d.box(1, 0, 1, 23, 0, 23, WHITE);
		d.box(5, 1, 5, 19, 5, 19, wall);
		d.air(6, 1, 6, 18, 5, 18);
		for (int x = 2; x <= 22; x += 5) {
			d.set(x, 5, 2, HANGING);
			d.set(x, 5, 22, HANGING);
		}

		// Labs on the inner ring, with observation windows.
		d.box(12, 1, 6, 12, 5, 18, wall);
		d.box(6, 1, 12, 18, 5, 12, wall);
		for (int z : new int[] {8, 15}) {
			d.box(5, 2, z, 5, 3, z + 1, Blocks.TINTED_GLASS);
			d.box(19, 2, z, 19, 3, z + 1, Blocks.TINTED_GLASS);
		}

		d.air(5, 1, 9, 5, 2, 9);
		d.air(19, 1, 16, 19, 2, 16);
		d.air(9, 1, 12, 9, 2, 12);
		d.air(15, 1, 12, 15, 2, 12);
		d.air(12, 1, 9, 12, 2, 9);
		// West lab: experiments.
		d.set(7, 1, 7, Blocks.BREWING_STAND);
		d.set(8, 1, 7, Blocks.CAULDRON);
		d.chest(10, 1, 7, Direction.SOUTH, loot("research_laboratory"));
		// East lab: an experiment that got loose.
		d.spawner(16, 1, 9, EntityTypes.ZOMBIE);
		d.set(17, 1, 7, Blocks.ENCHANTING_TABLE);
		// Security area.
		d.turret(8, 1, 16, TurretTier.ADVANCED, false, 0.4F);
		d.turret(10, 1, 17, TurretTier.BASIC, true, 0);
		// Central vault behind bars.
		d.room(13, 0, 13, 18, 5, 18, Blocks.OBSIDIAN.defaultBlockState());
		d.box(13, 1, 15, 13, 2, 16, Blocks.IRON_BARS);
		d.chest(16, 1, 16, Direction.WEST, loot("research_laboratory"));
		d.chest(16, 1, 15, Direction.WEST, loot("research_laboratory"));
		d.set(15, 4, 15, HANGING);
		for (int x = 7; x <= 17; x += 5) {
			d.set(x, 5, 9, HANGING);
		}
	}

	// ---- 4. Abandoned Automated Factory (surface) ----

	private static void factory(IndustrialPiece.Draw d) {
		Layout l = Layout.AUTOMATED_FACTORY;
		yard(d, l, Blocks.SMOOTH_STONE.defaultBlockState(), 9);
		// Warehouse shell with a partly collapsed roof.
		for (int y = 0; y <= 6; y++) {
			for (int x = 0; x < l.sizeX; x++) {
				for (int z = 0; z < l.sizeZ; z++) {
					boolean edge = x == 0 || x == l.sizeX - 1 || z == 0 || z == l.sizeZ - 1;
					if (edge && !d.chance(y > 3 ? 0.15F : 0.04F)) {
						d.set(x, y, z, y == 0 ? GRAY : y == 5 ? YELLOW : LIGHT_GRAY);
					}
				}
			}
		}

		for (int x = 1; x < l.sizeX - 1; x++) {
			for (int z = 1; z < l.sizeZ - 1; z++) {
				if (!d.chance(0.2F)) {
					d.set(x, 7, z, Blocks.SMOOTH_STONE_SLAB);
				}
			}
		}

		// Loading bays.
		d.air(4, 0, 0, 7, 3, 0);
		d.air(21, 0, 0, 24, 3, 0);
		// Assembly line: pipes past furnaces and smelters.
		for (int x = 3; x <= 25; x++) {
			d.set(x, 0, 6, ModBlocks.ITEM_PIPE);
			if (x % 4 == 1) {
				d.set(x, 0, 7, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.SOUTH));
				d.set(x, 0, 5, Blocks.BLAST_FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
			}
		}

		// Sorting room.
		d.box(3, 0, 10, 12, 0, 10, ModBlocks.REINFORCED_PIPE.defaultBlockState());
		for (int x = 3; x <= 12; x += 3) {
			d.chest(x, 0, 11, Direction.SOUTH, loot("automated_factory"));
		}

		d.set(13, 0, 10, ModBlocks.FILTER_PIPE);
		// Warehouse racks.
		for (int x = 16; x <= 25; x += 3) {
			d.box(x, 0, 13, x, 2, 16, Blocks.SPRUCE_PLANKS);
			d.set(x + 1, 0, 14, ModBlocks.CRATE_BASIC);
			d.set(x + 1, 1, 14, Blocks.BARREL);
		}

		d.chest(26, 0, 16, Direction.WEST, loot("automated_factory"));
		if (d.chance(0.25F)) {
			d.set(14, 0, 15, ModBlocks.SUPER_SMELTER);
		}

		for (int x = 4; x <= 24; x += 5) {
			d.set(x, 6, 9, HANGING);
		}
	}

	// ---- 5. Abandoned Tree Farm (surface) ----

	private static void treeFarm(IndustrialPiece.Draw d) {
		Layout l = Layout.TREE_FARM;
		d.foundation(0, 0, l.sizeX - 1, l.sizeZ - 1, Blocks.DIRT.defaultBlockState());
		d.box(0, -1, 0, l.sizeX - 1, -1, l.sizeZ - 1, Blocks.GRASS_BLOCK);
		d.air(0, 0, 0, l.sizeX - 1, 3, l.sizeZ - 1);
		// Overgrown planting grid.
		for (int x = 2; x <= 14; x += 3) {
			for (int z = 2; z <= 14; z += 3) {
				d.set(x, -1, z, Blocks.PODZOL);
				if (d.chance(0.45F)) {
					d.set(x, 0, z, d.chance(0.5F) ? Blocks.OAK_SAPLING : Blocks.BIRCH_SAPLING);
				} else if (d.chance(0.5F)) {
					d.box(x - 1, 0, z - 1, x + 1, 1, z + 1, Blocks.OAK_LEAVES.defaultBlockState()
						.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
					d.set(x, 0, z, Blocks.OAK_LOG);
				}
			}
		}

		// Broken harvesting mast.
		d.box(8, 0, 17, 8, d.chance(0.5F) ? 4 : 7, 17, FRAME);
		d.box(8, 6, 17, 12, 6, 17, d.chance(0.5F) ? FRAME : Blocks.AIR.defaultBlockState());
		// Lumber shed.
		for (int y = 0; y <= 3; y++) {
			for (int x = 15; x <= 20; x++) {
				for (int z = 14; z <= 20; z++) {
					boolean edge = x == 15 || x == 20 || z == 14 || z == 20;
					d.set(x, y, z, edge ? (y == 3 ? YELLOW : Blocks.SPRUCE_PLANKS.defaultBlockState()) : Blocks.AIR.defaultBlockState());
				}
			}
		}

		d.box(15, 4, 14, 20, 4, 20, Blocks.SPRUCE_SLAB.defaultBlockState());
		d.air(17, 0, 14, 18, 1, 14);
		d.chest(19, 0, 19, Direction.WEST, loot("tree_farm"));
		d.chest(16, 0, 19, Direction.EAST, loot("tree_farm"));
		d.box(16, 0, 15, 16, 1, 17, Blocks.OAK_LOG);
		d.set(19, 0, 15, Blocks.STONECUTTER);
		d.set(18, 3, 17, HANGING);
	}

	// ---- 6. Emergency Supply Bunker (buried) ----

	private static void bunker(IndustrialPiece.Draw d) {
		Layout l = Layout.SUPPLY_BUNKER;
		BlockState concrete = GRAY;
		d.room(0, 0, 0, 14, 5, 10, concrete);
		d.box(1, 0, 1, 13, 0, 9, Blocks.POLISHED_ANDESITE);
		// Sleeping quarters (west).
		d.box(5, 1, 1, 5, 4, 9, concrete);
		d.air(5, 1, 5, 5, 2, 5);
		BlockState bed = Blocks.BED.pick(DyeColor.GRAY).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		for (int z : new int[] {2, 4, 8}) {
			d.set(1, 1, z, bed.setValue(BedBlock.PART, BedPart.FOOT));
			d.set(2, 1, z, bed.setValue(BedBlock.PART, BedPart.HEAD));
		}

		d.barrel(4, 1, 1, loot("supply_bunker"));
		// Armory and supply lockers (east).
		d.box(10, 1, 1, 10, 4, 9, concrete);
		d.air(10, 1, 5, 10, 2, 5);
		d.chest(13, 1, 2, Direction.WEST, loot("supply_bunker"));
		d.chest(13, 1, 8, Direction.WEST, loot("supply_bunker"));
		for (int z = 3; z <= 7; z++) {
			d.barrel(13, 1, z, loot("supply_bunker"));
		}

		// Maintenance corner and emergency lights (lamps on hidden redstone).
		d.set(7, 1, 9, Blocks.ANVIL);
		d.set(8, 1, 9, Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH));
		for (int x : new int[] {3, 7, 12}) {
			d.set(x, 5, 5, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
			d.set(x, 6, 5, Blocks.REDSTONE_BLOCK);
		}

		// Access shaft with a ladder up to a hatch at the surface.
		int top = l.depth - 1; // the top ground block; the hatch sits on the surface above it
		for (int y = 1; y <= top; y++) {
			d.box(6, y, 0, 8, y, 2, concrete);
			d.air(7, y, 1, 7, y, 1);
			d.set(7, y, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}

		d.air(7, 1, 1, 7, 2, 2);
		d.set(7, 1, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		d.set(7, top + 1, 1, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
		d.hazard(6, top, 0, 8, 0);
	}

	// ---- 7. High-Voltage Substation (surface) ----

	private static void substation(IndustrialPiece.Draw d) {
		Layout l = Layout.SUBSTATION;
		yard(d, l, Blocks.GRAVEL.defaultBlockState(), 9);
		fence(d, 0, 0, l.sizeX - 1, l.sizeZ - 1, 9);
		// Transformers.
		for (int x : new int[] {3, 8, 13}) {
			d.box(x, 0, 4, x + 2, 2, 6, Blocks.IRON_BLOCK);
			d.set(x + 1, 3, 5, Blocks.COPPER_BLOCK.waxed().unaffected());
			d.set(x + 1, 3, 4, Blocks.IRON_CHAIN);
		}

		// Pylons with lines between them.
		for (int x : new int[] {2, 16}) {
			d.box(x, 0, 12, x, 7, 12, Blocks.IRON_BARS);
			d.box(x - 1, 7, 12, x + 1, 7, 12, GRAY);
		}

		for (int x = 3; x <= 15; x++) {
			if (!d.chance(0.15F)) {
				d.set(x, 7, 12, Blocks.IRON_CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.ChainBlock.AXIS, Direction.Axis.X));
			}
		}

		// Control shed and battery storage.
		shed(d, 11, 13, 16, 17, 4, LIGHT_GRAY);
		d.chest(15, 0, 16, Direction.WEST, loot("substation"));
		d.box(3, 0, 15, 6, 0, 15, ModBlocks.ENERGY_CABLE.defaultBlockState());
		d.set(3, 0, 16, ModBlocks.BATTERY_BASIC);
		if (d.chance(0.4F)) {
			d.set(5, 0, 16, ModBlocks.BATTERY_BASIC);
		}

		d.hazard(2, 0, 14, 7, 14);
	}

	// ---- 8. Prototype Technology Facility (deep underground) ----

	private static void prototype(IndustrialPiece.Draw d) {
		BlockState wall = Blocks.DEEPSLATE_TILES.defaultBlockState();
		d.room(0, 0, 0, 30, 8, 30, wall);
		d.box(1, 0, 1, 29, 0, 29, Blocks.POLISHED_DEEPSLATE);
		d.hazard(1, 0, 14, 29, 16);
		// Prototype labs in the corners.
		for (int[] c : new int[][] {{1, 1}, {21, 1}, {1, 21}, {21, 21}}) {
			d.room(c[0], 0, c[1], c[0] + 8, 6, c[1] + 8, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			d.box(c[0], 2, c[1] + 3, c[0], 3, c[1] + 5, Blocks.TINTED_GLASS);
			d.box(c[0] + 8, 2, c[1] + 3, c[0] + 8, 3, c[1] + 5, Blocks.TINTED_GLASS);
			d.air(c[0] + 4, 1, c[1] == 1 ? c[1] + 8 : c[1], c[0] + 4, 2, c[1] == 1 ? c[1] + 8 : c[1]);
			d.set(c[0] + 4, 5, c[1] + 4, HANGING);
			d.chest(c[0] + 1, 1, c[1] + 4, Direction.EAST, loot("prototype_lab"));
		}

		d.spawner(5, 1, 25, EntityTypes.SKELETON);
		d.spawner(25, 1, 5, EntityTypes.ZOMBIE);
		// Central vault with security doors (bars) and an obsidian shell.
		d.room(11, 0, 11, 19, 7, 19, Blocks.OBSIDIAN.defaultBlockState());
		d.box(15, 1, 11, 15, 3, 11, Blocks.IRON_BARS);
		d.box(14, 1, 11, 14, 3, 11, Blocks.IRON_BARS);
		d.box(16, 1, 11, 16, 3, 11, Blocks.IRON_BARS);
		d.chest(14, 1, 17, Direction.NORTH, loot("prototype_facility"));
		d.chest(16, 1, 17, Direction.NORTH, loot("prototype_facility"));
		d.set(15, 6, 15, HANGING);
		d.hazard(12, 0, 12, 18, 12);
		// Defences.
		d.turret(11, 1, 9, TurretTier.ADVANCED, false, 0.6F);
		d.turret(19, 1, 9, TurretTier.ADVANCED, false, 0.6F);
		d.turret(15, 1, 21, TurretTier.ELITE, true, 0);
		for (int x = 4; x <= 26; x += 6) {
			d.set(x, 7, 15, HANGING);
		}
	}

	// ---- 9. Abandoned Industrial Train Depot (surface) ----

	private static void trainDepot(IndustrialPiece.Draw d) {
		Layout l = Layout.TRAIN_DEPOT;
		yard(d, l, Blocks.GRAVEL.defaultBlockState(), 7);
		// Three sidings.
		for (int z : new int[] {2, 5, 8}) {
			for (int x = 0; x < l.sizeX; x++) {
				if (!d.chance(0.06F)) {
					d.set(x, 0, z, Blocks.RAIL);
				}
			}
		}

		// Freight cars.
		d.freightCar(6, 0, 2, loot("train_depot"));
		if (d.chance(0.6F)) {
			d.freightCar(18, 0, 5, loot("train_depot"));
		}

		// Loading platform.
		d.box(2, 0, 10, 30, 0, 11, Blocks.STONE_SLAB.defaultBlockState());
		d.hazard(2, 0, 10, 30, 10);
		// Cargo warehouse and maintenance shed.
		shed(d, 4, 12, 14, 16, 5, Blocks.BRICKS.defaultBlockState());
		d.chest(5, 0, 15, Direction.EAST, loot("train_depot"));
		d.chest(13, 0, 15, Direction.WEST, loot("train_depot"));
		d.set(9, 0, 15, ModBlocks.CRATE_BASIC);
		shed(d, 20, 12, 27, 16, 4, GRAY);
		d.set(21, 0, 15, Blocks.ANVIL);
		d.set(26, 0, 15, Blocks.SMITHING_TABLE);
		d.box(28, 0, 12, 30, 1, 14, Blocks.SPRUCE_PLANKS);
	}

	// ---- 10. Ruined Solar Array (surface) ----

	private static void solarArray(IndustrialPiece.Draw d) {
		Layout l = Layout.SOLAR_ARRAY;
		yard(d, l, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 5);
		// Rows of panels on frames; most broken.
		for (int x = 1; x <= 23; x += 2) {
			for (int z = 1; z <= 15; z += 4) {
				d.set(x, 0, z, FRAME);
				if (d.chance(0.12F)) {
					d.set(x, 1, z, d.chance(0.15F) ? ModBlocks.SOLAR_PANEL_ADVANCED : ModBlocks.SOLAR_PANEL_BASIC);
				} else if (d.chance(0.4F)) {
					d.set(x, 1, z, Blocks.GLASS_PANE);
				}
			}
		}

		// Exposed cables to the battery enclosure.
		for (int x = 2; x <= 14; x++) {
			if (!d.chance(0.2F)) {
				d.set(x, 0, 18, ModBlocks.ENERGY_CABLE);
			}
		}

		for (int x = 14; x <= 18; x++) {
			for (int z = 17; z <= 21; z++) {
				boolean edge = x == 14 || x == 18 || z == 17 || z == 21;
				if (edge) {
					d.box(x, 0, z, x, 1, z, Blocks.IRON_BARS);
				}
			}
		}

		d.set(16, 0, 19, ModBlocks.BATTERY_BASIC);
		// Maintenance shed.
		shed(d, 19, 18, 24, 23, 3, LIGHT_GRAY);
		d.chest(23, 0, 22, Direction.WEST, loot("solar_array"));
		if (d.chance(0.5F)) {
			d.chest(20, 0, 22, Direction.EAST, loot("solar_array"));
		}
	}
}
