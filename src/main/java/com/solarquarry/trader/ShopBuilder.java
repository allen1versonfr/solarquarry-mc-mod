package com.solarquarry.trader;

import com.solarquarry.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Builds the trader's shop: a 9 x 9 steel-and-concrete hut with windows, lanterns, a sign, a Fabrication Bench and a
 * supply chest. The site is chosen near the world spawn on dry, fairly flat natural ground with nothing built on it;
 * if no such place exists within {@link #SEARCH_RADIUS} blocks, it is built on a levelled platform at the spawn.
 */
public final class ShopBuilder {
	public static final int HALF = 4;
	public static final int HEIGHT = 5;
	public static final int SEARCH_RADIUS = 48;

	private ShopBuilder() {
	}

	/** Finds a site: returns the floor centre (the block the floor is placed in), or null if none qualifies. */
	public static @Nullable BlockPos findSite(ServerLevel level, BlockPos near) {
		for (int r = 6; r <= SEARCH_RADIUS; r += 3) {
			for (int i = 0; i < 16; i++) {
				double angle = Math.PI * 2 * i / 16;
				int x = near.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = near.getZ() + (int) Math.round(Math.sin(angle) * r);
				BlockPos site = siteAt(level, x, z);
				if (site != null) {
					return site;
				}
			}
		}

		return null;
	}

	/** Checks one candidate column: flat within 2 blocks, solid natural ground, no liquid, nothing built, nothing above. */
	public static @Nullable BlockPos siteAt(ServerLevel level, int cx, int cz) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
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

		if (max - min > 2 || min <= level.getMinY() + 1 || max + HEIGHT + 2 >= level.getMaxY()) {
			return null;
		}

		// Nothing in the way above the floor (trees, buildings, liquid).
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int y = max; y <= max + HEIGHT + 1; y++) {
					BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && !(state.canBeReplaced() && state.getFluidState().isEmpty()) || level.getBlockEntity(pos) != null) {
						return null;
					}
				}
			}
		}

		return new BlockPos(cx, max, cz);
	}

	private static boolean isNaturalGround(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.CLAY);
	}

	/** Builds the shop with its floor at {@code floor} (the door faces north). */
	public static void build(ServerLevel level, BlockPos floor) {
		int y0 = floor.getY();
		// Foundation down to the ground, so the hut never floats.
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int y = y0 - 1; y >= y0 - 8; y--) {
					BlockPos pos = new BlockPos(floor.getX() + dx, y, floor.getZ() + dz);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
						break;
					}

					set(level, pos, Blocks.STONE_BRICKS.defaultBlockState());
				}

				set(level, floor.offset(dx, 0, dz), Blocks.SMOOTH_STONE.defaultBlockState());
				for (int dy = 1; dy <= HEIGHT + 1; dy++) {
					set(level, floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}

		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				boolean edgeX = Math.abs(dx) == HALF;
				boolean edgeZ = Math.abs(dz) == HALF;
				for (int dy = 1; dy <= HEIGHT - 1; dy++) {
					if (edgeX && edgeZ) {
						set(level, floor.offset(dx, dy, dz), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.GRAY).defaultBlockState());
					} else if (edgeX || edgeZ) {
						boolean window = dy == 2 && (Math.abs(dx) == 2 || Math.abs(dz) == 2);
						set(level, floor.offset(dx, dy, dz), window ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState());
					}
				}

				// Flat roof with a hazard-yellow trim.
				set(level, floor.offset(dx, HEIGHT, dz), edgeX || edgeZ ? Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState() : Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
			}
		}

		// Door on the north wall (z - HALF), centred.
		BlockPos door = floor.offset(0, 1, -HALF);
		BlockState doorState = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		set(level, door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		set(level, door.north().below(), Blocks.SMOOTH_STONE.defaultBlockState());

		// Sign over the door.
		BlockPos signPos = door.above(2).north();
		set(level, signPos, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
		if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			SignText text = new SignText()
				.setMessage(0, Component.translatable("solarquarry.shop.sign.0"))
				.setMessage(1, Component.translatable("solarquarry.shop.sign.1"))
				.setMessage(2, Component.translatable("solarquarry.shop.sign.2"));
			sign.setText(text, true);
			sign.setWaxed(true);
		}

		// Lights: lanterns inside the corners and on both sides of the door.
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			set(level, floor.offset(c[0], 1, c[1]), Blocks.LANTERN.defaultBlockState());
		}

		set(level, door.offset(-1, 1, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		set(level, door.offset(1, 1, -1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		set(level, door.offset(-1, 0, -1), Blocks.STONE_BRICKS.defaultBlockState());
		set(level, door.offset(1, 0, -1), Blocks.STONE_BRICKS.defaultBlockState());

		// Counter, bench and supply chest along the back wall.
		for (int dx = -2; dx <= 2; dx++) {
			if (dx != 0) {
				set(level, floor.offset(dx, 1, 2), Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState());
			}
		}

		set(level, floor.offset(0, 1, 3), ModBlocks.FABRICATION_BENCH.defaultBlockState());
		BlockPos chest = floor.offset(-3, 1, 0);
		set(level, chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity supplies) {
			supplies.setItem(0, new ItemStack(Items.TORCH, 32));
			supplies.setItem(1, new ItemStack(Items.BREAD, 8));
			supplies.setItem(2, new ItemStack(Items.COAL, 16));
			supplies.setItem(3, new ItemStack(Items.IRON_PICKAXE));
			supplies.setItem(4, new ItemStack(Items.OAK_LOG, 16));
		}
	}

	/** Where the trader stands (behind the counter) and where the guard waits (by the door). */
	public static BlockPos traderSpot(BlockPos floor) {
		return floor.offset(0, 1, 1);
	}

	public static BlockPos guardSpot(BlockPos floor) {
		return floor.offset(2, 1, -2);
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}
}
