package com.solarquarry.trader;

import com.solarquarry.construction.ConstructionBlocks;
import com.solarquarry.construction.ConstructionHost;
import com.solarquarry.construction.Placement;
import com.solarquarry.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The Mechanic's house: a 13 x 11 workshop-home in dark steel and concrete with a hazard-striped band, built by the
 * Mechanic block by block through the shared construction framework.
 *
 * <p>Rooms (door on the north side, {@code floor} is the centre of the floor):
 * <ul>
 * <li>trading hall (west front) with a counter the Mechanic stands behind,</li>
 * <li>living room (west back) with a bed, bookshelf and barrel,</li>
 * <li>workshop (east) with the Fabrication Bench (his workstation), an anvil, smithing table, blast furnace,
 * grindstone, stonecutter and the supply chest.</li>
 * </ul>
 * The plan is built in stages: clearing plants and leaves, foundation (bottom up, so the house never floats), floor,
 * walls layer by layer, roof, furniture, outside lights and the sign.
 */
public final class MechanicHouse {
	public static final int HALF_X = 6;
	public static final int HALF_Z = 5;
	/** Walls are 4 high; the roof sits at +5. */
	public static final int ROOF = 5;
	/** Outside the north wall: the door step and two lamp posts (z = -6). */
	public static final int FRONT = -6;
	public static final int MAX_FOUNDATION = 10;

	private MechanicHouse() {
	}

	// ---- named spots (relative to the floor centre) ----

	public static BlockPos traderSpot(BlockPos floor) {
		return floor.offset(-3, 1, 0);
	}

	public static BlockPos guardSpot(BlockPos floor) {
		return floor.offset(-1, 1, -3);
	}

	public static BlockPos benchPos(BlockPos floor) {
		return floor.offset(3, 1, 4);
	}

	public static BlockPos chestPos(BlockPos floor) {
		return floor.offset(2, 1, 4);
	}

	public static BlockPos signPos(BlockPos floor) {
		return floor.offset(-3, 3, FRONT);
	}

	public static BlockPos doorPos(BlockPos floor) {
		return floor.offset(-3, 1, -HALF_Z);
	}

	/** Whether a world position lies in the house's footprint (including the front step), at any height. */
	public static boolean inFootprint(BlockPos floor, BlockPos pos) {
		int dx = pos.getX() - floor.getX();
		int dz = pos.getZ() - floor.getZ();
		return Math.abs(dx) <= HALF_X && dz >= FRONT && dz <= HALF_Z;
	}

	// ---- blueprint ----

	/**
	 * Resolves the house at {@code floor} into placements, in building order. Air placements clear wild plants and
	 * natural leaves from the rooms (the construction framework skips them where it is already air).
	 */
	public static List<Placement> blueprint(ServerLevel level, BlockPos floor) {
		Map<BlockPos, BlockState> house = new LinkedHashMap<>();
		BlockState concrete = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
		BlockState darkConcrete = Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
		BlockState yellow = Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
		BlockState black = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
		BlockState pillar = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState glass = ConstructionBlocks.INDUSTRIAL_GLASS.defaultBlockState();

		// Floor: smooth stone in the west rooms, polished andesite in the workshop.
		for (int dx = -HALF_X; dx <= HALF_X; dx++) {
			for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
				boolean edge = Math.abs(dx) == HALF_X || Math.abs(dz) == HALF_Z;
				BlockState f = edge ? darkConcrete : dx >= 2 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
				house.put(floor.offset(dx, 0, dz), f);
			}
		}

		// Front step and lamp posts.
		house.put(floor.offset(-3, 0, FRONT), Blocks.STONE_BRICKS.defaultBlockState());
		house.put(floor.offset(-5, 0, FRONT), pillar);
		house.put(floor.offset(-1, 0, FRONT), pillar);

		// Outer walls, layer by layer; hazard stripes on the top band.
		for (int dy = 1; dy <= 4; dy++) {
			for (int dx = -HALF_X; dx <= HALF_X; dx++) {
				for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
					boolean edgeX = Math.abs(dx) == HALF_X;
					boolean edgeZ = Math.abs(dz) == HALF_Z;
					if (!edgeX && !edgeZ) {
						continue;
					}

					BlockState wall;
					if ((edgeX && edgeZ) || (edgeZ && (dx == 0 || dx == 1)) || (edgeX && dz == 0)) {
						wall = pillar;
					} else if (dy == 4) {
						wall = Math.floorMod(dx + dz, 2) == 0 ? yellow : black;
					} else if (dy == 1) {
						wall = darkConcrete;
					} else if (isWindow(dx, dz)) {
						wall = glass;
					} else {
						wall = concrete;
					}

					house.put(floor.offset(dx, dy, dz), wall);
				}
			}
		}

		// Inner walls: workshop (x = 1) with a doorway at z = -3; living room (z = 1) with a doorway at x = -2.
		for (int dy = 1; dy <= 4; dy++) {
			for (int dz = -HALF_Z + 1; dz <= HALF_Z - 1; dz++) {
				if (!(dz == -3 && dy <= 2)) {
					house.put(floor.offset(1, dy, dz), dy == 4 ? darkConcrete : concrete);
				}
			}

			for (int dx = -HALF_X + 1; dx <= 0; dx++) {
				if (!(dx == -2 && dy <= 2)) {
					house.put(floor.offset(dx, dy, 1), dy == 4 ? darkConcrete : concrete);
				}
			}
		}

		// Door (lower half, then upper half).
		BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		BlockPos doorPos = doorPos(floor);
		house.put(doorPos, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		house.put(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Roof: slabs inside a dark rim, a vent and an antenna.
		for (int dx = -HALF_X; dx <= HALF_X; dx++) {
			for (int dz = -HALF_Z; dz <= HALF_Z; dz++) {
				boolean edge = Math.abs(dx) == HALF_X || Math.abs(dz) == HALF_Z;
				house.put(floor.offset(dx, ROOF, dz), edge ? darkConcrete : Blocks.SMOOTH_STONE.defaultBlockState());
			}
		}

		house.put(floor.offset(4, ROOF + 1, 3), Blocks.IRON_BARS.defaultBlockState());
		house.put(floor.offset(3, ROOF + 1, 3), Blocks.IRON_BARS.defaultBlockState());
		house.put(floor.offset(5, ROOF + 1, 4), Blocks.IRON_CHAIN.defaultBlockState());

		// Trading hall: counter (gap at x = 0), lantern in the corner.
		for (int dx = -5; dx <= -1; dx++) {
			house.put(floor.offset(dx, 1, -1), Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
		}

		house.put(floor.offset(-5, 1, -4), Blocks.LANTERN.defaultBlockState());
		house.put(floor.offset(0, 1, -4), Blocks.BARREL.defaultBlockState());

		// Living room.
		BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		house.put(floor.offset(-5, 1, 3), bed.setValue(BedBlock.PART, BedPart.FOOT));
		house.put(floor.offset(-5, 1, 4), bed.setValue(BedBlock.PART, BedPart.HEAD));
		house.put(floor.offset(-2, 1, 4), Blocks.BOOKSHELF.defaultBlockState());
		house.put(floor.offset(-1, 1, 4), Blocks.BARREL.defaultBlockState());
		house.put(floor.offset(-3, 1, 3), Blocks.CARPET.pick(DyeColor.GRAY).defaultBlockState());
		house.put(floor.offset(0, 1, 4), Blocks.CRAFTING_TABLE.defaultBlockState());

		// Workshop: the Fabrication Bench is his workstation.
		house.put(benchPos(floor), ModBlocks.FABRICATION_BENCH.defaultBlockState());
		house.put(chestPos(floor), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		house.put(floor.offset(5, 1, 3), Blocks.ANVIL.defaultBlockState());
		house.put(floor.offset(5, 1, 1), Blocks.SMITHING_TABLE.defaultBlockState());
		house.put(floor.offset(5, 1, -1), Blocks.BLAST_FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
		house.put(floor.offset(5, 1, -3), Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR).setValue(GrindstoneBlock.FACING, Direction.WEST));
		house.put(floor.offset(4, 1, -4), Blocks.STONECUTTER.defaultBlockState());

		// Hanging lights in every room.
		BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
		for (int[] c : new int[][] {{-3, -3}, {-3, 3}, {3, -2}, {3, 2}}) {
			house.put(floor.offset(c[0], 4, c[1]), hanging);
		}

		// Outside: lamps on the posts and the sign over the door.
		house.put(floor.offset(-5, 1, FRONT), Blocks.LANTERN.defaultBlockState());
		house.put(floor.offset(-1, 1, FRONT), Blocks.LANTERN.defaultBlockState());
		house.put(signPos(floor), Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));

		List<Placement> plan = new ArrayList<>();
		// 1. Clear the rooms (wild plants, natural leaves) so nothing grows inside.
		for (int dx = -HALF_X + 1; dx <= HALF_X - 1; dx++) {
			for (int dz = -HALF_Z + 1; dz <= HALF_Z - 1; dz++) {
				for (int dy = 1; dy <= 4; dy++) {
					BlockPos pos = floor.offset(dx, dy, dz);
					if (!house.containsKey(pos)) {
						plan.add(new Placement(pos, Blocks.AIR.defaultBlockState()));
					}
				}
			}
		}

		// 2. Foundation under every floor-level block, from the bottom up.
		List<Placement> foundation = new ArrayList<>();
		for (BlockPos pos : house.keySet()) {
			if (pos.getY() != floor.getY()) {
				continue;
			}

			for (int y = 1; y <= MAX_FOUNDATION; y++) {
				BlockPos below = pos.below(y);
				if (!level.isLoaded(below) || isSolidGround(level.getBlockState(below))) {
					break;
				}

				foundation.add(new Placement(below, Blocks.STONE_BRICKS.defaultBlockState()));
			}
		}

		foundation.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()));
		plan.addAll(foundation);

		// 3. The house itself in the order above (floor, walls bottom-up, door, roof, furniture, lights, sign).
		house.forEach((pos, state) -> plan.add(new Placement(pos, state)));
		return plan;
	}

	private static boolean isWindow(int dx, int dz) {
		if (Math.abs(dz) == HALF_Z) {
			return (dx >= 2 && dx <= 4) || (dz == HALF_Z && dx >= -4 && dx <= -2);
		}

		return dz == -3 || dz == -2 || dz == 2 || dz == 3;
	}

	/** Ground the foundation may stand on: anything solid except leaves and plants. */
	static boolean isSolidGround(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !(state.getBlock() instanceof LeavesBlock)
			&& !ConstructionHost.isClearable(state);
	}

	/**
	 * What the house may build over: air, wild plants and snow (as every machine), plus natural (non-placed) leaves.
	 * Logs, planks, player-placed leaves and every other block stop construction until they are removed.
	 */
	public static boolean canReplace(BlockState existing) {
		if (ConstructionHost.isClearable(existing)) {
			return true;
		}

		return existing.getBlock() instanceof LeavesBlock && !existing.getValue(LeavesBlock.PERSISTENT) && existing.is(BlockTags.LEAVES);
	}

	// ---- site selection ----

	public static final int STRICT_RADIUS = 64;
	public static final int RELAXED_RADIUS = 96;

	/**
	 * Picks the floor centre near {@code near}: first dry, flat (2 blocks) natural ground with nothing but plants
	 * above; then ground up to 4 blocks uneven with natural tree leaves allowed above (the Mechanic clears them);
	 * otherwise next to the spawn at the highest ground level. Deterministic for a given world.
	 */
	public static BlockPos findSite(ServerLevel level, BlockPos near) {
		for (boolean strict : new boolean[] {true, false}) {
			int max = strict ? STRICT_RADIUS : RELAXED_RADIUS;
			for (int r = 8; r <= max; r += 4) {
				for (int i = 0; i < 16; i++) {
					double angle = Math.PI * 2 * i / 16;
					int x = near.getX() + (int) Math.round(Math.cos(angle) * r);
					int z = near.getZ() + (int) Math.round(Math.sin(angle) * r);
					BlockPos site = siteAt(level, x, z, strict);
					if (site != null) {
						return site;
					}
				}
			}
		}

		int x = near.getX() + 8;
		int z = near.getZ() + 8;
		int top = level.getSeaLevel();
		for (int dx = -HALF_X; dx <= HALF_X; dx++) {
			for (int dz = FRONT; dz <= HALF_Z; dz++) {
				top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz));
			}
		}

		return new BlockPos(x, Math.min(top, level.getMaxY() - ROOF - 3), z);
	}

	/** Checks one candidate; returns the floor centre or null. */
	public static @Nullable BlockPos siteAt(ServerLevel level, int cx, int cz, boolean strict) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = -HALF_X; dx <= HALF_X; dx++) {
			for (int dz = FRONT; dz <= HALF_Z; dz++) {
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
				BlockPos ground = new BlockPos(cx + dx, top - 1, cz + dz);
				BlockState state = level.getBlockState(ground);
				if (!state.getFluidState().isEmpty() || !isNaturalGround(state) || level.getBlockEntity(ground) != null) {
					return null;
				}

				min = Math.min(min, top);
				max = Math.max(max, top);
			}
		}

		if (max - min > (strict ? 2 : 4) || min <= level.getMinY() + 1 || max + ROOF + 3 >= level.getMaxY()) {
			return null;
		}

		for (int dx = -HALF_X; dx <= HALF_X; dx++) {
			for (int dz = FRONT; dz <= HALF_Z; dz++) {
				for (int y = max; y <= max + ROOF + 2; y++) {
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					BlockState state = level.getBlockState(pos);
					boolean ok = strict ? ConstructionHost.isClearable(state) : canReplace(state);
					if (!ok || level.getBlockEntity(pos) != null) {
						return null;
					}
				}
			}
		}

		return new BlockPos(cx, max, cz);
	}

	private static boolean isNaturalGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY);
	}

	/** Places {@code state} directly (tests only). */
	static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}
}
