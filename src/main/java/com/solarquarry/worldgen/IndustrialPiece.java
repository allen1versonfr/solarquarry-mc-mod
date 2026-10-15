package com.solarquarry.worldgen;

import com.solarquarry.turret.LaserTurretBlockEntity;
import com.solarquarry.turret.TurretTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One whole Solar Quarry structure. The building is drawn by {@link Layouts} in local coordinates (x, y, z from the
 * north-west bottom corner); every block is placed only inside the chunk being generated, and all random choices come
 * from a random seeded by the structure's position, so each chunk draws the same building and nothing is duplicated
 * when neighbouring chunks generate later. Loot is set as a loot table on the containers (rolled when first opened),
 * turrets are placed wrecked or with a finite charge, never with a power supply.
 */
public class IndustrialPiece extends StructurePiece {
	private final Layout layout;
	private final BlockPos origin;

	public IndustrialPiece(Layout layout, BlockPos origin) {
		super(ModStructures.INDUSTRIAL_PIECE, 0, new BoundingBox(origin.getX(), origin.getY() - (layout.underground() ? 0 : 6), origin.getZ(),
			origin.getX() + layout.sizeX - 1, origin.getY() + layout.sizeY - 1, origin.getZ() + layout.sizeZ - 1));
		this.layout = layout;
		this.origin = origin;
	}

	public IndustrialPiece(CompoundTag tag) {
		super(ModStructures.INDUSTRIAL_PIECE, tag);
		this.layout = Layout.valueOf(tag.getStringOr("Layout", Layout.SOLAR_ARRAY.name()));
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 0), tag.getIntOr("OZ", 0));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Layout", this.layout.name());
		tag.putInt("OX", this.origin.getX());
		tag.putInt("OY", this.origin.getY());
		tag.putInt("OZ", this.origin.getZ());
	}

	public Layout layout() {
		return this.layout;
	}

	public BlockPos origin() {
		return this.origin;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
		ChunkPos chunkPos, BlockPos pivot) {
		Draw draw = new Draw(level, chunkBox, this.origin, RandomSource.create(Mth.getSeed(this.origin.getX(), this.origin.getY(), this.origin.getZ())), random);
		Layouts.build(this.layout, draw);
	}

	/** Drawing helper in local coordinates, clipped to the chunk being generated. */
	public static final class Draw {
		final WorldGenLevel level;
		final BoundingBox chunkBox;
		final BlockPos origin;
		/** The structure's own random: the same sequence in every chunk. */
		public final RandomSource random;
		/** The chunk's random (only for loot seeds, which are per container anyway). */
		final RandomSource chunkRandom;

		Draw(WorldGenLevel level, BoundingBox chunkBox, BlockPos origin, RandomSource random, RandomSource chunkRandom) {
			this.level = level;
			this.chunkBox = chunkBox;
			this.origin = origin;
			this.random = random;
			this.chunkRandom = chunkRandom;
		}

		public BlockPos world(int x, int y, int z) {
			return this.origin.offset(x, y, z);
		}

		public boolean inChunk(int x, int y, int z) {
			return this.chunkBox.isInside(this.world(x, y, z));
		}

		public void set(int x, int y, int z, BlockState state) {
			BlockPos pos = this.world(x, y, z);
			if (this.chunkBox.isInside(pos)) {
				this.level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			}
		}

		public void set(int x, int y, int z, Block block) {
			this.set(x, y, z, block.defaultBlockState());
		}

		/** Filled box (inclusive corners). */
		public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						this.set(x, y, z, state);
					}
				}
			}
		}

		public void box(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
			this.box(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
		}

		public void air(int x0, int y0, int z0, int x1, int y1, int z1) {
			this.box(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
		}

		/** Walls, floor and ceiling of {@code wall}, air inside. */
		public void room(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall) {
			this.box(x0, y0, z0, x1, y1, z1, wall);
			this.air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
		}

		/** Like {@link #room} with some wall blocks worn (cracked, mossy or missing). */
		public void ruinedRoom(int x0, int y0, int z0, int x1, int y1, int z1, BlockState wall, float wear) {
			for (int x = x0; x <= x1; x++) {
				for (int y = y0; y <= y1; y++) {
					for (int z = z0; z <= z1; z++) {
						boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
						if (!shell) {
							this.set(x, y, z, Blocks.AIR.defaultBlockState());
						} else {
							this.set(x, y, z, this.random.nextFloat() < wear ? this.worn(wall, y == y0) : wall);
						}
					}
				}
			}
		}

		/** A worn version of a wall block: cracked or mossy where one exists, otherwise gravel-filled or missing. */
		public BlockState worn(BlockState wall, boolean floor) {
			if (wall.is(Blocks.STONE_BRICKS)) {
				return this.random.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
			}

			if (wall.is(Blocks.DEEPSLATE_TILES)) {
				return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
			}

			if (wall.is(Blocks.DEEPSLATE_BRICKS)) {
				return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
			}

			return floor ? Blocks.GRAVEL.defaultBlockState() : Blocks.AIR.defaultBlockState();
		}

		/** Alternating yellow and black hazard stripe along a line. */
		public void hazard(int x0, int y, int z0, int x1, int z1) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					this.set(x, y, z, Math.floorMod(x + z, 2) == 0 ? Layouts.YELLOW : Layouts.BLACK);
				}
			}
		}

		/** Foundation under a surface structure (its floor is y = -1): fills down from y = -2 to the ground (at most 12 blocks). */
		public void foundation(int x0, int z0, int x1, int z1, BlockState fill) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					for (int y = -2; y >= -13; y--) {
						BlockPos pos = this.world(x, y, z);
						if (!this.chunkBox.isInside(pos)) {
							break;
						}

						BlockState state = this.level.getBlockState(pos);
						if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
							break;
						}

						this.level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		/** A chest whose contents come from a loot table when first opened. */
		public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> loot) {
			BlockPos pos = this.world(x, y, z);
			if (this.chunkBox.isInside(pos)) {
				this.level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
				RandomizableContainer.setBlockEntityLootTable(this.level, this.chunkRandom, pos, loot);
			}
		}

		/** A barrel with loot (supply lockers). */
		public void barrel(int x, int y, int z, ResourceKey<LootTable> loot) {
			BlockPos pos = this.world(x, y, z);
			if (this.chunkBox.isInside(pos)) {
				this.level.setBlock(pos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
				RandomizableContainer.setBlockEntityLootTable(this.level, this.chunkRandom, pos, loot);
			}
		}

		/** A Laser Turret: wrecked, or charged to {@code charge} (0-1) of its buffer with nothing to recharge it. */
		public void turret(int x, int y, int z, TurretTier tier, boolean wrecked, float charge) {
			BlockPos pos = this.world(x, y, z);
			if (!this.chunkBox.isInside(pos)) {
				return;
			}

			Block block = switch (tier) {
				case BASIC -> com.solarquarry.registry.ModBlocks.LASER_TURRET_BASIC;
				case ADVANCED -> com.solarquarry.registry.ModBlocks.LASER_TURRET_ADVANCED;
				case ELITE -> com.solarquarry.registry.ModBlocks.LASER_TURRET_ELITE;
			};
			this.level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (this.level.getBlockEntity(pos) instanceof LaserTurretBlockEntity turret) {
				turret.setDamaged(wrecked);
				if (!wrecked) {
					turret.restoreEnergy((long) (tier.capacity() * Math.clamp(charge, 0.0F, 1.0F)));
				}
			}
		}

		public void spawner(int x, int y, int z, EntityType<?> type) {
			BlockPos pos = this.world(x, y, z);
			if (this.chunkBox.isInside(pos)) {
				this.level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (this.level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
					spawner.setEntityId(type, this.chunkRandom);
				}
			}
		}

		/** A chest minecart with loot standing on a rail (freight cars). */
		public void freightCar(int x, int y, int z, ResourceKey<LootTable> loot) {
			BlockPos pos = this.world(x, y, z);
			if (!this.chunkBox.isInside(pos)) {
				return;
			}

			this.level.setBlock(pos, Blocks.RAIL.defaultBlockState(), Block.UPDATE_CLIENTS);
			var cart = EntityTypes.CHEST_MINECART.create(this.level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
			if (cart != null) {
				cart.setInitialPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				cart.setLootTable(loot, this.chunkRandom.nextLong());
				this.level.addFreshEntity(cart);
			}
		}

		public boolean chance(float p) {
			return this.random.nextFloat() < p;
		}
	}
}
