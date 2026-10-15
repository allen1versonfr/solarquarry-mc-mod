package com.solarquarry.treefarm;

import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.quarry.UpgradeType;
import com.solarquarry.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Tree Farm: plants saplings on a grid in front of the machine, speeds growth with bone meal (and the Growth
 * Upgrade), and sends its harvesting drone to fell grown trees, collecting the real drops. Tree detection is bounded
 * ({@link TreeScan}) and refuses anything touching built blocks, so it never cuts houses or beams.
 *
 * <p>Slots: 0-8 saplings, 9-11 bone meal, 12-29 output, 30-33 upgrades (speed, capacity, efficiency, range, growth,
 * harvest; quarry-only upgrades are refused).
 */
public class TreeFarmBlockEntity extends BlockEntity implements WorldlyContainer, EnergyNode, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int SAPLING_SLOTS = 9;
	public static final int BONE_MEAL_START = 9;
	public static final int BONE_MEAL_SLOTS = 3;
	public static final int OUTPUT_START = 12;
	public static final int OUTPUT_SLOTS = 18;
	public static final int UPGRADE_START = 30;
	public static final int UPGRADE_SLOTS = 4;
	public static final int TOTAL_SLOTS = 34;
	public static final int SPACING = 3;
	public static final long BASE_CAPACITY = 50_000;
	public static final long MAX_INPUT = 1_024;
	public static final long PLANT_COST = 20;
	public static final long LOG_COST = 40;
	public static final long LEAF_COST = 8;
	public static final long BONE_MEAL_COST = 30;
	public static final long GROWTH_COST = 4;
	/** Energy per construction step (one frame block). */
	public static final long BUILD_COST = 25;
	/** How far above and below the machine's height the farm looks for ground at each spot. */
	public static final int GROUND_UP = 2;
	public static final int GROUND_DOWN = 3;
	public static final int DATA_COUNT = 20;
	private static final int[] INPUT_FACES = range(0, BONE_MEAL_START + BONE_MEAL_SLOTS);
	private static final int[] OUTPUT_FACES = range(OUTPUT_START, OUTPUT_START + OUTPUT_SLOTS);

	public enum Status {
		IDLE,
		PLANTING,
		GROWING,
		HARVESTING,
		NO_SAPLINGS,
		NO_POWER,
		OUTPUT_FULL,
		/** Building its mast and corner posts (shared construction framework). */
		BUILDING,
		/** Construction is waiting: a block is in the way, or its chunk is not loaded. */
		BUILD_BLOCKED,
		/** No spot in the area has plantable ground (dirt, grass, moss...) within reach of the machine's height. */
		NO_GROUND
	}

	private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
	private long energy;
	private Status status = Status.IDLE;
	private int cooldown;
	private long ticks;
	private int treesFelled;
	private int saplingsPlanted;
	/** Tree being felled: remaining blocks, logs first from the top. */
	private final List<BlockPos> felling = new ArrayList<>();
	private int fellingTotal;
	/** Where the drone is working (synced for the renderer). */
	private @Nullable BlockPos droneTarget;
	private int scanCursor;
	private long energyReceivedThisTick;
	private boolean syncDirty;
	/** Mast on the machine and posts at the area's corners, built before the farm starts working. */
	private final com.solarquarry.construction.ConstructionSite site = new com.solarquarry.construction.ConstructionSite(4, 1);
	/** Radius the current construction plan was made for (-1 = not planned since loading). */
	private int plannedRadius = -1;
	/** Whether the last planting round found any spot with ground (for the "no ground" message). */
	private boolean anyGround = true;

	// client-side drone animation
	public float clientDroneX = Float.NaN;
	public float clientDroneY;
	public float clientDroneZ;
	public long clientLastNanos;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			TreeFarmBlockEntity f = TreeFarmBlockEntity.this;
			long capacity = f.getEnergyCapacity();
			return switch (index) {
				case 0 -> (int) (f.energy & 0xFFFF);
				case 1 -> (int) ((f.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (capacity & 0xFFFF);
				case 3 -> (int) ((capacity >>> 16) & 0xFFFF);
				case 4 -> f.status.ordinal();
				case 5 -> f.radius();
				case 6 -> Math.min(0x7FFF, f.treesFelled);
				case 7 -> Math.min(0x7FFF, f.saplingsPlanted);
				case 8 -> f.fellingTotal == 0 ? 0 : (f.fellingTotal - f.felling.size()) * 100 / f.fellingTotal;
				case 9 -> f.upgradeCount(UpgradeType.SPEED);
				case 10 -> f.upgradeCount(UpgradeType.GROWTH);
				case 11 -> f.upgradeCount(UpgradeType.HARVEST);
				case 12 -> f.ticksPerOperation();
				case 13 -> f.countPlanted();
				case 14 -> f.site.state().ordinal();
				case 15 -> f.site.percent();
				case 16 -> f.site.problem().ordinal();
				case 17 -> f.site.problemPos() == null ? 0 : f.site.problemPos().getX() - f.worldPosition.getX();
				case 18 -> f.site.problemPos() == null ? 0 : f.site.problemPos().getY() - f.worldPosition.getY();
				case 19 -> f.site.problemPos() == null ? 0 : f.site.problemPos().getZ() - f.worldPosition.getZ();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public TreeFarmBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TREE_FARM, pos, state);
	}

	private static int[] range(int from, int to) {
		int[] r = new int[to - from];
		for (int i = 0; i < r.length; i++) {
			r[i] = from + i;
		}

		return r;
	}

	// ---- upgrades and numbers ----

	public int upgradeCount(UpgradeType type) {
		int n = 0;
		for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) {
			if (this.items.get(i).getItem() instanceof UpgradeItem upgrade && upgrade.type() == type) {
				n += type == UpgradeType.RANGE ? upgrade.tier() : Math.min(this.items.get(i).getCount(), type.maxStack());
			}
		}

		return Math.min(n, type == UpgradeType.RANGE ? 2 : type.maxStack());
	}

	/** Half-width of the planting area: 4, 6 or 8 (Range Upgrade I/II). */
	public int radius() {
		return 4 + 2 * this.upgradeCount(UpgradeType.RANGE);
	}

	public int ticksPerOperation() {
		return Math.max(2, 20 / (1 + this.upgradeCount(UpgradeType.SPEED)));
	}

	public long getEnergyCapacityValue() {
		return BASE_CAPACITY * (1 + this.upgradeCount(UpgradeType.ENERGY_CAPACITY));
	}

	/** Energy multiplier: efficiency lowers it (15% per upgrade), speed raises it (20% per upgrade). */
	private long cost(long base) {
		double m = (1.0 - 0.15 * this.upgradeCount(UpgradeType.ENERGY_EFFICIENCY)) * (1.0 + 0.2 * this.upgradeCount(UpgradeType.SPEED))
			* com.solarquarry.config.SolarQuarryConfig.get().energyCostMultiplier();
		return Math.max(1L, Math.round(base * m));
	}

	/** Centre of the square work area: in front of the machine (the side it faces), at its height. */
	public BlockPos areaCenter() {
		Direction facing = this.getBlockState().hasProperty(TreeFarmBlock.FACING) ? this.getBlockState().getValue(TreeFarmBlock.FACING) : Direction.NORTH;
		return this.worldPosition.relative(facing, this.radius() + 1);
	}

	/** Planting spots: every third block in the area, on the ground level of the machine (one block up or down allowed). */
	public List<BlockPos> plantingSpots() {
		BlockPos c = this.areaCenter();
		int r = this.radius();
		List<BlockPos> spots = new ArrayList<>();
		for (int dx = -r + 1; dx <= r - 1; dx += SPACING) {
			for (int dz = -r + 1; dz <= r - 1; dz += SPACING) {
				spots.add(c.offset(dx, 0, dz));
			}
		}

		return spots;
	}

	public AABB workArea() {
		BlockPos c = this.areaCenter();
		int r = this.radius();
		return new AABB(c.getX() - r, c.getY() - 2, c.getZ() - r, c.getX() + r + 1, c.getY() + TreeScan.MAX_HEIGHT, c.getZ() + r + 1);
	}

	// ---- tick ----

	public static void serverTick(Level level, BlockPos pos, BlockState state, TreeFarmBlockEntity farm) {
		if (level instanceof ServerLevel server) {
			farm.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		this.ticks++;
		this.energyReceivedThisTick = 0;
		Status next = this.status;
		this.planConstruction(level);
		if (!this.site.isComplete()) {
			next = this.build(level);
		} else if (this.cooldown > 0) {
			this.cooldown--;
		} else {
			next = this.work(level);
			this.cooldown = this.ticksPerOperation();
		}

		// Growth Upgrade: extra random ticks for planted saplings, paid per sapling.
		int growth = this.upgradeCount(UpgradeType.GROWTH);
		if (growth > 0 && this.site.isComplete() && this.ticks % 20 == 0) {
			for (BlockPos spot : this.plantingSpots()) {
				BlockPos plant = this.plantAt(level, spot);
				BlockState state = plant == null ? null : level.getBlockState(plant);
				if (state != null && state.getBlock() instanceof SaplingBlock) {
					spot = plant;
					long c = this.cost(GROWTH_COST) * growth;
					if (this.energy < c) {
						break;
					}

					this.energy -= c;
					for (int i = 0; i < growth; i++) {
						state = level.getBlockState(spot);
						if (state.getBlock() instanceof SaplingBlock) {
							state.randomTick(level, spot, level.getRandom());
						}
					}
				}
			}
		}

		if (this.ticks % 40 == 0) {
			this.collectLooseDrops(level);
		}

		if (next != this.status) {
			this.status = next;
			this.syncDirty = true;
		}

		if (this.syncDirty && this.ticks % 5 == 0) {
			this.syncDirty = false;
			this.setChanged();
			level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** One operation: keep felling the current tree, else find a grown tree, else plant, else bone-meal a sapling. */
	private Status work(ServerLevel level) {
		if (!this.felling.isEmpty()) {
			return this.fellNext(level);
		}

		BlockPos tree = this.findGrownTree(level);
		if (tree != null) {
			return this.fellNext(level);
		}

		Status planted = this.plantNext(level);
		if (planted != null) {
			return planted;
		}

		Status grown = this.boneMealNext(level);
		return grown == Status.IDLE && !this.anyGround ? Status.NO_GROUND : grown;
	}

	// ---- construction (shared framework) ----

	/** (Re)plans the mast and corner posts when the farm is new, was moved or its range changed. */
	private void planConstruction(ServerLevel level) {
		int r = this.radius();
		var plan = this.site.plan();
		boolean stale = !plan.isEmpty() && plan.getFirst().pos().distManhattan(this.worldPosition) > 2;
		if (r == this.plannedRadius && !stale && this.site.state() != com.solarquarry.construction.ConstructionState.IDLE) {
			return;
		}

		if (stale) {
			this.site.reset();
		}

		java.util.List<com.solarquarry.construction.Placement> placements = this.blueprint(level);
		if (placements == null) {
			return; // terrain not loaded yet: try again next tick
		}

		if (this.plannedRadius >= 0 && this.plannedRadius != r) {
			// Range changed: take down the posts of the old area that are not part of the new plan.
			java.util.Set<BlockPos> keep = new java.util.HashSet<>();
			placements.forEach(p -> keep.add(p.pos()));
			for (com.solarquarry.construction.Placement old : plan) {
				if (!keep.contains(old.pos()) && level.isLoaded(old.pos())
					&& com.solarquarry.construction.ConstructionBlocks.isMachineBuilt(level.getBlockState(old.pos()))) {
					level.setBlock(old.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}

		this.plannedRadius = r;
		this.site.plan(placements);
		this.syncDirty = true;
		this.setChanged();
	}

	/**
	 * The farm's structure: a three-block mast on top of the machine (the drone's dock) and a two-block post at each
	 * corner of the work area, standing on the ground there. Returns null while the area is not loaded.
	 */
	private java.util.@Nullable List<com.solarquarry.construction.Placement> blueprint(ServerLevel level) {
		BlockState frame = com.solarquarry.construction.ConstructionBlocks.INDUSTRIAL_FRAME.defaultBlockState();
		java.util.List<com.solarquarry.construction.Placement> list = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			list.add(new com.solarquarry.construction.Placement(this.worldPosition.above(i), frame));
		}

		BlockPos c = this.areaCenter();
		int r = this.radius();
		for (int[] corner : new int[][] {{-r, -r}, {-r, r}, {r, -r}, {r, r}}) {
			BlockPos column = c.offset(corner[0], 0, corner[1]);
			if (!level.isLoaded(column)) {
				return null;
			}

			BlockPos base = this.postBase(level, column);
			if (base != null) {
				list.add(new com.solarquarry.construction.Placement(base, frame));
				list.add(new com.solarquarry.construction.Placement(base.above(), frame));
			}
		}

		return list;
	}

	/** Lowest free (air or plants) block on solid ground in the column near the machine's height, or null. */
	private @Nullable BlockPos postBase(ServerLevel level, BlockPos column) {
		for (int dy = GROUND_UP; dy >= -GROUND_DOWN; dy--) {
			BlockPos pos = column.above(dy);
			BlockState below = level.getBlockState(pos.below());
			if (isClearable(level.getBlockState(pos)) && isClearable(level.getBlockState(pos.above()))
				&& below.isFaceSturdy(level, pos.below(), Direction.UP) && !below.is(BlockTags.LOGS) && !below.is(BlockTags.LEAVES)) {
				return pos;
			}
		}

		return null;
	}

	/** Air, or a plant/snow layer the farm may clear (never liquids, saplings or anything solid). */
	static boolean isClearable(BlockState state) {
		return com.solarquarry.construction.ConstructionHost.isClearable(state);
	}

	private Status build(ServerLevel level) {
		com.solarquarry.construction.ConstructionHost host = new com.solarquarry.construction.ConstructionHost() {
			@Override
			public boolean payForStep() {
				long c = TreeFarmBlockEntity.this.cost(BUILD_COST);
				if (TreeFarmBlockEntity.this.energy < c) {
					return false;
				}

				TreeFarmBlockEntity.this.energy -= c;
				return true;
			}

			@Override
			public void onStepDone(ServerLevel l, @Nullable BlockPos pos) {
				if (pos != null) {
					TreeFarmBlockEntity.this.droneTarget = pos.immutable();
					l.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
					l.playSound(null, pos, net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_PLACE, net.minecraft.sounds.SoundSource.BLOCKS,
						com.solarquarry.registry.ModSounds.volume(0.5F), 1.2F);
				}
			}

			@Override
			public void onCompleted(ServerLevel l) {
				l.playSound(null, TreeFarmBlockEntity.this.worldPosition, com.solarquarry.registry.ModSounds.MACHINE_START, net.minecraft.sounds.SoundSource.BLOCKS,
					com.solarquarry.registry.ModSounds.volume(0.7F), 1.1F);
			}
		};
		if (this.site.tick(level, host)) {
			this.syncDirty = true;
			this.setChanged();
		}

		return switch (this.site.state()) {
			case COMPLETED -> Status.IDLE;
			case PAUSED -> this.site.problem() == com.solarquarry.construction.ConstructionProblem.NO_POWER ? Status.NO_POWER : Status.BUILD_BLOCKED;
			case ERROR -> Status.BUILD_BLOCKED;
			default -> Status.BUILDING;
		};
	}

	public com.solarquarry.construction.ConstructionSite getConstruction() {
		return this.site;
	}

	private @Nullable BlockPos findGrownTree(ServerLevel level) {
		for (BlockPos spot : this.plantingSpots()) {
			if (!level.isLoaded(spot)) {
				continue;
			}

			// The trunk's lowest log, where a sapling was planted (within the farm's ground window).
			BlockPos base = this.plantAt(level, spot);
			if (base == null || !level.getBlockState(base).is(BlockTags.LOGS)) {
				continue;
			}

			TreeScan.Tree tree = TreeScan.find((x, y, z) -> kindAt(level, new BlockPos(x, y, z)), base.getX(), base.getY(), base.getZ());
			if (tree == null) {
				continue;
			}

			// Logs from the top down, then (with the Harvest Upgrade) the leaves.
			List<BlockPos> order = new ArrayList<>();
			for (int i = tree.logs().size() - 1; i >= 0; i--) {
				TreeScan.Pos p = tree.logs().get(i);
				order.add(new BlockPos(p.x(), p.y(), p.z()));
			}

			order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
			if (this.upgradeCount(UpgradeType.HARVEST) > 0) {
				for (TreeScan.Pos p : tree.leaves()) {
					order.add(new BlockPos(p.x(), p.y(), p.z()));
				}
			}

			this.felling.clear();
			this.felling.addAll(order);
			this.fellingTotal = order.size();
			this.setChanged();
			return spot;
		}

		return null;
	}

	public static TreeScan.Kind kindAt(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return TreeScan.Kind.BUILT; // unknown: be safe
		}

		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return TreeScan.Kind.AIR;
		}

		if (state.is(BlockTags.LOGS)) {
			return TreeScan.Kind.LOG;
		}

		if (state.getBlock() instanceof LeavesBlock) {
			return state.getValue(LeavesBlock.PERSISTENT) ? TreeScan.Kind.PLACED_LEAVES : TreeScan.Kind.NATURAL_LEAVES;
		}

		if (com.solarquarry.construction.ConstructionBlocks.isMachineBuilt(state)) {
			return TreeScan.Kind.NATURAL; // the farm's own posts never make a tree count as part of a building
		}

		if (state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.canBeReplaced()
			|| (state.getBlock() instanceof SaplingBlock) || state.is(BlockTags.FLOWERS) || state.is(net.minecraft.world.level.block.Blocks.VINE)
			|| state.is(net.minecraft.world.level.block.Blocks.COCOA) || state.is(net.minecraft.world.level.block.Blocks.BEE_NEST)
			|| state.is(net.minecraft.world.level.block.Blocks.GRAVEL) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
			|| !state.getFluidState().isEmpty() || state.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET)
			|| state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM)
			|| state.is(net.minecraft.world.level.block.Blocks.RED_MUSHROOM_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.BROWN_MUSHROOM_BLOCK)) {
			return TreeScan.Kind.NATURAL;
		}

		return TreeScan.Kind.BUILT;
	}

	private Status fellNext(ServerLevel level) {
		while (!this.felling.isEmpty()) {
			BlockPos pos = this.felling.getFirst();
			BlockState state = level.getBlockState(pos);
			boolean log = state.is(BlockTags.LOGS);
			if (!log && !(state.getBlock() instanceof LeavesBlock)) {
				this.felling.removeFirst(); // already gone (decayed, broken by someone)
				continue;
			}

			if (!level.isLoaded(pos)) {
				return Status.HARVESTING;
			}

			long c = this.cost(log ? LOG_COST : LEAF_COST);
			if (this.energy < c) {
				return Status.NO_POWER;
			}

			FakePlayer actor = FakePlayer.get(level);
			ItemStack tool = new ItemStack(Items.IRON_AXE);
			List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), actor, tool);
			if (!this.fitsAll(drops)) {
				return Status.OUTPUT_FULL;
			}

			this.energy -= c;
			level.destroyBlock(pos, false, actor);
			for (ItemStack drop : drops) {
				this.store(drop);
			}

			this.felling.removeFirst();
			this.droneTarget = pos.immutable();
			this.syncDirty = true;
			if (this.felling.isEmpty()) {
				this.treesFelled++;
				this.fellingTotal = 0;
			}

			this.setChanged();
			return Status.HARVESTING;
		}

		this.fellingTotal = 0;
		return Status.IDLE;
	}

	/** Plants one sapling on a free, valid spot. Returns null when every spot is taken. */
	private @Nullable Status plantNext(ServerLevel level) {
		boolean freeSpot = false;
		boolean ground = false;
		for (BlockPos spot : this.plantingSpots()) {
			if (this.plantAt(level, spot) != null) {
				ground = true;
				continue;
			}

			BlockPos target = this.groundSpot(level, spot);
			if (target == null) {
				continue;
			}

			ground = true;
			freeSpot = true;
			int slot = this.saplingSlotFor(level, target);
			if (slot < 0) {
				continue;
			}

			long c = this.cost(PLANT_COST);
			if (this.energy < c) {
				return Status.NO_POWER;
			}

			ItemStack stack = this.items.get(slot);
			BlockState sapling = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
			this.energy -= c;
			if (!level.getBlockState(target).isAir()) {
				level.destroyBlock(target, false); // grass, flowers or snow on the spot
			}

			if (!level.getBlockState(target.above()).isAir()) {
				level.destroyBlock(target.above(), false); // the top half of tall grass
			}

			level.setBlock(target, sapling, Block.UPDATE_ALL);
			stack.shrink(1);
			this.saplingsPlanted++;
			this.droneTarget = target.immutable();
			this.syncDirty = true;
			this.setChanged();
			return Status.PLANTING;
		}

		this.anyGround = ground;
		return freeSpot ? Status.NO_SAPLINGS : null;
	}

	/**
	 * Where a sapling would go at this spot: the first free position (air, grass, flowers, snow) on plantable ground,
	 * searching from {@link #GROUND_UP} above to {@link #GROUND_DOWN} below the machine's height. Null if there is
	 * none, or if part of the column is not loaded.
	 */
	private @Nullable BlockPos groundSpot(ServerLevel level, BlockPos spot) {
		for (int dy = GROUND_UP; dy >= -GROUND_DOWN; dy--) {
			BlockPos pos = spot.above(dy);
			if (!level.isLoaded(pos)) {
				return null;
			}

			BlockState state = level.getBlockState(pos);
			if (isClearable(state) && level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD) && isClearable(level.getBlockState(pos.above()))) {
				return pos;
			}
		}

		return null;
	}

	/** The sapling or the trunk's lowest log standing on the ground at this spot (within the ground window), or null. */
	private @Nullable BlockPos plantAt(Level level, BlockPos spot) {
		for (int dy = -GROUND_DOWN; dy <= GROUND_UP; dy++) {
			BlockPos pos = spot.above(dy);
			if (!level.isLoaded(pos)) {
				return null;
			}

			BlockState state = level.getBlockState(pos);
			if ((state.getBlock() instanceof SaplingBlock || state.is(BlockTags.LOGS)) && level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) {
				return pos;
			}
		}

		return null;
	}

	/** A sapling slot whose sapling can survive at the target, or -1. Saplings that need a 2 x 2 group are not used. */
	private int saplingSlotFor(ServerLevel level, BlockPos target) {
		for (int i = 0; i < SAPLING_SLOTS; i++) {
			ItemStack stack = this.items.get(i);
			if (isPlantable(stack) && ((BlockItem) stack.getItem()).getBlock().defaultBlockState().canSurvive(level, target)) {
				return i;
			}
		}

		return -1;
	}

	public static boolean isPlantable(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem
			&& !stack.is(Items.DARK_OAK_SAPLING) && !stack.is(Items.PALE_OAK_SAPLING);
	}

	/** Bone meal on one sapling; bone meal is only used when the sapling accepts it. */
	private Status boneMealNext(ServerLevel level) {
		int slot = -1;
		for (int i = BONE_MEAL_START; i < BONE_MEAL_START + BONE_MEAL_SLOTS; i++) {
			if (this.items.get(i).is(Items.BONE_MEAL)) {
				slot = i;
				break;
			}
		}

		boolean anySapling = false;
		for (BlockPos spot : this.plantingSpots()) {
			BlockPos plant = this.plantAt(level, spot);
			for (BlockPos pos : plant == null ? java.util.List.<BlockPos>of() : java.util.List.of(plant)) {
				BlockState state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof SaplingBlock sapling)) {
					continue;
				}

				anySapling = true;
				if (slot < 0 || !sapling.isValidBonemealTarget(level, pos, state)) {
					continue;
				}

				long c = this.cost(BONE_MEAL_COST);
				if (this.energy < c) {
					return Status.NO_POWER;
				}

				this.energy -= c;
				this.items.get(slot).shrink(1);
				if (sapling.isBonemealSuccess(level, level.getRandom(), pos, state)) {
					sapling.performBonemeal(level, level.getRandom(), pos, state);
				}

				level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 15);
				this.droneTarget = pos.immutable();
				this.syncDirty = true;
				this.setChanged();
				return Status.GROWING;
			}
		}

		return anySapling ? Status.GROWING : Status.IDLE;
	}

	/** Picks up saplings, sticks and apples lying in the area (leaves that decayed on their own). */
	private void collectLooseDrops(ServerLevel level) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.workArea(), e -> e.isAlive())) {
			ItemStack stack = item.getItem();
			if (!stack.is(ItemTags.SAPLINGS) && !stack.is(Items.STICK) && !stack.is(Items.APPLE)) {
				continue;
			}

			ItemStack copy = stack.copy();
			if (this.fitsAll(List.of(copy))) {
				this.store(copy);
				item.discard();
				this.setChanged();
			}
		}
	}

	// ---- inventory helpers ----

	private boolean fitsAll(List<ItemStack> stacks) {
		List<ItemStack> sim = new ArrayList<>();
		for (int i = 0; i < TOTAL_SLOTS; i++) {
			sim.add(this.items.get(i).copy());
		}

		for (ItemStack stack : stacks) {
			ItemStack left = stack.copy();
			insert(sim, left, true);
			if (!left.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/** Saplings refill the sapling slots first; everything else (and spare saplings) goes to the output. */
	private void store(ItemStack stack) {
		insert(this.items, stack, true);
	}

	private static void insert(List<ItemStack> slots, ItemStack stack, boolean saplingsFirst) {
		if (saplingsFirst && isPlantable(stack)) {
			mergeInto(slots, 0, SAPLING_SLOTS, stack);
		}

		mergeInto(slots, OUTPUT_START, OUTPUT_START + OUTPUT_SLOTS, stack);
	}

	private static void mergeInto(List<ItemStack> slots, int from, int to, ItemStack stack) {
		for (int i = from; i < to && !stack.isEmpty(); i++) {
			ItemStack existing = slots.get(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				existing.grow(move);
				stack.shrink(move);
			}
		}

		for (int i = from; i < to && !stack.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				slots.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
			}
		}
	}

	private int countPlanted() {
		if (this.level == null) {
			return 0;
		}

		int n = 0;
		for (BlockPos spot : this.plantingSpots()) {
			if (this.plantAt(this.level, spot) != null) {
				n++;
			}
		}

		return n;
	}

	public Status getStatus() {
		return this.status;
	}

	public @Nullable BlockPos getDroneTarget() {
		return this.droneTarget;
	}

	public int getTreesFelled() {
		return this.treesFelled;
	}

	public int getSaplingsPlanted() {
		return this.saplingsPlanted;
	}

	public ContainerData getDataAccess() {
		return this.data;
	}

	// ---- energy ----

	@Override
	public Role energyRole() {
		return Role.CONSUMER;
	}

	@Override
	public long getEnergy() {
		return this.energy;
	}

	@Override
	public long getEnergyCapacity() {
		return this.getEnergyCapacityValue();
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		long accepted = Math.max(0, Math.min(amount, Math.min(this.getEnergyCapacity() - this.energy, MAX_INPUT - this.energyReceivedThisTick)));
		if (!simulate && accepted > 0) {
			this.energy += accepted;
			this.energyReceivedThisTick += accepted;
			this.setChanged();
		}

		return accepted;
	}

	@Override
	public long extractEnergy(long amount, boolean simulate) {
		return 0;
	}

	@Override
	public void restoreEnergy(long amount) {
		this.energy = Math.min(this.getEnergyCapacity(), this.energy + Math.max(0, amount));
		this.setChanged();
	}

	// ---- container ----

	@Override
	public int getContainerSize() {
		return TOTAL_SLOTS;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < TOTAL_SLOTS ? this.items.get(slot) : ItemStack.EMPTY;
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, count);
		if (!result.isEmpty()) {
			this.setChanged();
		}

		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot >= 0 && slot < TOTAL_SLOTS) {
			this.items.set(slot, stack);
			stack.limitSize(this.getMaxStackSize(stack));
			this.setChanged();
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return canPlace(this, slot, stack);
	}

	/** Slot rules, shared with the client's mirror container. */
	public static boolean canPlace(Container container, int slot, ItemStack stack) {
		if (slot < SAPLING_SLOTS) {
			return isPlantable(stack);
		}

		if (slot < BONE_MEAL_START + BONE_MEAL_SLOTS) {
			return stack.is(Items.BONE_MEAL);
		}

		if (slot < UPGRADE_START) {
			return false;
		}

		if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.type().fitsTreeFarm()) {
			return false;
		}

		for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS && i < container.getContainerSize(); i++) {
			if (i != slot && container.getItem(i).getItem() instanceof UpgradeItem installed && installed.type() == upgrade.type()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUTPUT_FACES : INPUT_FACES;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return side != Direction.DOWN && slot < BONE_MEAL_START + BONE_MEAL_SLOTS && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS;
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.tree_farm");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new TreeFarmMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence and sync ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		if (input.list("Items", net.minecraft.world.ItemStackWithSlot.CODEC).isPresent()) {
			this.items.clear();
			ContainerHelper.loadAllItems(input, this.items);
		}

		this.energy = Math.max(0, input.getLongOr("Energy", this.energy));
		this.treesFelled = input.getIntOr("TreesFelled", this.treesFelled);
		this.saplingsPlanted = input.getIntOr("SaplingsPlanted", this.saplingsPlanted);
		int ordinal = input.getIntOr("Status", this.status.ordinal());
		this.status = ordinal >= 0 && ordinal < Status.values().length ? Status.values()[ordinal] : Status.IDLE;
		this.droneTarget = input.getLong("DroneTarget").map(BlockPos::of).orElse(null);
		this.felling.clear();
		input.read("Felling", com.mojang.serialization.Codec.LONG.listOf()).ifPresent(list -> list.forEach(l -> this.felling.add(BlockPos.of(l))));
		this.fellingTotal = Math.max(this.felling.size(), input.getIntOr("FellingTotal", 0));
		if (!this.site.load(input)) {
			this.site.reset(); // saved before 4.0: the structure is planned and built on the next tick
		}

		this.plannedRadius = this.site.plan().isEmpty() ? -1 : input.getIntOr("PlannedRadius", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		output.putLong("Energy", this.energy);
		output.putInt("TreesFelled", this.treesFelled);
		output.putInt("SaplingsPlanted", this.saplingsPlanted);
		this.writeSync(output);
		List<Long> felling = new ArrayList<>();
		this.felling.forEach(p -> felling.add(p.asLong()));
		output.store("Felling", com.mojang.serialization.Codec.LONG.listOf(), felling);
		output.putInt("FellingTotal", this.fellingTotal);
		this.site.save(output);
		output.putInt("PlannedRadius", this.plannedRadius);
	}

	private void writeSync(ValueOutput output) {
		output.putInt("Status", this.status.ordinal());
		if (this.droneTarget != null) {
			output.putLong("DroneTarget", this.droneTarget.asLong());
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		this.writeSync(output);
		return output.buildResult();
	}

	/** Breaking the farm takes its mast and posts down too (they drop nothing, so nothing is duplicated). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level instanceof ServerLevel server) {
			this.site.dismantle(server, com.solarquarry.construction.ConstructionBlocks::isMachineBuilt);
		}

		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		if (this.level instanceof ServerLevel server) {
			this.site.dismantle(server, com.solarquarry.construction.ConstructionBlocks::isMachineBuilt);
		}

		this.site.reset(); // rebuilt wherever the farm is placed next
		this.plannedRadius = -1;
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.energy = 0;
		this.felling.clear();
		this.setChanged();
		return data;
	}
}
