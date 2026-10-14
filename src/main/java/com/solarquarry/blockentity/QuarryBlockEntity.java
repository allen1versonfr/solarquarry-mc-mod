package com.solarquarry.blockentity;

import com.mojang.logging.LogUtils;
import com.solarquarry.block.EnergyCableBlock;
import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.MachineBlock;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.quarry.QuarryLamp;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.quarry.QuarryUpgrades;
import com.solarquarry.quarry.UpgradeType;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModSounds;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
	public static final int DATA_COUNT = 18;
	/** Maximum positions examined per tick while skipping air and unbreakable blocks. */
	private static final int SCAN_BUDGET = 1024;
	private static final long FLUID_COST = 10;
	private static final int[] BUFFER_SLOT_IDS = createRange(BUFFER_SLOTS);

	public enum Mode {
		IDLE,
		RUNNING,
		PAUSED
	}

	private enum StepResult {
		MINED,
		SCANNING,
		NO_ENERGY,
		FULL,
		UNLOADED,
		FINISHED
	}

	// ---- persistent state ----
	private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
	private final SimpleContainer voidFilter = new SimpleContainer(FILTER_SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			QuarryBlockEntity.this.setChanged();
		}
	};
	private long energy;
	private Mode mode = Mode.IDLE;
	private boolean finished;
	private int layer;
	private int row;
	private int col;
	private int areaSize = 16;
	private boolean hasTargetY;
	private int targetY;
	private long blocksMined;
	private int cooldown;

	// ---- runtime state (recomputed, not authoritative) ----
	private QuarryStatus status = QuarryStatus.IDLE;
	private QuarryUpgrades upgrades = QuarryUpgrades.NONE;
	private @Nullable ItemStack cachedTool;
	private long ticks;
	private boolean syncDirty = true;
	private long lastSyncTick;
	private long energyReceivedThisTick;

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
			return switch (index) {
				case 0 -> (int) (q.energy & 0xFFFF);
				case 1 -> (int) ((q.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (cap & 0xFFFF);
				case 3 -> (int) ((cap >>> 16) & 0xFFFF);
				case 4 -> q.status.ordinal();
				case 5 -> q.progressPermille();
				case 6 -> q.col;
				case 7 -> q.row;
				case 8 -> q.currentY();
				case 9 -> q.effectiveTargetY();
				case 10 -> q.areaSize;
				case 11 -> q.upgrades.blocksPerMinute();
				case 12 -> (int) Math.min(q.upgrades.typicalEnergyPerBlock(), 0x7FFF);
				case 13 -> q.mode.ordinal();
				case 14 -> (int) (q.blocksMined & 0xFFFF);
				case 15 -> (int) ((q.blocksMined >>> 16) & 0xFFFF);
				case 16 -> q.upgrades.voiding() ? 1 : 0;
				case 17 -> q.level != null ? q.level.getMinY() : 0;
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
		QuarryUpgrades newUpgrades = QuarryUpgrades.of(this, BUFFER_SLOTS, UPGRADE_SLOTS);
		if (!newUpgrades.equals(this.upgrades)) {
			this.upgrades = newUpgrades;
			this.cachedTool = null;
			this.syncDirty = true;
		}

		int wantedArea = this.upgrades.areaSize();
		if (wantedArea != this.areaSize) {
			// The area changed (range upgrade added or removed): rescan from the top layer.
			this.areaSize = wantedArea;
			this.resetProgress();
		}

		QuarryStatus previous = this.status;
		QuarryStatus next;
		if (this.mode == Mode.IDLE) {
			next = QuarryStatus.IDLE;
		} else if (this.finished) {
			next = QuarryStatus.FINISHED;
		} else if (this.mode == Mode.PAUSED) {
			next = QuarryStatus.PAUSED;
		} else {
			next = this.runMining(level);
		}

		if (this.ticks % 5 == 0) {
			this.pushOutput(level);
		}

		if (next != previous) {
			this.status = next;
			this.onStatusChanged(level, state, previous, next);
		}

		if (this.status == QuarryStatus.RUNNING) {
			if (this.ticks % 40 == 0) {
				BlockPos head = this.cursorPos();
				level.playSound(null, head.getX() + 0.5, head.getY() + 0.5, head.getZ() + 0.5, ModSounds.QUARRY_DRILL, SoundSource.BLOCKS, 0.55F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}

			if (this.ticks % 20 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1, this.worldPosition.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.02);
			}
		}

		if (this.syncDirty && this.ticks - this.lastSyncTick >= 10) {
			this.lastSyncTick = this.ticks;
			this.syncDirty = false;
			level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
		}
	}

	private void onStatusChanged(ServerLevel level, BlockState state, QuarryStatus previous, QuarryStatus next) {
		QuarryLamp lamp = QuarryLamp.forStatus(next);
		if (state.hasProperty(QuarryBlock.LAMP) && state.getValue(QuarryBlock.LAMP) != lamp) {
			level.setBlock(this.worldPosition, state.setValue(QuarryBlock.LAMP, lamp), Block.UPDATE_CLIENTS);
		}

		if (next == QuarryStatus.RUNNING && previous != QuarryStatus.RUNNING) {
			level.playSound(null, this.worldPosition, ModSounds.MACHINE_START, SoundSource.BLOCKS, 0.7F, 1.0F);
		} else if (previous == QuarryStatus.RUNNING && (next == QuarryStatus.NO_ENERGY || next == QuarryStatus.FINISHED || next == QuarryStatus.BUFFER_FULL)) {
			level.playSound(null, this.worldPosition, ModSounds.POWER_DOWN, SoundSource.BLOCKS, 0.6F, next == QuarryStatus.FINISHED ? 1.2F : 1.0F);
		}

		this.syncDirty = true;
		this.lastSyncTick = 0;
		this.setChanged();
	}

	private QuarryStatus runMining(ServerLevel level) {
		if (this.cooldown > 0) {
			this.cooldown--;
			return this.status == QuarryStatus.RUNNING ? QuarryStatus.RUNNING : this.status;
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
			case MINED -> {
				this.cooldown = this.upgrades.ticksPerOperation() - 1;
				this.syncDirty = true;
				yield QuarryStatus.RUNNING;
			}
			case SCANNING -> {
				this.syncDirty = true;
				yield QuarryStatus.RUNNING;
			}
			case NO_ENERGY -> QuarryStatus.NO_ENERGY;
			case FULL -> QuarryStatus.BUFFER_FULL;
			case UNLOADED -> QuarryStatus.BLOCKED;
			case FINISHED -> {
				this.finished = true;
				this.setChanged();
				yield QuarryStatus.FINISHED;
			}
		};
	}

	private StepResult mineNext(ServerLevel level) {
		int bottom = this.effectiveTargetY();
		for (int scanned = 0; scanned < SCAN_BUDGET; scanned++) {
			if (this.currentY() < bottom) {
				return StepResult.FINISHED;
			}

			BlockPos pos = this.cursorPos();
			if (!level.isLoaded(pos)) {
				return StepResult.UNLOADED;
			}

			BlockState target = level.getBlockState(pos);
			if (this.shouldSkip(level, pos, target)) {
				this.advance();
				continue;
			}

			if (target.getBlock() instanceof LiquidBlock) {
				if (this.energy < FLUID_COST) {
					return StepResult.NO_ENERGY;
				}

				this.energy -= FLUID_COST;
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				this.advance();
				this.setChanged();
				return StepResult.MINED;
			}

			float hardness = target.getDestroySpeed(level, pos);
			long cost = this.upgrades.energyPerBlock(hardness);
			if (this.energy < cost) {
				return StepResult.NO_ENERGY;
			}

			ItemStack tool = this.getMiningTool(level);
			BlockEntity targetEntity = target.hasBlockEntity() ? level.getBlockEntity(pos) : null;
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

			this.energy -= cost;
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			target.spawnAfterBreak(level, pos, tool, false);
			for (ItemStack stack : kept) {
				this.insertIntoBuffer(stack);
			}

			this.blocksMined++;
			if (this.upgrades.ticksPerOperation() >= 4 || this.blocksMined % 4 == 0) {
				level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(target));
			}

			this.advance();
			this.setChanged();
			return StepResult.MINED;
		}

		return StepResult.SCANNING;
	}

	private boolean shouldSkip(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || pos.equals(this.worldPosition)) {
			return true;
		}

		if (state.getDestroySpeed(level, pos) < 0.0F) {
			return true; // bedrock, barriers and other unbreakable blocks
		}

		Block block = state.getBlock();
		if (block instanceof MachineBlock || block instanceof EnergyCableBlock || block instanceof ItemPipeBlock) {
			return true; // never eat our own infrastructure
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
		if (!this.upgrades.voiding()) {
			return false;
		}

		for (int i = 0; i < this.voidFilter.getContainerSize(); i++) {
			ItemStack filter = this.voidFilter.getItem(i);
			if (!filter.isEmpty() && filter.is(stack.getItem())) {
				return true;
			}
		}

		return false;
	}

	private void advance() {
		this.col++;
		if (this.col >= this.areaSize) {
			this.col = 0;
			this.row++;
			if (this.row >= this.areaSize) {
				this.row = 0;
				this.layer++;
			}
		}
	}

	public void resetProgress() {
		this.layer = 0;
		this.row = 0;
		this.col = 0;
		this.finished = false;
		this.cooldown = 0;
		this.syncDirty = true;
		this.setChanged();
	}

	// =====================================================================================
	// Geometry
	// =====================================================================================

	public Direction getFacing() {
		BlockState state = this.getBlockState();
		return state.hasProperty(QuarryBlock.FACING) ? state.getValue(QuarryBlock.FACING) : Direction.NORTH;
	}

	/** Direction from the quarry into the mining area (behind the machine). */
	public Direction getAreaDirection() {
		return this.getFacing().getOpposite();
	}

	/** World position of local cell (col,row) at the given layer. */
	public BlockPos cellPos(int cellCol, int cellRow, int cellLayer) {
		Direction forward = this.getAreaDirection();
		Direction lateral = forward.getClockWise();
		int half = this.areaSize / 2;
		return this.worldPosition
			.relative(forward, 1 + cellRow)
			.relative(lateral, cellCol - half)
			.below(cellLayer);
	}

	public BlockPos cursorPos() {
		return this.cellPos(this.col, this.row, this.layer);
	}

	public int currentY() {
		return this.worldPosition.getY() - this.layer;
	}

	public int effectiveTargetY() {
		int min = this.level != null ? this.level.getMinY() : -64;
		int start = this.worldPosition.getY();
		int target = this.hasTargetY ? this.targetY : min;
		return Math.max(min, Math.min(start, target));
	}

	public void setTargetY(int y) {
		int min = this.level != null ? this.level.getMinY() : -64;
		this.targetY = Math.max(min, Math.min(this.worldPosition.getY(), y));
		this.hasTargetY = true;
		if (this.finished && this.currentY() >= this.targetY) {
			this.finished = false;
		}

		this.syncDirty = true;
		this.setChanged();
	}

	public int progressPermille() {
		long perLayer = (long) this.areaSize * this.areaSize;
		long layers = (long) this.worldPosition.getY() - this.effectiveTargetY() + 1;
		long total = Math.max(1, perLayer * layers);
		if (this.finished) {
			return 1000;
		}

		long done = this.layer * perLayer + (long) this.row * this.areaSize + this.col;
		return (int) Math.max(0, Math.min(1000, done * 1000 / total));
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

	public Mode getMode() {
		return this.mode;
	}

	public QuarryStatus getStatus() {
		return this.status;
	}

	public int getAreaSize() {
		return this.areaSize;
	}

	public int getLayer() {
		return this.layer;
	}

	public int getRow() {
		return this.row;
	}

	public int getCol() {
		return this.col;
	}

	public long getBlocksMined() {
		return this.blocksMined;
	}

	public boolean isFinished() {
		return this.finished;
	}

	public SimpleContainer getVoidFilter() {
		return this.voidFilter;
	}

	public ContainerData getDataAccess() {
		return this.dataAccess;
	}

	public int getComparatorOutput() {
		int filled = 0;
		for (int i = 0; i < BUFFER_SLOTS; i++) {
			if (!this.items.get(i).isEmpty()) {
				filled++;
			}
		}

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
		if (!remaining.isEmpty()) {
			// canFitAll() guarantees room; never delete items if that invariant is ever broken.
			if (this.level != null) {
				net.minecraft.world.Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5, remaining);
			}
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
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = this.worldPosition.relative(dir);
			if (!level.isLoaded(neighbor)) {
				continue;
			}

			BlockEntity be = level.getBlockEntity(neighbor);
			if (be instanceof ItemPipeBlockEntity pipe) {
				this.pushIntoPipe(pipe, dir.getOpposite());
				continue;
			}

			if (be instanceof QuarryBlockEntity) {
				continue;
			}

			Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, neighbor, dir.getOpposite());
			if (storage != null && storage.supportsInsertion()) {
				this.pushIntoStorage(storage);
			}
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
	public static boolean canInstallUpgrade(net.minecraft.world.Container container, int slot, ItemStack itemStack) {
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
		return net.minecraft.world.Container.stillValidBlockEntity(this, player);
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

		input.child("VoidFilter").ifPresent(filter -> {
			NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(filter, filterItems);
			for (int i = 0; i < FILTER_SLOTS; i++) {
				this.voidFilter.setItem(i, filterItems.get(i));
			}
		});

		this.energy = Math.max(0, input.getLongOr("Energy", this.energy));
		int modeOrdinal = input.getIntOr("Mode", this.mode.ordinal());
		this.mode = modeOrdinal >= 0 && modeOrdinal < Mode.values().length ? Mode.values()[modeOrdinal] : Mode.IDLE;
		this.finished = input.getBooleanOr("Finished", this.finished);
		this.layer = Math.max(0, input.getIntOr("Layer", this.layer));
		this.areaSize = input.getIntOr("Area", this.areaSize);
		if (this.areaSize != 16 && this.areaSize != 32 && this.areaSize != 64) {
			this.areaSize = 16;
		}

		this.row = Math.max(0, Math.min(this.areaSize - 1, input.getIntOr("Row", this.row)));
		this.col = Math.max(0, Math.min(this.areaSize - 1, input.getIntOr("Col", this.col)));
		this.hasTargetY = input.getBooleanOr("HasTargetY", this.hasTargetY);
		this.targetY = input.getIntOr("TargetY", this.targetY);
		this.blocksMined = Math.max(0, input.getLongOr("BlocksMined", this.blocksMined));
		this.cooldown = Math.max(0, input.getIntOr("Cooldown", this.cooldown));
		this.status = QuarryStatus.byOrdinal(input.getIntOr("Status", this.status.ordinal()));
		this.upgrades = QuarryUpgrades.of(this, BUFFER_SLOTS, UPGRADE_SLOTS);
		this.cachedTool = null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items, true);
		NonNullList<ItemStack> filterItems = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < FILTER_SLOTS; i++) {
			filterItems.set(i, this.voidFilter.getItem(i));
		}

		ContainerHelper.saveAllItems(output.child("VoidFilter"), filterItems, true);
		output.putLong("Energy", this.energy);
		this.writeSyncFields(output);
		output.putBoolean("Finished", this.finished);
		output.putLong("BlocksMined", this.blocksMined);
		output.putInt("Cooldown", this.cooldown);
	}

	private void writeSyncFields(ValueOutput output) {
		output.putInt("Mode", this.mode.ordinal());
		output.putInt("Layer", this.layer);
		output.putInt("Row", this.row);
		output.putInt("Col", this.col);
		output.putInt("Area", this.areaSize);
		output.putBoolean("HasTargetY", this.hasTargetY);
		output.putInt("TargetY", this.targetY);
		output.putInt("Status", this.status.ordinal());
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
			output.putBoolean("Finished", this.finished);
			return output.buildResult();
		}
	}

	/**
	 * Prepares this quarry to be carried by a wrench: returns its complete state and empties the block entity
	 * so the following block removal cannot drop or duplicate anything.
	 */
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag tag = this.saveCustomOnly(registries);
		this.items.clear();
		this.voidFilter.clearContent();
		this.energy = 0;
		this.mode = Mode.IDLE;
		this.setChanged();
		return tag;
	}
}
