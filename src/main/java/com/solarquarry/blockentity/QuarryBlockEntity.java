package com.solarquarry.blockentity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.solarquarry.SolarQuarry;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.config.SolarQuarryConfig;
import com.solarquarry.quarry.MiningPattern;
import com.solarquarry.quarry.QuarryChunkLoading;
import com.solarquarry.quarry.QuarryFrame;
import com.solarquarry.quarry.QuarryFrames;
import com.solarquarry.quarry.QuarryLamp;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryProtection;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.quarry.QuarryUpgrades;
import com.solarquarry.quarry.UpgradeType;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModSounds;
import com.solarquarry.registry.ModTags;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class QuarryBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos>, EnergyNode {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int BUFFER_SLOTS = 27;
	public static final int UPGRADE_SLOTS = 6;
	public static final int FILTER_SLOTS = 9;
	public static final int TOTAL_SLOTS = BUFFER_SLOTS + UPGRADE_SLOTS;
	public static final int DATA_COUNT = 28;
	public static final int DEFAULT_SIZE = 16;
	public static final int DEFAULT_HEIGHT = 3;
	/** Maximum positions examined per tick while skipping air and ignored blocks. */
	private static final int SCAN_BUDGET = 1024;
	/** Maximum positions examined per tick while prospecting for ores (ore priority mode). */
	private static final int ORE_SCAN_BUDGET = 2048;
	/** Ore positions remembered at once; scanning pauses while the queue is full. */
	public static final int MAX_ORE_QUEUE = 2048;
	private static final long FLUID_COST = 10;
	private static final int[] BUFFER_SLOT_IDS = createRange(BUFFER_SLOTS);

	/** What the player asked the machine to do. */
	public enum Mode {
		IDLE,
		RUNNING,
		PAUSED
	}

	/** Which ghost list a config page shows. */
	public enum ListKind {
		VOID,
		SKIP,
		ORES
	}

	private enum StepResult {
		MINED,
		SKIPPED,
		SCANNING,
		NO_ENERGY,
		FULL,
		UNLOADED,
		FINISHED
	}

	// ---- persistent state ----
	private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
	private final SimpleContainer voidFilter = this.ghostList();
	private final SimpleContainer skipFilter = this.ghostList();
	private final SimpleContainer oreFilter = this.ghostList();
	private long energy;
	private Mode mode = Mode.IDLE;
	private QuarryMode quarryMode = QuarryMode.STRIP_MINE;
	private int width = DEFAULT_SIZE;
	private int length = DEFAULT_SIZE;
	private int height = DEFAULT_HEIGHT;
	private boolean oresOnly;
	private boolean hasTargetY;
	private int targetY;
	private long step;
	private boolean finished;
	private long scanStep;
	private boolean scanComplete;
	private final LongArrayList oreQueue = new LongArrayList();
	private long blocksMined;
	private int cooldown;
	private @Nullable UUID owner;
	private String ownerName = "";
	private QuarryStatus status = QuarryStatus.IDLE;
	/** Last block the drill worked on (drives the animation; synced to clients). */
	private @Nullable BlockPos lastTarget;

	// ---- runtime state (recomputed, not authoritative) ----
	private QuarryUpgrades upgrades = QuarryUpgrades.NONE;
	private @Nullable ItemStack cachedTool;
	private @Nullable MiningPattern cachedPattern;
	private long ticks;
	private boolean syncDirty = true;
	private long lastSyncTick;
	private long energyReceivedThisTick;
	private long windowIn;
	private long windowUsed;
	private int avgInput;
	private int avgUse;
	private boolean outputConnected;
	/** The fake player standing in for the owner during protection checks (created lazily, once per tick). */
	private @Nullable ServerPlayer actorThisTick;
	/** True while this quarry holds chunk tickets (Chunk Loader Upgrade, running, powered, allowed by the config). */
	private boolean chunkLoadingActive;
	/** Whether this tick's chunk-loading upkeep was paid (needed to load the chunks being mined). */
	private boolean upkeepPaid;
	/** Area limit the current width/length were last checked against (-1 = not yet since loading). */
	private int appliedMaxArea = -1;
	/** Frame registered for collision (see {@link QuarryFrames}); compared to avoid re-registering every tick. */
	private @Nullable MiningPattern framePattern;
	private @Nullable Direction frameDirection;
	private final RateWindow mineRate = new RateWindow(10);

	// ---- client animation state (client only) ----
	public float clientHeadX = Float.NaN;
	public float clientHeadY;
	public float clientHeadZ;
	public long clientLastFrameNanos;
	public float clientDrillAngle;

	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			QuarryBlockEntity q = QuarryBlockEntity.this;
			long cap = q.getEnergyCapacity();
			MiningPattern pattern = q.pattern();
			return switch (index) {
				case 0 -> (int) (q.energy & 0xFFFF);
				case 1 -> (int) ((q.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (cap & 0xFFFF);
				case 3 -> (int) ((cap >>> 16) & 0xFFFF);
				case 4 -> q.status.ordinal();
				case 5 -> q.progressPermille();
				case 6 -> q.cursorCell().lateral();
				case 7 -> q.cursorCell().forward();
				case 8 -> q.currentY();
				case 9 -> q.effectiveTargetY();
				case 10 -> q.width;
				case 11 -> q.length;
				case 12 -> q.height;
				case 13 -> q.quarryMode.ordinal();
				case 14 -> q.mode.ordinal();
				case 15 -> (int) (q.blocksMined & 0xFFFF);
				case 16 -> (int) ((q.blocksMined >>> 16) & 0xFFFF);
				case 17 -> (q.upgrades.voiding() ? 1 : 0) | (q.oresOnly ? 2 : 0) | (q.outputConnected ? 4 : 0)
					| (q.upgrades.silkTouch() ? 8 : 0) | (q.scanComplete ? 16 : 0) | (q.upgrades.chunkLoader() ? 32 : 0)
					| (q.chunkLoadingActive ? 64 : 0) | (SolarQuarryConfig.get().allowChunkLoading() ? 0 : 128);
				case 18 -> q.level != null ? q.level.getMinY() : -64;
				case 19 -> q.upgrades.maxAreaSize();
				case 20 -> q.upgrades.blocksPerMinute();
				case 21 -> (int) Math.min(q.upgrades.typicalEnergyPerBlock(), 0x7FFF);
				case 22 -> Math.min(q.avgInput, 0x7FFF);
				case 23 -> Math.min(q.avgUse, 0x7FFF);
				case 24 -> Math.min(q.oreQueue.size(), 0x7FFF);
				case 25 -> q.level != null ? q.level.getMaxY() : 319;
				case 26 -> (int) Math.min(q.upgrades.typicalPowerDraw(), 0x7FFF);
				case 27 -> (int) Math.min(Math.round(q.mineRate.perSecond() * 100.0), 0x7FFF);
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Values are only ever written on the client copy of the menu.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public QuarryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.QUARRY, pos, state);
	}

	private SimpleContainer ghostList() {
		return new SimpleContainer(FILTER_SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				QuarryBlockEntity.this.onListChanged();
			}
		};
	}

	private void onListChanged() {
		this.setChanged();
		this.syncDirty = true;
	}

	private static int[] createRange(int size) {
		int[] result = new int[size];
		for (int i = 0; i < size; i++) {
			result[i] = i;
		}

		return result;
	}

	// =====================================================================================
	// Ticking
	// =====================================================================================

	public static void serverTick(Level level, BlockPos pos, BlockState state, QuarryBlockEntity quarry) {
		if (level instanceof ServerLevel serverLevel) {
			quarry.tickServer(serverLevel, state);
		}
	}

	private void tickServer(ServerLevel level, BlockState state) {
		this.ticks++;
		this.energyReceivedThisTick = 0;
		this.refreshUpgrades();
		this.updateChunkLoading(level);
		this.refreshFrame();
		if (this.ticks % 20 == 0) {
			this.mineRate.advance();
		}

		QuarryStatus previous = this.status;
		QuarryStatus next;
		if (this.mode == Mode.IDLE) {
			next = QuarryStatus.IDLE;
		} else if (this.finished) {
			next = QuarryStatus.COMPLETE;
		} else if (this.mode == Mode.PAUSED) {
			next = QuarryStatus.PAUSED;
		} else {
			next = this.runMining(level);
		}

		if (this.ticks % 5 == 0) {
			this.pushOutput(level);
		}

		if (this.ticks % 20 == 0) {
			this.avgInput = (int) Math.min(Integer.MAX_VALUE, this.windowIn / 20);
			this.avgUse = (int) Math.min(Integer.MAX_VALUE, this.windowUsed / 20);
			this.windowIn = 0;
			this.windowUsed = 0;
		}

		if (next != previous) {
			this.status = next;
			this.onStatusChanged(level, state, previous, next);
		}

		if (this.status.isWorking()) {
			if (this.ticks % 40 == 0) {
				BlockPos head = this.lastTarget != null ? this.lastTarget : this.worldPosition;
				level.playSound(null, head.getX() + 0.5, head.getY() + 0.5, head.getZ() + 0.5, ModSounds.QUARRY_DRILL, SoundSource.BLOCKS, ModSounds.volume(0.55F), 0.9F + level.getRandom().nextFloat() * 0.2F);
			}

			if (this.ticks % 20 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1, this.worldPosition.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.02);
			}
		} else if (this.status.isProblem() && this.ticks % 60 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.05, this.worldPosition.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.01);
		}

		if (this.syncDirty && this.ticks - this.lastSyncTick >= 10) {
			this.lastSyncTick = this.ticks;
			this.syncDirty = false;
			level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
		}

		this.actorThisTick = null;
	}

	/** Recomputes the upgrade snapshot and adapts the area when the range limit changes (upgrades or server config). */
	private void refreshUpgrades() {
		QuarryUpgrades newUpgrades = QuarryUpgrades.of(this, BUFFER_SLOTS, UPGRADE_SLOTS);
		if (!newUpgrades.equals(this.upgrades)) {
			this.upgrades = newUpgrades;
			this.cachedTool = null;
			this.syncDirty = true;
		}

		int newMax = this.upgrades.maxAreaSize();
		if (newMax == this.appliedMaxArea || this.level == null || this.level.isClientSide()) {
			return;
		}

		int oldMax = this.appliedMaxArea;
		this.appliedMaxArea = newMax;
		boolean followMax = oldMax > 0 && this.width == oldMax && this.length == oldMax;
		int newWidth = followMax ? newMax : Math.min(this.width, newMax);
		int newLength = followMax ? newMax : Math.min(this.length, this.maxLength(newMax));
		if (newWidth != this.width || newLength != this.length) {
			this.width = newWidth;
			this.length = newLength;
			this.resetProgress();
		}
	}

	/**
	 * Chunk Loader Upgrade: while the quarry is running (not paused or finished), the upgrade is installed and chunk
	 * loading is allowed by the server config, the quarry keeps its own 3 x 3 chunks ticking, so the panels and batteries
	 * there keep charging it even while it waits for power. Loading the chunks it mines costs the upkeep each tick and
	 * only happens while the upkeep can be paid. Tickets time out after 5 seconds, so they vanish on their own when any
	 * condition fails.
	 */
	private void updateChunkLoading(ServerLevel level) {
		SolarQuarryConfig config = SolarQuarryConfig.get();
		boolean active = this.upgrades.chunkLoader() && config.allowChunkLoading() && this.mode == Mode.RUNNING && !this.finished;
		boolean paid = false;
		if (active) {
			long upkeep = config.chunkLoaderUpkeep();
			if (this.energy >= upkeep) {
				this.useEnergy(upkeep);
				paid = true;
			}

			if (this.ticks % 20 == 0 || !this.chunkLoadingActive) {
				QuarryChunkLoading.keepMachineLoaded(level, this.worldPosition);
				if (paid && this.lastTarget != null) {
					QuarryChunkLoading.loadTarget(level, this.lastTarget);
				}

				QuarryChunkLoading.register(level, this.worldPosition);
			}
		}

		this.upkeepPaid = paid;
		if (active != this.chunkLoadingActive) {
			this.chunkLoadingActive = active;
			this.syncDirty = true;
			if (!active) {
				QuarryChunkLoading.unregister(level, this.worldPosition);
			}
		}
	}

	/** The next block is in an unloaded chunk: request it if chunk loading is active, and wait either way. */
	private StepResult waitForChunk(ServerLevel level, BlockPos pos) {
		if (this.chunkLoadingActive && this.upkeepPaid) {
			QuarryChunkLoading.loadTarget(level, pos);
		}

		return StepResult.UNLOADED;
	}

	public boolean isChunkLoadingActive() {
		return this.chunkLoadingActive;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level instanceof ServerLevel serverLevel) {
			QuarryChunkLoading.unregister(serverLevel, pos);
		}

		super.preRemoveSideEffects(pos, state);
	}

	private void onStatusChanged(ServerLevel level, BlockState state, QuarryStatus previous, QuarryStatus next) {
		QuarryLamp lamp = QuarryLamp.forStatus(next);
		if (state.hasProperty(QuarryBlock.LAMP) && state.getValue(QuarryBlock.LAMP) != lamp) {
			level.setBlock(this.worldPosition, state.setValue(QuarryBlock.LAMP, lamp), Block.UPDATE_CLIENTS);
		}

		if (next.isWorking() && !previous.isWorking()) {
			level.playSound(null, this.worldPosition, ModSounds.MACHINE_START, SoundSource.BLOCKS, ModSounds.volume(0.7F), 1.0F);
		} else if (previous.isWorking() && (next.isProblem() || next == QuarryStatus.COMPLETE)) {
			level.playSound(null, this.worldPosition, ModSounds.POWER_DOWN, SoundSource.BLOCKS, ModSounds.volume(0.6F), next == QuarryStatus.COMPLETE ? 1.2F : 1.0F);
		}

		this.syncDirty = true;
		this.lastSyncTick = 0;
		this.setChanged();
	}

	private QuarryStatus runMining(ServerLevel level) {
		if (this.cooldown > 0) {
			this.cooldown--;
			return this.status.isWorking() ? this.status : QuarryStatus.RUNNING;
		}

		StepResult result = StepResult.SCANNING;
		int ops = this.upgrades.blocksPerOperation();
		for (int i = 0; i < ops; i++) {
			result = this.mineNext(level);
			if (result != StepResult.MINED) {
				break;
			}
		}

		return switch (result) {
			case MINED, SKIPPED -> {
				this.cooldown = this.upgrades.ticksPerOperation() - 1;
				this.syncDirty = true;
				yield QuarryStatus.RUNNING;
			}
			case SCANNING -> {
				this.syncDirty = true;
				yield this.quarryMode == QuarryMode.ORE_PRIORITY && !this.scanComplete ? QuarryStatus.SCANNING : QuarryStatus.RUNNING;
			}
			case NO_ENERGY -> QuarryStatus.WAITING_FOR_POWER;
			case FULL -> QuarryStatus.OUTPUT_BLOCKED;
			case UNLOADED -> QuarryStatus.WAITING_FOR_CHUNK;
			case FINISHED -> {
				this.finished = true;
				this.setChanged();
				yield QuarryStatus.COMPLETE;
			}
		};
	}

	private StepResult mineNext(ServerLevel level) {
		if (this.quarryMode == QuarryMode.ORE_PRIORITY) {
			StepResult ore = this.mineOres(level);
			if (ore != null) {
				return ore;
			}

			if (this.oresOnly) {
				return StepResult.FINISHED;
			}
		}

		MiningPattern pattern = this.pattern();
		for (int scanned = 0; scanned < SCAN_BUDGET; scanned++) {
			if (pattern.isComplete(this.step)) {
				return StepResult.FINISHED;
			}

			BlockPos pos = this.positionOf(pattern, this.step);
			StepResult result = this.tryMine(level, pos);
			if (result == StepResult.SKIPPED) {
				this.step++;
				continue;
			}

			if (result == StepResult.MINED) {
				this.step++;
			}

			return result;
		}

		return StepResult.SCANNING;
	}

	/**
	 * Ore priority: first drains the queue of known ore positions, then prospects further.
	 * @return a result while ore work remains, or null once the whole volume has been scanned and every ore mined
	 */
	private @Nullable StepResult mineOres(ServerLevel level) {
		int budget = SCAN_BUDGET;
		while (!this.oreQueue.isEmpty() && budget-- > 0) {
			BlockPos pos = BlockPos.of(this.oreQueue.getLong(0));
			if (!level.isLoaded(pos)) {
				return this.waitForChunk(level, pos);
			}

			BlockState state = level.getBlockState(pos);
			if (!this.isPriorityOre(state)) {
				this.oreQueue.removeLong(0);
				continue;
			}

			StepResult result = this.tryMine(level, pos);
			if (result == StepResult.SKIPPED || result == StepResult.MINED) {
				this.oreQueue.removeLong(0);
				this.setChanged();
				if (result == StepResult.MINED) {
					return result;
				}

				continue;
			}

			return result;
		}

		if (!this.oreQueue.isEmpty()) {
			return StepResult.SCANNING;
		}

		if (this.scanComplete) {
			return null;
		}

		MiningPattern pattern = this.pattern();
		int scanned = 0;
		while (scanned < ORE_SCAN_BUDGET && this.oreQueue.size() < MAX_ORE_QUEUE) {
			if (pattern.isComplete(this.scanStep)) {
				this.scanComplete = true;
				this.setChanged();
				return this.oreQueue.isEmpty() ? null : StepResult.SCANNING;
			}

			BlockPos pos = this.positionOf(pattern, this.scanStep);
			if (!level.isLoaded(pos)) {
				return this.waitForChunk(level, pos);
			}

			BlockState state = level.getBlockState(pos);
			if (this.isPriorityOre(state) && !this.shouldSkip(level, pos, state)) {
				this.oreQueue.add(pos.asLong());
			}

			this.scanStep++;
			scanned++;
		}

		this.setChanged();
		return StepResult.SCANNING;
	}

	/** Ores the ore-priority mode looks for: the ore list if it has entries, otherwise anything tagged c:ores. */
	public boolean isPriorityOre(BlockState state) {
		if (state.isAir()) {
			return false;
		}

		boolean listed = false;
		boolean anyEntry = false;
		Item item = state.getBlock().asItem();
		for (int i = 0; i < FILTER_SLOTS; i++) {
			ItemStack entry = this.oreFilter.getItem(i);
			if (!entry.isEmpty()) {
				anyEntry = true;
				if (item != Items.AIR && entry.is(item)) {
					listed = true;
					break;
				}
			}
		}

		return anyEntry ? listed : state.is(ConventionalBlockTags.ORES);
	}

	/** Mines one block if possible. Never advances any cursor itself. */
	private StepResult tryMine(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return this.waitForChunk(level, pos);
		}

		BlockState target = level.getBlockState(pos);
		if (this.shouldSkip(level, pos, target)) {
			return StepResult.SKIPPED;
		}

		BlockEntity targetEntity = target.hasBlockEntity() ? level.getBlockEntity(pos) : null;
		if (!QuarryProtection.mayBreak(level, this.actor(level), pos, target, targetEntity)) {
			return StepResult.SKIPPED;
		}

		if (target.getBlock() instanceof LiquidBlock) {
			if (this.energy < FLUID_COST) {
				return StepResult.NO_ENERGY;
			}

			this.useEnergy(FLUID_COST);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			this.lastTarget = pos.immutable();
			this.setChanged();
			return StepResult.MINED;
		}

		float hardness = target.getDestroySpeed(level, pos);
		long cost = this.upgrades.energyPerBlock(hardness);
		if (this.energy < cost) {
			return StepResult.NO_ENERGY;
		}

		ItemStack tool = this.getMiningTool(level);
		List<ItemStack> drops = Block.getDrops(target, level, pos, targetEntity, null, tool);
		List<ItemStack> kept = new ArrayList<>(drops.size());
		for (ItemStack drop : drops) {
			if (!drop.isEmpty() && !this.shouldVoid(drop)) {
				kept.add(drop);
			}
		}

		if (!this.canFitAll(kept)) {
			return StepResult.FULL;
		}

		this.useEnergy(cost);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		target.spawnAfterBreak(level, pos, tool, false);
		for (ItemStack stack : kept) {
			this.insertIntoBuffer(stack);
		}

		this.blocksMined++;
		this.mineRate.record(1);
		this.lastTarget = pos.immutable();
		if (this.upgrades.ticksPerOperation() >= 4 || this.blocksMined % 4 == 0) {
			level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(target));
		}

		this.setChanged();
		return StepResult.MINED;
	}

	private void useEnergy(long amount) {
		this.energy = Math.max(0, this.energy - amount);
		this.windowUsed += amount;
	}

	/**
	 * Targets the quarry never touches: air, unbreakable blocks, anything outside the world border, our own machines
	 * and cables, data-pack ignored blocks, containers (their contents would spill) and blocks on the skip list.
	 * Protection (spawn protection, claim mods) is checked separately, right before a block is broken.
	 */
	public boolean shouldSkip(Level level, BlockPos pos, BlockState state) {
		if (state.isAir() || pos.equals(this.worldPosition)) {
			return true;
		}

		if (!level.getWorldBorder().isWithinBounds(pos)) {
			return true;
		}

		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return true;
		}

		Block block = state.getBlock();
		if (SolarQuarry.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
			return true;
		}

		if (state.is(ModTags.QUARRY_IGNORED)) {
			return true;
		}

		if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container) {
			return true;
		}

		return this.isListed(this.skipFilter, block.asItem());
	}

	/** The fake player that stands in for the owner (see {@link QuarryProtection}). */
	private ServerPlayer actor(ServerLevel level) {
		if (this.actorThisTick == null) {
			this.actorThisTick = QuarryProtection.actor(level, this.owner, this.ownerName);
		}

		return this.actorThisTick;
	}

	private boolean isListed(Container list, Item item) {
		if (item == Items.AIR) {
			return false;
		}

		for (int i = 0; i < list.getContainerSize(); i++) {
			ItemStack entry = list.getItem(i);
			if (!entry.isEmpty() && entry.is(item)) {
				return true;
			}
		}

		return false;
	}

	private ItemStack getMiningTool(ServerLevel level) {
		if (this.cachedTool == null) {
			ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
			var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			if (this.upgrades.silkTouch()) {
				tool.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
			} else if (this.upgrades.fortune() > 0) {
				tool.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), this.upgrades.fortune());
			}

			this.cachedTool = tool;
		}

		return this.cachedTool;
	}

	private boolean shouldVoid(ItemStack stack) {
		return this.upgrades.voiding() && this.isListed(this.voidFilter, stack.getItem());
	}

	// =====================================================================================
	// Geometry
	// =====================================================================================

	public Direction getFacing() {
		BlockState state = this.getBlockState();
		return state.hasProperty(QuarryBlock.FACING) ? state.getValue(QuarryBlock.FACING) : Direction.NORTH;
	}

	/** Direction from the quarry into the work volume (behind the machine). */
	public Direction getAreaDirection() {
		return this.getFacing().getOpposite();
	}

	/** The work volume for the current settings (cached until a setting changes). */
	public MiningPattern pattern() {
		if (this.cachedPattern == null) {
			int top = this.level != null ? this.level.getMaxY() : 319;
			this.cachedPattern = MiningPattern.of(this.quarryMode, this.width, this.length, this.height, this.worldPosition.getY(), this.effectiveTargetY(), top);
		}

		return this.cachedPattern;
	}

	private void invalidatePattern() {
		this.cachedPattern = null;
	}

	public BlockPos positionOf(MiningPattern pattern, long index) {
		return this.toWorld(pattern.cell(index), pattern);
	}

	public BlockPos toWorld(MiningPattern.Cell cell, MiningPattern pattern) {
		Direction forward = this.getAreaDirection();
		Direction lateral = forward.getClockWise();
		return this.worldPosition
			.relative(forward, cell.forward())
			.relative(lateral, pattern.lateralOffset(cell.lateral()))
			.above(cell.dy());
	}

	/** The cell the machine is currently working toward (for display). */
	public MiningPattern.Cell cursorCell() {
		MiningPattern pattern = this.pattern();
		long index = this.quarryMode == QuarryMode.ORE_PRIORITY && !this.scanComplete ? this.scanStep : this.step;
		return pattern.cell(Math.min(index, Math.max(0, pattern.total() - 1)));
	}

	public BlockPos cursorPos() {
		return this.toWorld(this.cursorCell(), this.pattern());
	}

	public @Nullable BlockPos getLastTarget() {
		return this.lastTarget;
	}

	public int currentY() {
		if (this.lastTarget != null && this.status.isWorking()) {
			return this.lastTarget.getY();
		}

		return this.worldPosition.getY() + this.cursorCell().dy();
	}

	public int effectiveTargetY() {
		int min = this.level != null ? this.level.getMinY() : -64;
		int start = this.worldPosition.getY();
		int target = this.hasTargetY ? this.targetY : min;
		return Math.max(min, Math.min(start, target));
	}

	public int progressPermille() {
		if (this.finished) {
			return 1000;
		}

		MiningPattern pattern = this.pattern();
		if (this.quarryMode == QuarryMode.ORE_PRIORITY) {
			// Scanning is the first half of the job; strip mining the rest (if enabled) the second.
			int scan = pattern.permille(this.scanStep);
			if (this.oresOnly) {
				return this.scanComplete && this.oreQueue.isEmpty() ? 1000 : Math.min(999, scan);
			}

			return this.scanComplete ? 500 + pattern.permille(this.step) / 2 : scan / 2;
		}

		return pattern.permille(this.step);
	}

	// =====================================================================================
	// Configuration (server authoritative; every setter validates and clamps)
	// =====================================================================================

	private int maxLength(int maxArea) {
		return this.quarryMode == QuarryMode.TUNNEL_BORE ? maxArea * MiningPattern.TUNNEL_LENGTH_FACTOR : maxArea;
	}

	public int getMaxWidth() {
		return this.upgrades.maxAreaSize();
	}

	public int getMaxLength() {
		return this.maxLength(this.upgrades.maxAreaSize());
	}

	public int getMaxHeight() {
		return this.quarryMode == QuarryMode.TUNNEL_BORE ? MiningPattern.MAX_TUNNEL_HEIGHT : MiningPattern.MAX_CLEAR_HEIGHT;
	}

	public void setTargetY(int y) {
		int min = this.level != null ? this.level.getMinY() : -64;
		int clamped = Math.max(min, Math.min(this.worldPosition.getY(), y));
		if (this.hasTargetY && clamped == this.targetY) {
			return;
		}

		this.targetY = clamped;
		this.hasTargetY = true;
		this.invalidatePattern();
		if (this.quarryMode.usesTargetDepth()) {
			// A deeper target keeps progress (the pattern only grows downward); a shallower one may complete the job.
			this.finished = this.pattern().isComplete(this.step) && (this.quarryMode != QuarryMode.ORE_PRIORITY || this.scanComplete);
			if (this.quarryMode == QuarryMode.ORE_PRIORITY && !this.pattern().isComplete(this.scanStep)) {
				this.scanComplete = false;
			}
		}

		this.markConfigChanged();
	}

	public void setQuarryMode(QuarryMode newMode) {
		if (newMode == this.quarryMode) {
			return;
		}

		this.quarryMode = newMode;
		this.length = Math.min(this.length, this.getMaxLength());
		this.height = Math.max(1, Math.min(this.height, this.getMaxHeight()));
		this.resetProgress();
	}

	public void setWidth(int value) {
		int clamped = Math.max(1, Math.min(this.getMaxWidth(), value));
		if (clamped != this.width) {
			this.width = clamped;
			this.resetProgress();
		}
	}

	public void setLength(int value) {
		int clamped = Math.max(1, Math.min(this.getMaxLength(), value));
		if (clamped != this.length) {
			this.length = clamped;
			this.resetProgress();
		}
	}

	public void setHeight(int value) {
		int clamped = Math.max(1, Math.min(this.getMaxHeight(), value));
		if (clamped != this.height) {
			this.height = clamped;
			this.resetProgress();
		}
	}

	public void setOresOnly(boolean value) {
		if (value != this.oresOnly) {
			this.oresOnly = value;
			if (this.finished && !value && this.quarryMode == QuarryMode.ORE_PRIORITY) {
				this.finished = false;
			}

			this.markConfigChanged();
		}
	}

	/** Restarts the scan from the first cell of the work volume. Mined blocks are already air and are skipped quickly. */
	public void resetProgress() {
		this.step = 0;
		this.scanStep = 0;
		this.scanComplete = false;
		this.oreQueue.clear();
		this.finished = false;
		this.cooldown = 0;
		this.invalidatePattern();
		this.markConfigChanged();
	}

	private void markConfigChanged() {
		this.invalidatePattern();
		this.syncDirty = true;
		this.lastSyncTick = 0;
		this.setChanged();
	}

	// =====================================================================================
	// Controls (server authoritative)
	// =====================================================================================

	public void start() {
		if (this.finished) {
			this.resetProgress();
		}

		this.mode = Mode.RUNNING;
		this.syncDirty = true;
		this.setChanged();
	}

	public void pause() {
		if (this.mode == Mode.RUNNING) {
			this.mode = Mode.PAUSED;
			this.syncDirty = true;
			this.setChanged();
		}
	}

	public void resume() {
		if (this.mode == Mode.PAUSED) {
			this.mode = Mode.RUNNING;
			this.syncDirty = true;
			this.setChanged();
		}
	}

	public void setOwner(LivingEntity placer) {
		if (placer instanceof Player player) {
			this.owner = player.getUUID();
			this.ownerName = player.getGameProfile().name();
			this.setChanged();
		}
	}

	/** Sets the owner directly (used by the self-test and when restoring an owner by id). */
	public void setOwner(UUID id, String name) {
		this.owner = id;
		this.ownerName = name == null ? "" : name;
		this.setChanged();
	}

	public @Nullable UUID getOwner() {
		return this.owner;
	}

	public String getOwnerName() {
		return this.ownerName;
	}

	/** Owners and server operators may control the machine remotely. */
	public boolean isAuthorized(ServerPlayer player) {
		return this.owner == null || this.owner.equals(player.getUUID()) || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
	}

	public Mode getMode() {
		return this.mode;
	}

	public QuarryStatus getStatus() {
		return this.status;
	}

	public QuarryMode getQuarryMode() {
		return this.quarryMode;
	}

	public int getWidth() {
		return this.width;
	}

	public int getLength() {
		return this.length;
	}

	public int getHeight() {
		return this.height;
	}

	public boolean isOresOnly() {
		return this.oresOnly;
	}

	public long getStep() {
		return this.step;
	}

	public long getScanStep() {
		return this.scanStep;
	}

	public int getOreQueueSize() {
		return this.oreQueue.size();
	}

	public long getBlocksMined() {
		return this.blocksMined;
	}

	public boolean isFinished() {
		return this.finished;
	}

	public boolean isOutputConnected() {
		return this.outputConnected;
	}

	public QuarryUpgrades getUpgrades() {
		return this.upgrades;
	}

	public int getAverageInput() {
		return this.avgInput;
	}

	public int getAverageUse() {
		return this.avgUse;
	}

	public SimpleContainer getVoidFilter() {
		return this.voidFilter;
	}

	public SimpleContainer getSkipFilter() {
		return this.skipFilter;
	}

	public SimpleContainer getOreFilter() {
		return this.oreFilter;
	}

	public SimpleContainer getList(ListKind kind) {
		return switch (kind) {
			case VOID -> this.voidFilter;
			case SKIP -> this.skipFilter;
			case ORES -> this.oreFilter;
		};
	}

	public ContainerData getDataAccess() {
		return this.dataAccess;
	}

	/** Number of non-empty buffer slots. */
	public int getUsedBufferSlots() {
		int filled = 0;
		for (int i = 0; i < BUFFER_SLOTS; i++) {
			if (!this.items.get(i).isEmpty()) {
				filled++;
			}
		}

		return filled;
	}

	public int getComparatorOutput() {
		int filled = this.getUsedBufferSlots();
		return filled == 0 ? 0 : 1 + filled * 14 / BUFFER_SLOTS;
	}

	// =====================================================================================
	// Item buffer
	// =====================================================================================

	private boolean canFitAll(List<ItemStack> stacks) {
		if (stacks.isEmpty()) {
			return true;
		}

		List<ItemStack> simulated = new ArrayList<>(BUFFER_SLOTS);
		for (int i = 0; i < BUFFER_SLOTS; i++) {
			simulated.add(this.items.get(i).copy());
		}

		for (ItemStack stack : stacks) {
			ItemStack remaining = stack.copy();
			mergeInto(simulated, remaining);
			if (!remaining.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	private void insertIntoBuffer(ItemStack stack) {
		ItemStack remaining = stack.copy();
		List<ItemStack> view = this.items.subList(0, BUFFER_SLOTS);
		mergeInto(view, remaining);
		if (!remaining.isEmpty() && this.level != null) {
			// canFitAll() guarantees room; never delete items if that invariant is ever broken.
			Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, remaining);
		}
	}

	/** Merges {@code stack} into the list (existing stacks first, then empty slots); the stack is shrunk in place. */
	private static void mergeInto(List<ItemStack> slots, ItemStack stack) {
		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			ItemStack existing = slots.get(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				if (move > 0) {
					existing.grow(move);
					stack.shrink(move);
				}
			}
		}

		for (int i = 0; i < slots.size() && !stack.isEmpty(); i++) {
			if (slots.get(i).isEmpty()) {
				int move = Math.min(stack.getCount(), stack.getMaxStackSize());
				slots.set(i, stack.split(move));
			}
		}
	}

	/** Pushes buffered items into adjacent item pipes or inventories. */
	private void pushOutput(ServerLevel level) {
		boolean connected = false;
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = this.worldPosition.relative(dir);
			if (!level.isLoaded(neighbor)) {
				continue;
			}

			BlockEntity be = level.getBlockEntity(neighbor);
			if (be instanceof ItemPipeBlockEntity pipe) {
				connected = true;
				this.pushIntoPipe(pipe, dir.getOpposite());
				continue;
			}

			if (be instanceof QuarryBlockEntity) {
				continue;
			}

			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, neighbor, dir.getOpposite());
			if (storage != null && storage.supportsInsertion()) {
				connected = true;
				this.pushIntoStorage(storage);
			}
		}

		if (connected != this.outputConnected) {
			this.outputConnected = connected;
			this.syncDirty = true;
		}
	}

	private void pushIntoPipe(ItemPipeBlockEntity pipe, Direction entryFace) {
		for (int i = 0; i < BUFFER_SLOTS; i++) {
			ItemStack stack = this.items.get(i);
			if (stack.isEmpty()) {
				continue;
			}

			ItemStack batch = stack.copyWithCount(Math.min(stack.getCount(), ItemPipeBlockEntity.MAX_BATCH));
			if (pipe.acceptFromSource(batch, entryFace)) {
				stack.shrink(batch.getCount());
				this.setChanged();
				return; // one batch per pipe per push keeps the network flowing steadily
			}
		}
	}

	private void pushIntoStorage(Storage<ItemVariant> storage) {
		int budget = 64;
		for (int i = 0; i < BUFFER_SLOTS && budget > 0; i++) {
			ItemStack stack = this.items.get(i);
			if (stack.isEmpty()) {
				continue;
			}

			try (Transaction tx = Transaction.openOuter()) {
				long inserted = storage.insert(ItemVariant.of(stack), Math.min(stack.getCount(), budget), tx);
				if (inserted > 0) {
					tx.commit();
					stack.shrink((int) inserted);
					budget -= (int) inserted;
					this.setChanged();
				}
			}
		}
	}

	// =====================================================================================
	// Container / WorldlyContainer (exposes only the output buffer to automation)
	// =====================================================================================

	@Override
	public int getContainerSize() {
		return TOTAL_SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}

		return true;
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
	public void setItem(int slot, ItemStack itemStack) {
		if (slot < 0 || slot >= TOTAL_SLOTS) {
			return;
		}

		this.items.set(slot, itemStack);
		if (slot >= BUFFER_SLOTS) {
			int max = this.getMaxStackSize(itemStack);
			if (itemStack.getCount() > max) {
				itemStack.setCount(max);
			}
		}

		this.setChanged();
	}

	@Override
	public int getMaxStackSize(ItemStack itemStack) {
		if (itemStack.getItem() instanceof UpgradeItem upgrade) {
			return Math.min(upgrade.type().maxStack(), itemStack.getMaxStackSize());
		}

		return WorldlyContainer.super.getMaxStackSize(itemStack);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack itemStack) {
		if (slot < BUFFER_SLOTS) {
			return false;
		}

		return this.canInstallUpgrade(slot, itemStack);
	}

	/** Upgrade slots accept one upgrade type each; fortune and silk touch are mutually exclusive. */
	public boolean canInstallUpgrade(int slot, ItemStack itemStack) {
		return canInstallUpgrade(this, slot, itemStack);
	}

	/** Works on the block entity and on the client's mirror container (same slot layout). */
	public static boolean canInstallUpgrade(Container container, int slot, ItemStack itemStack) {
		if (!(itemStack.getItem() instanceof UpgradeItem upgrade)) {
			return false;
		}

		for (int i = BUFFER_SLOTS; i < TOTAL_SLOTS && i < container.getContainerSize(); i++) {
			if (i == slot) {
				continue;
			}

			ItemStack other = container.getItem(i);
			if (other.getItem() instanceof UpgradeItem installed) {
				if (installed.type() == upgrade.type()) {
					return false;
				}

				if (installed.type() == UpgradeType.SILK_TOUCH && upgrade.type() == UpgradeType.FORTUNE
					|| installed.type() == UpgradeType.FORTUNE && upgrade.type() == UpgradeType.SILK_TOUCH) {
					return false;
				}
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
		this.setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return BUFFER_SLOT_IDS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, @Nullable Direction direction) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
		return slot < BUFFER_SLOTS;
	}

	// =====================================================================================
	// Energy
	// =====================================================================================

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
		return this.upgrades.energyCapacity();
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		long room = Math.max(0, this.getEnergyCapacity() - this.energy);
		long rate = Math.max(0, this.upgrades.maxInput() - this.energyReceivedThisTick);
		long accepted = Math.max(0, Math.min(amount, Math.min(room, rate)));
		if (!simulate && accepted > 0) {
			this.energy += accepted;
			this.energyReceivedThisTick += accepted;
			this.windowIn += accepted;
			this.setChanged();
		}

		return accepted;
	}

	@Override
	public long extractEnergy(long amount, boolean simulate) {
		return 0; // quarries only consume
	}

	@Override
	public void restoreEnergy(long amount) {
		this.energy += Math.max(0, amount);
		this.setChanged();
	}

	// =====================================================================================
	// Menu
	// =====================================================================================

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.quarry");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new QuarryMenu(containerId, inventory, this);
	}

	// =====================================================================================
	// Persistence
	// =====================================================================================

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		if (input.list("Items", ItemStackWithSlot.CODEC).isPresent()) {
			this.items.clear();
			ContainerHelper.loadAllItems(input, this.items);
		}

		loadList(input, "VoidFilter", this.voidFilter);
		loadList(input, "SkipList", this.skipFilter);
		loadList(input, "OreList", this.oreFilter);

		this.energy = Math.max(0, input.getLongOr("Energy", this.energy));
		int modeOrdinal = input.getIntOr("Mode", this.mode.ordinal());
		this.mode = modeOrdinal >= 0 && modeOrdinal < Mode.values().length ? Mode.values()[modeOrdinal] : Mode.IDLE;
		this.finished = input.getBooleanOr("Finished", this.finished);
		this.quarryMode = QuarryMode.byId(input.getStringOr("QuarryMode", this.quarryMode.id()));
		this.hasTargetY = input.getBooleanOr("HasTargetY", this.hasTargetY);
		this.targetY = input.getIntOr("TargetY", this.targetY);
		this.oresOnly = input.getBooleanOr("OresOnly", this.oresOnly);
		this.blocksMined = Math.max(0, input.getLongOr("BlocksMined", this.blocksMined));
		this.cooldown = Math.max(0, Math.min(20, input.getIntOr("Cooldown", this.cooldown)));
		this.status = QuarryStatus.byName(input.getStringOr("Status", this.status.getSerializedName()));
		this.upgrades = QuarryUpgrades.of(this, BUFFER_SLOTS, UPGRADE_SLOTS);
		this.cachedTool = null;

		int maxArea = this.upgrades.maxAreaSize();
		if (input.getInt("Width").isPresent()) {
			this.width = input.getIntOr("Width", DEFAULT_SIZE);
			this.length = input.getIntOr("Length", DEFAULT_SIZE);
			this.height = input.getIntOr("Height", DEFAULT_HEIGHT);
			this.step = input.getLongOr("Step", 0L);
		} else {
			// Data written by version 1.0 (square area, layer/row/col cursor): resume at the start of the current layer.
			int area = input.getIntOr("Area", DEFAULT_SIZE);
			this.width = area;
			this.length = area;
			this.height = DEFAULT_HEIGHT;
			this.step = (long) Math.max(0, input.getIntOr("Layer", 0)) * area * area;
		}

		this.width = Math.max(1, Math.min(maxArea, this.width));
		this.length = Math.max(1, Math.min(this.maxLength(maxArea), this.length));
		this.height = Math.max(1, Math.min(this.getMaxHeight(), this.height));
		this.step = Math.max(0, this.step);
		this.scanStep = Math.max(0, input.getLongOr("ScanStep", 0L));
		this.scanComplete = input.getBooleanOr("ScanComplete", false);
		this.oreQueue.clear();
		input.read("OreQueue", Codec.LONG.listOf()).ifPresent(list -> {
			for (int i = 0; i < list.size() && i < MAX_ORE_QUEUE; i++) {
				this.oreQueue.add(list.get(i).longValue());
			}
		});
		this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(this.owner);
		this.ownerName = input.getStringOr("OwnerName", this.ownerName);
		this.lastTarget = input.getLong("LastTarget").map(BlockPos::of).orElse(null);
		this.invalidatePattern();
		this.refreshFrame();
	}

	/** Whether the frame is up: the machine was started or has made progress (same rule as the renderer). */
	public boolean isFrameVisible() {
		return this.mode != Mode.IDLE || this.step > 0 || this.scanStep > 0;
	}

	/** Registers the frame's solid boxes for collision when it is shown, and removes them when it is not. */
	private void refreshFrame() {
		if (this.level == null || this.isRemoved()) {
			return;
		}

		MiningPattern pattern = this.isFrameVisible() ? this.pattern() : null;
		Direction direction = pattern == null ? null : this.getAreaDirection();
		if (java.util.Objects.equals(pattern, this.framePattern) && direction == this.frameDirection) {
			return;
		}

		this.framePattern = pattern;
		this.frameDirection = direction;
		if (pattern == null) {
			QuarryFrames.remove(this.level, this.worldPosition.asLong());
		} else {
			QuarryFrames.put(this.level, this.worldPosition.asLong(), new QuarryFrame(pattern).worldBoxes(this.worldPosition, direction));
		}
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		this.framePattern = null;
		this.frameDirection = null;
		this.refreshFrame();
	}

	@Override
	public void setRemoved() {
		if (this.level != null) {
			QuarryFrames.remove(this.level, this.worldPosition.asLong());
		}

		this.framePattern = null;
		this.frameDirection = null;
		super.setRemoved();
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		this.refreshFrame();
	}

	/** Blocks actually mined per second over the last 10 seconds (fluids, skips and failures are not counted). */
	public double measuredBlocksPerSecond() {
		return this.mineRate.perSecond();
	}

	private static void loadList(ValueInput input, String name, SimpleContainer target) {
		input.child(name).ifPresent(child -> {
			NonNullList<ItemStack> list = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(child, list);
			for (int i = 0; i < FILTER_SLOTS; i++) {
				target.setItem(i, list.get(i));
			}
		});
	}

	private static void saveList(ValueOutput output, String name, SimpleContainer source) {
		NonNullList<ItemStack> list = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < FILTER_SLOTS; i++) {
			list.set(i, source.getItem(i));
		}

		ContainerHelper.saveAllItems(output.child(name), list, true);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		saveList(output, "VoidFilter", this.voidFilter);
		saveList(output, "SkipList", this.skipFilter);
		saveList(output, "OreList", this.oreFilter);
		output.putLong("Energy", this.energy);
		this.writeSyncFields(output);
		output.putLong("BlocksMined", this.blocksMined);
		output.putInt("Cooldown", this.cooldown);
		output.putLong("ScanStep", this.scanStep);
		output.putBoolean("ScanComplete", this.scanComplete);
		List<Long> queue = new ArrayList<>(this.oreQueue.size());
		for (int i = 0; i < this.oreQueue.size(); i++) {
			queue.add(this.oreQueue.getLong(i));
		}

		output.store("OreQueue", Codec.LONG.listOf(), queue);
		output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
		output.putString("OwnerName", this.ownerName);
	}

	/** Fields clients need for the renderer and GUI; also part of the saved data. */
	private void writeSyncFields(ValueOutput output) {
		output.putInt("Mode", this.mode.ordinal());
		output.putString("QuarryMode", this.quarryMode.id());
		output.putInt("Width", this.width);
		output.putInt("Length", this.length);
		output.putInt("Height", this.height);
		output.putBoolean("OresOnly", this.oresOnly);
		output.putLong("Step", this.step);
		output.putBoolean("HasTargetY", this.hasTargetY);
		output.putInt("TargetY", this.targetY);
		output.putBoolean("Finished", this.finished);
		output.putString("Status", this.status.getSerializedName());
		if (this.lastTarget != null) {
			output.putLong("LastTarget", this.lastTarget.asLong());
		}
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			this.writeSyncFields(output);
			return output.buildResult();
		}
	}

	// =====================================================================================
	// Presets (configuration cards): settings only, never items, energy or upgrades
	// =====================================================================================

	public static final int PRESET_VERSION = 1;

	public CompoundTag writePreset() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Version", PRESET_VERSION);
		tag.putString("Kind", "quarry");
		tag.putString("QuarryMode", this.quarryMode.id());
		tag.putInt("Width", this.width);
		tag.putInt("Length", this.length);
		tag.putInt("Height", this.height);
		tag.putBoolean("OresOnly", this.oresOnly);
		if (this.hasTargetY) {
			tag.putInt("DepthBelow", Math.max(0, this.worldPosition.getY() - this.effectiveTargetY()));
		}

		tag.put("VoidList", listIds(this.voidFilter));
		tag.put("SkipList", listIds(this.skipFilter));
		tag.put("OreList", listIds(this.oreFilter));
		return tag;
	}

	/**
	 * Applies a preset. Unknown or out-of-range values are replaced or clamped; items, energy and upgrades are untouched.
	 * @return a message describing what happened
	 */
	public Component applyPreset(CompoundTag tag) {
		int version = tag.getIntOr("Version", 0);
		if (!"quarry".equals(tag.getStringOr("Kind", ""))) {
			return Component.translatable("solarquarry.card.wrong_kind");
		}

		if (version > PRESET_VERSION) {
			return Component.translatable("solarquarry.card.too_new", version);
		}

		String modeId = tag.getStringOr("QuarryMode", QuarryMode.STRIP_MINE.id());
		QuarryMode newMode = QuarryMode.byId(modeId);
		boolean unknownMode = !newMode.id().equals(modeId);
		this.setQuarryMode(newMode);
		this.setWidth(tag.getIntOr("Width", DEFAULT_SIZE));
		this.setLength(tag.getIntOr("Length", DEFAULT_SIZE));
		this.setHeight(tag.getIntOr("Height", DEFAULT_HEIGHT));
		this.setOresOnly(tag.getBooleanOr("OresOnly", false));
		if (tag.contains("DepthBelow")) {
			this.setTargetY(this.worldPosition.getY() - Math.max(0, tag.getIntOr("DepthBelow", 0)));
		} else {
			this.hasTargetY = false;
			this.markConfigChanged();
		}

		int skipped = readIds(tag, "VoidList", this.voidFilter) + readIds(tag, "SkipList", this.skipFilter) + readIds(tag, "OreList", this.oreFilter);
		Component summary = Component.translatable("solarquarry.card.applied_quarry", Component.translatable(this.quarryMode.translationKey()), this.width, this.length);
		if (unknownMode || skipped > 0) {
			return summary.copy().append(" ").append(Component.translatable("solarquarry.card.partial", skipped + (unknownMode ? 1 : 0)));
		}

		return summary;
	}

	static net.minecraft.nbt.ListTag listIds(SimpleContainer container) {
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			list.add(net.minecraft.nbt.StringTag.valueOf(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
		}

		return list;
	}

	/** @return how many entries were invalid and skipped */
	static int readIds(CompoundTag tag, String key, SimpleContainer container) {
		net.minecraft.nbt.ListTag list = tag.getListOrEmpty(key);
		int skipped = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			String id = i < list.size() ? list.getStringOr(i, "") : "";
			ItemStack value = ItemStack.EMPTY;
			if (!id.isEmpty()) {
				net.minecraft.resources.Identifier parsed = net.minecraft.resources.Identifier.tryParse(id);
				Item item = parsed != null ? BuiltInRegistries.ITEM.getOptional(parsed).orElse(null) : null;
				if (item == null || item == Items.AIR) {
					skipped++;
				} else {
					value = new ItemStack(item);
				}
			}

			container.setItem(i, value);
		}

		return skipped;
	}

	/**
	 * Prepares this quarry to be carried by a wrench: returns its complete state and empties the block entity
	 * so the following block removal cannot drop or duplicate anything.
	 */
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag tag = this.saveCustomOnly(registries);
		this.items.clear();
		this.energy = 0;
		this.mode = Mode.IDLE;
		this.setChanged();
		return tag;
	}
}
