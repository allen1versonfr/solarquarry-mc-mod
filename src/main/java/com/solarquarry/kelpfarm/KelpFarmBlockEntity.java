package com.solarquarry.kelpfarm;

import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.construction.ConstructionBlocks;
import com.solarquarry.construction.ConstructionHost;
import com.solarquarry.construction.ConstructionProblem;
import com.solarquarry.construction.ConstructionSite;
import com.solarquarry.construction.ConstructionState;
import com.solarquarry.construction.Placement;
import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.quarry.UpgradeType;
import com.solarquarry.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Kelp Farm: builds a glass basin in front of itself (shared construction framework), fills it with water, plants kelp
 * on the floor and harvests it when it has grown, leaving the bottom block so it grows back. Growth is vanilla kelp
 * growth (random ticks, bone meal, the Growth Upgrade adds random ticks); nothing is produced on a timer. Harvested
 * kelp is the real drops of the broken blocks and is pushed into adjacent pipes and inventories.
 *
 * <p>Slots: 0-2 kelp to plant, 3 bone meal, 4-21 output, 22-24 upgrades (speed, capacity, efficiency, growth).
 */
public class KelpFarmBlockEntity extends BlockEntity implements WorldlyContainer, EnergyNode, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int KELP_SLOTS = 3;
	public static final int BONE_MEAL_SLOT = 3;
	public static final int OUTPUT_START = 4;
	public static final int OUTPUT_SLOTS = 18;
	public static final int UPGRADE_START = 22;
	public static final int UPGRADE_SLOTS = 3;
	public static final int TOTAL_SLOTS = 25;
	/** Inner basin: (2 * RADIUS + 1) squared columns. */
	public static final int RADIUS = 2;
	/** Water layers in the basin; kelp is harvested when it reaches the top one. */
	public static final int HEIGHT = 6;
	public static final long BASE_CAPACITY = 40_000;
	public static final long MAX_INPUT = 1_024;
	public static final long BUILD_COST = 20;
	public static final long FILL_COST = 10;
	public static final long PLANT_COST = 10;
	public static final long HARVEST_COST = 6;
	public static final long BONE_MEAL_COST = 20;
	public static final long GROWTH_COST = 2;
	/** Growth Upgrade: one extra random tick per level for each kelp tip every this many ticks (about 1 kelp per 2 s per column with two upgrades). */
	public static final int GROWTH_INTERVAL = 60;
	public static final int DATA_COUNT = 16;
	private static final int[] INPUT_FACES = {0, 1, 2, 3};
	private static final int[] OUTPUT_FACES = range(OUTPUT_START, OUTPUT_START + OUTPUT_SLOTS);

	public enum Status {
		IDLE,
		BUILDING,
		BUILD_BLOCKED,
		FILLING,
		FILL_BLOCKED,
		PLANTING,
		GROWING,
		HARVESTING,
		NO_KELP,
		NO_POWER,
		OUTPUT_FULL
	}

	private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
	private final ConstructionSite site = new ConstructionSite(2, 2);
	private long energy;
	private long energyReceivedThisTick;
	private Status status = Status.IDLE;
	private @Nullable BlockPos blockedAt;
	private int cooldown;
	private long ticks;
	private long harvested;
	private int planted;
	private int harvestCursor;
	private int boneMealCursor;
	private boolean syncDirty;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			KelpFarmBlockEntity f = KelpFarmBlockEntity.this;
			long capacity = f.getEnergyCapacity();
			BlockPos problem = f.problemPos();
			return switch (index) {
				case 0 -> (int) (f.energy & 0xFFFF);
				case 1 -> (int) ((f.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (capacity & 0xFFFF);
				case 3 -> (int) ((capacity >>> 16) & 0xFFFF);
				case 4 -> f.status.ordinal();
				case 5 -> (int) Math.min(0x7FFF, f.harvested);
				case 6 -> f.countKelpColumns();
				case 7 -> f.site.state().ordinal();
				case 8 -> f.site.percent();
				case 9 -> problem == null ? 0 : 1;
				case 10 -> problem == null ? 0 : problem.getX() - f.worldPosition.getX();
				case 11 -> problem == null ? 0 : problem.getY() - f.worldPosition.getY();
				case 12 -> problem == null ? 0 : problem.getZ() - f.worldPosition.getZ();
				case 13 -> f.upgradeCount(UpgradeType.SPEED);
				case 14 -> f.upgradeCount(UpgradeType.GROWTH);
				case 15 -> f.tallestColumn();
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

	public KelpFarmBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.KELP_FARM, pos, state);
	}

	private static int[] range(int from, int to) {
		int[] r = new int[to - from];
		for (int i = 0; i < r.length; i++) {
			r[i] = from + i;
		}

		return r;
	}

	// ---- layout ----

	public Direction facing() {
		BlockState state = this.getBlockState();
		return state.hasProperty(KelpFarmBlock.FACING) ? state.getValue(KelpFarmBlock.FACING) : Direction.NORTH;
	}

	/** Centre column of the basin, at the machine's height (the first water layer). */
	public BlockPos basinCenter() {
		return this.worldPosition.relative(this.facing(), RADIUS + 2);
	}

	/** Bottom water cell of every column, in a fixed order. */
	public List<BlockPos> columns() {
		BlockPos c = this.basinCenter();
		List<BlockPos> list = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				list.add(c.offset(dx, 0, dz));
			}
		}

		return list;
	}

	/**
	 * The basin: a floor of Industrial Frame where the ground under it is not solid, glass walls around the water
	 * ({@link #HEIGHT} high) with frame corner posts, and a frame rim on top of the corners. Returns null while any of
	 * it is unloaded.
	 */
	private @Nullable List<Placement> blueprint(ServerLevel level) {
		BlockState frame = ConstructionBlocks.INDUSTRIAL_FRAME.defaultBlockState();
		BlockState glass = ConstructionBlocks.INDUSTRIAL_GLASS.defaultBlockState();
		BlockPos c = this.basinCenter();
		int w = RADIUS + 1;
		List<Placement> floor = new ArrayList<>();
		List<Placement> walls = new ArrayList<>();
		for (int dx = -w; dx <= w; dx++) {
			for (int dz = -w; dz <= w; dz++) {
				BlockPos bottom = c.offset(dx, 0, dz);
				if (!level.isLoaded(bottom)) {
					return null;
				}

				BlockPos under = bottom.below();
				if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
					floor.add(new Placement(under, frame));
				}

				boolean edge = Math.abs(dx) == w || Math.abs(dz) == w;
				boolean corner = Math.abs(dx) == w && Math.abs(dz) == w;
				if (edge && !bottom.equals(this.worldPosition.relative(this.facing()))) {
					for (int y = 0; y < HEIGHT; y++) {
						walls.add(new Placement(bottom.above(y), corner ? frame : glass));
					}
				} else if (edge) {
					// The wall cell touching the machine: frame (the machine's mount), glass above it.
					for (int y = 0; y < HEIGHT; y++) {
						walls.add(new Placement(bottom.above(y), y == 0 ? frame : glass));
					}
				}
			}
		}

		List<Placement> all = new ArrayList<>(floor);
		all.addAll(walls);
		return all;
	}

	/** Water may be built over (the farm can stand in a lake); seagrass is cleared like grass on land. */
	static boolean canReplace(BlockState existing) {
		if (ConstructionHost.isClearable(existing)) {
			return true;
		}

		return !existing.hasBlockEntity() && (existing.is(Blocks.WATER) || existing.is(Blocks.SEAGRASS) || existing.is(Blocks.TALL_SEAGRASS));
	}

	// ---- upgrades and numbers ----

	public int upgradeCount(UpgradeType type) {
		int n = 0;
		for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_SLOTS; i++) {
			if (this.items.get(i).getItem() instanceof UpgradeItem upgrade && upgrade.type() == type) {
				n += Math.min(this.items.get(i).getCount(), type.maxStack());
			}
		}

		return Math.min(n, type.maxStack());
	}

	public int ticksPerOperation() {
		return Math.max(2, 10 / (1 + this.upgradeCount(UpgradeType.SPEED)));
	}

	private long cost(long base) {
		double m = (1.0 - 0.15 * this.upgradeCount(UpgradeType.ENERGY_EFFICIENCY)) * (1.0 + 0.2 * this.upgradeCount(UpgradeType.SPEED))
			* com.solarquarry.config.SolarQuarryConfig.get().energyCostMultiplier();
		return Math.max(1L, Math.round(base * m));
	}

	private boolean pay(long base) {
		long c = this.cost(base);
		if (this.energy < c) {
			return false;
		}

		this.energy -= c;
		return true;
	}

	// ---- tick ----

	public static void serverTick(Level level, BlockPos pos, BlockState state, KelpFarmBlockEntity farm) {
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

		int growth = this.upgradeCount(UpgradeType.GROWTH);
		if (growth > 0 && this.site.isComplete() && this.ticks % GROWTH_INTERVAL == 0) {
			this.growthTicks(level, growth);
		}

		if (this.ticks % 10 == 0) {
			this.pushOutput(level);
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

	private void planConstruction(ServerLevel level) {
		if (this.site.state() != ConstructionState.IDLE) {
			List<Placement> plan = this.site.plan();
			if (plan.isEmpty() || plan.getFirst().pos().distManhattan(this.worldPosition) <= 3 * (RADIUS + 3) + HEIGHT) {
				return;
			}

			this.site.reset(); // moved by a wrench: plan again here
		}

		List<Placement> placements = this.blueprint(level);
		if (placements != null) {
			this.site.plan(placements);
			this.syncDirty = true;
			this.setChanged();
		}
	}

	private Status build(ServerLevel level) {
		ConstructionHost host = new ConstructionHost() {
			@Override
			public boolean payForStep() {
				return KelpFarmBlockEntity.this.pay(BUILD_COST);
			}

			@Override
			public boolean canReplace(BlockState existing) {
				return KelpFarmBlockEntity.canReplace(existing);
			}

			@Override
			public void onStepDone(ServerLevel l, @Nullable BlockPos pos) {
				if (pos != null) {
					l.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.05);
					if (l.getRandom().nextInt(3) == 0) {
						l.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, com.solarquarry.registry.ModSounds.volume(0.4F), 1.3F);
					}
				}
			}

			@Override
			public void onCompleted(ServerLevel l) {
				l.playSound(null, KelpFarmBlockEntity.this.worldPosition, com.solarquarry.registry.ModSounds.MACHINE_START, SoundSource.BLOCKS,
					com.solarquarry.registry.ModSounds.volume(0.7F), 1.0F);
			}
		};
		if (this.site.tick(level, host)) {
			this.syncDirty = true;
			this.setChanged();
		}

		return switch (this.site.state()) {
			case COMPLETED -> Status.IDLE;
			case PAUSED -> this.site.problem() == ConstructionProblem.NO_POWER ? Status.NO_POWER : Status.BUILD_BLOCKED;
			case ERROR -> Status.BUILD_BLOCKED;
			default -> Status.BUILDING;
		};
	}

	/** One operation: fill a missing water cell, else plant, else harvest a grown column, else bone meal. */
	private Status work(ServerLevel level) {
		this.blockedAt = null;
		Status filled = this.fillNext(level);
		if (filled != null) {
			return filled;
		}

		Status plantedNow = this.plantNext(level);
		if (plantedNow != null) {
			return plantedNow;
		}

		Status cut = this.harvestNext(level);
		if (cut != null) {
			return cut;
		}

		return this.boneMealNext(level);
	}

	private static boolean isKelp(BlockState state) {
		return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
	}

	private static boolean isWaterSource(BlockState state) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}

	/** Fills the lowest cell of the basin that is not water (or kelp) yet. Returns null when the basin is full. */
	private @Nullable Status fillNext(ServerLevel level) {
		for (int y = 0; y < HEIGHT; y++) {
			for (BlockPos column : this.columns()) {
				BlockPos pos = column.above(y);
				if (!level.isLoaded(pos)) {
					return Status.FILL_BLOCKED;
				}

				BlockState state = level.getBlockState(pos);
				if (isWaterSource(state) || isKelp(state)) {
					continue;
				}

				boolean flowing = state.is(Blocks.WATER);
				if (!flowing && !canReplace(state)) {
					this.blockedAt = pos.immutable();
					return Status.FILL_BLOCKED; // a player's block in the basin: never removed
				}

				if (!this.pay(FILL_COST)) {
					return Status.NO_POWER;
				}

				if (!state.isAir() && !flowing) {
					level.destroyBlock(pos, false);
				}

				level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				this.syncDirty = true;
				return Status.FILLING;
			}
		}

		return null;
	}

	/** Plants kelp on the floor of the next column that has none. Returns null when every column is planted. */
	private @Nullable Status plantNext(ServerLevel level) {
		boolean free = false;
		for (BlockPos column : this.columns()) {
			BlockState state = level.getBlockState(column);
			if (isKelp(state) || !isWaterSource(state)) {
				continue;
			}

			free = true;
			int slot = this.kelpSlot();
			if (slot < 0) {
				continue;
			}

			BlockState kelp = Blocks.KELP.defaultBlockState();
			if (!kelp.canSurvive(level, column)) {
				continue;
			}

			if (!this.pay(PLANT_COST)) {
				return Status.NO_POWER;
			}

			level.setBlock(column, kelp.setValue(GrowingPlantHeadBlock.AGE, level.getRandom().nextInt(GrowingPlantHeadBlock.MAX_AGE)), Block.UPDATE_ALL);
			this.items.get(slot).shrink(1);
			this.planted++;
			this.syncDirty = true;
			this.setChanged();
			return Status.PLANTING;
		}

		return free ? Status.NO_KELP : null;
	}

	private int kelpSlot() {
		for (int i = 0; i < KELP_SLOTS; i++) {
			if (this.items.get(i).is(Items.KELP)) {
				return i;
			}
		}

		return -1;
	}

	/** Height of the kelp in a column (0 when the floor cell holds no kelp). */
	public static int kelpHeight(Level level, BlockPos column) {
		int h = 0;
		while (h < HEIGHT + 8 && isKelp(level.getBlockState(column.above(h)))) {
			h++;
		}

		return h;
	}

	/** A column is ripe when its kelp reached the top water layer or has stopped growing below it. */
	private boolean ripe(ServerLevel level, BlockPos column) {
		int h = kelpHeight(level, column);
		if (h < 2) {
			return false;
		}

		if (h >= HEIGHT) {
			return true;
		}

		BlockState head = level.getBlockState(column.above(h - 1));
		return head.is(Blocks.KELP) && head.getValue(GrowingPlantHeadBlock.AGE) >= GrowingPlantHeadBlock.MAX_AGE;
	}

	/**
	 * Cuts one ripe column down to its bottom block (which stays and grows again), collecting the real drops. Returns
	 * null when no column is ripe.
	 */
	private @Nullable Status harvestNext(ServerLevel level) {
		List<BlockPos> columns = this.columns();
		for (int k = 0; k < columns.size(); k++) {
			int index = (this.harvestCursor + k) % columns.size();
			BlockPos column = columns.get(index);
			if (!this.ripe(level, column)) {
				continue;
			}

			int h = kelpHeight(level, column);
			List<ItemStack> drops = new ArrayList<>();
			for (int y = h - 1; y >= 1; y--) {
				BlockPos pos = column.above(y);
				drops.addAll(Block.getDrops(level.getBlockState(pos), level, pos, null));
			}

			if (!this.fitsAll(drops)) {
				return Status.OUTPUT_FULL;
			}

			long c = this.cost(HARVEST_COST) * (h - 1);
			if (this.energy < c) {
				return Status.NO_POWER;
			}

			this.energy -= c;
			// Top down, each cut block becomes water again; the block left at the bottom turns back into a growing tip.
			for (int y = h - 1; y >= 1; y--) {
				level.setBlock(column.above(y), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
			}

			for (ItemStack drop : drops) {
				this.store(drop);
				this.harvested += drop.is(Items.KELP) ? drop.getCount() : 0;
			}

			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, column.getX() + 0.5, column.getY() + 1.0, column.getZ() + 0.5, 12, 0.2, h * 0.4, 0.2, 0.05);
			level.playSound(null, column, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.BLOCKS, com.solarquarry.registry.ModSounds.volume(0.6F), 1.0F);
			this.harvestCursor = (index + 1) % columns.size();
			this.syncDirty = true;
			this.setChanged();
			return Status.HARVESTING;
		}

		return null;
	}

	/** Bone meal on the next growing kelp tip (round-robin). */
	private Status boneMealNext(ServerLevel level) {
		ItemStack meal = this.items.get(BONE_MEAL_SLOT);
		List<BlockPos> columns = this.columns();
		boolean any = false;
		for (int k = 0; k < columns.size(); k++) {
			int index = (this.boneMealCursor + k) % columns.size();
			BlockPos column = columns.get(index);
			int h = kelpHeight(level, column);
			if (h == 0) {
				continue;
			}

			any = true;
			BlockPos tip = column.above(h - 1);
			BlockState state = level.getBlockState(tip);
			if (meal.isEmpty() || !meal.is(Items.BONE_MEAL) || !(state.getBlock() instanceof GrowingPlantHeadBlock head)
				|| !head.isValidBonemealTarget(level, tip, state) || h >= HEIGHT) {
				continue;
			}

			if (!this.pay(BONE_MEAL_COST)) {
				return Status.NO_POWER;
			}

			meal.shrink(1);
			head.performBonemeal(level, level.getRandom(), tip, state);
			level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, tip, 15);
			this.boneMealCursor = (index + 1) % columns.size();
			this.setChanged();
			return Status.GROWING;
		}

		return any ? Status.GROWING : Status.IDLE;
	}

	/** Growth Upgrade: extra random ticks for each kelp tip, paid per tip. */
	private void growthTicks(ServerLevel level, int growth) {
		for (BlockPos column : this.columns()) {
			int h = kelpHeight(level, column);
			if (h == 0 || h >= HEIGHT) {
				continue;
			}

			long c = this.cost(GROWTH_COST) * growth;
			if (this.energy < c) {
				return;
			}

			this.energy -= c;
			for (int i = 0; i < growth; i++) {
				BlockPos tip = column.above(kelpHeight(level, column) - 1);
				BlockState state = level.getBlockState(tip);
				if (state.is(Blocks.KELP)) {
					state.randomTick(level, tip, level.getRandom());
				}
			}
		}
	}

	// ---- output ----

	/** Pushes output into pipes and inventories next to the machine (not into the basin side). */
	private void pushOutput(ServerLevel level) {
		for (Direction dir : Direction.values()) {
			if (dir == this.facing()) {
				continue;
			}

			BlockPos neighbor = this.worldPosition.relative(dir);
			if (!level.isLoaded(neighbor)) {
				continue;
			}

			BlockEntity be = level.getBlockEntity(neighbor);
			if (be instanceof ItemPipeBlockEntity pipe) {
				for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
					ItemStack stack = this.items.get(i);
					if (!stack.isEmpty()) {
						ItemStack batch = stack.copyWithCount(Math.min(stack.getCount(), pipe.tier().batch()));
						if (pipe.acceptFromSource(batch, dir.getOpposite())) {
							stack.shrink(batch.getCount());
							this.setChanged();
						}

						break;
					}
				}

				continue;
			}

			if (be == null || be instanceof KelpFarmBlockEntity) {
				continue;
			}

			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, neighbor, dir.getOpposite());
			if (storage == null || !storage.supportsInsertion()) {
				continue;
			}

			for (int i = OUTPUT_START; i < OUTPUT_START + OUTPUT_SLOTS; i++) {
				ItemStack stack = this.items.get(i);
				if (stack.isEmpty()) {
					continue;
				}

				try (Transaction tx = Transaction.openOuter()) {
					long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), tx);
					if (inserted > 0) {
						tx.commit();
						stack.shrink((int) inserted);
						this.setChanged();
					}
				}
			}
		}
	}

	private boolean fitsAll(List<ItemStack> stacks) {
		List<ItemStack> sim = new ArrayList<>();
		for (int i = 0; i < TOTAL_SLOTS; i++) {
			sim.add(this.items.get(i).copy());
		}

		for (ItemStack stack : stacks) {
			ItemStack left = stack.copy();
			mergeInto(sim, OUTPUT_START, OUTPUT_START + OUTPUT_SLOTS, left);
			if (!left.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private void store(ItemStack stack) {
		mergeInto(this.items, OUTPUT_START, OUTPUT_START + OUTPUT_SLOTS, stack.copy());
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

	// ---- queries ----

	public Status getStatus() {
		return this.status;
	}

	public ConstructionSite getConstruction() {
		return this.site;
	}

	public long getHarvested() {
		return this.harvested;
	}

	public int getPlanted() {
		return this.planted;
	}

	public ContainerData getDataAccess() {
		return this.data;
	}

	private @Nullable BlockPos problemPos() {
		if (this.status != Status.BUILD_BLOCKED && this.status != Status.FILL_BLOCKED) {
			return null;
		}

		return this.blockedAt != null ? this.blockedAt : this.site.problemPos();
	}

	public int countKelpColumns() {
		if (this.level == null || !this.site.isComplete()) {
			return 0;
		}

		int n = 0;
		for (BlockPos column : this.columns()) {
			if (isKelp(this.level.getBlockState(column))) {
				n++;
			}
		}

		return n;
	}

	public int tallestColumn() {
		if (this.level == null || !this.site.isComplete()) {
			return 0;
		}

		int best = 0;
		for (BlockPos column : this.columns()) {
			best = Math.max(best, kelpHeight(this.level, column));
		}

		return best;
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
		return BASE_CAPACITY * (1 + this.upgradeCount(UpgradeType.ENERGY_CAPACITY));
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
		if (slot < KELP_SLOTS) {
			return stack.is(Items.KELP);
		}

		if (slot == BONE_MEAL_SLOT) {
			return stack.is(Items.BONE_MEAL);
		}

		if (slot < UPGRADE_START) {
			return false;
		}

		if (!(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.type().fitsKelpFarm()) {
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
		return side != Direction.DOWN && slot <= BONE_MEAL_SLOT && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot >= OUTPUT_START && slot < OUTPUT_START + OUTPUT_SLOTS;
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.kelp_farm");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new KelpFarmMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		if (input.list("Items", net.minecraft.world.ItemStackWithSlot.CODEC).isPresent()) {
			this.items.clear();
			ContainerHelper.loadAllItems(input, this.items);
		}

		this.energy = Math.max(0, input.getLongOr("Energy", this.energy));
		this.harvested = input.getLongOr("Harvested", this.harvested);
		this.planted = input.getIntOr("Planted", this.planted);
		int ordinal = input.getIntOr("Status", this.status.ordinal());
		this.status = ordinal >= 0 && ordinal < Status.values().length ? Status.values()[ordinal] : Status.IDLE;
		if (!this.site.load(input)) {
			this.site.reset();
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		output.putLong("Energy", this.energy);
		output.putLong("Harvested", this.harvested);
		output.putInt("Planted", this.planted);
		output.putInt("Status", this.status.ordinal());
		this.site.save(output);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putInt("Status", this.status.ordinal());
		return output.buildResult();
	}

	/** Breaking the farm takes its basin down too; the water inside is removed with it so nothing floods. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level instanceof ServerLevel server) {
			this.drainAndDismantle(server);
		}

		super.preRemoveSideEffects(pos, state);
	}

	private void drainAndDismantle(ServerLevel level) {
		if (this.site.state() != ConstructionState.IDLE) {
			for (BlockPos column : this.columns()) {
				for (int y = HEIGHT - 1; y >= 0; y--) {
					BlockPos p = column.above(y);
					if (level.isLoaded(p) && (level.getBlockState(p).is(Blocks.WATER) || isKelp(level.getBlockState(p)))) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		this.site.dismantle(level, ConstructionBlocks::isMachineBuilt);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		if (this.level instanceof ServerLevel server) {
			this.drainAndDismantle(server);
		}

		this.site.reset();
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.energy = 0;
		this.setChanged();
		return data;
	}
}
