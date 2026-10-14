package com.solarquarry.smelting;

import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.registry.ModBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Super Smelter: an electric furnace with three independent lanes (input slot i feeds output slot i + 3).
 * Each lane smelts one item in {@link #COOK_TICKS} ticks (four times a furnace) while drawing {@link #LANE_POWER} E/t;
 * nothing is drawn while idle. Progress pauses (it is not lost) without power or while the output is full.
 * Inputs accept only items with a smelting recipe; automation inserts into inputs from the top and sides and takes
 * results from the bottom.
 */
public class SuperSmelterBlockEntity extends BlockEntity implements WorldlyContainer, EnergyNode, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int LANES = 3;
	public static final int SLOTS = LANES * 2;
	public static final int COOK_TICKS = 50;
	public static final long LANE_POWER = 16;
	public static final long CAPACITY = 64_000;
	public static final long MAX_INPUT = 512;
	public static final int DATA_COUNT = 10;
	private static final int[] INPUT_SLOTS = {0, 1, 2};
	private static final int[] OUTPUT_SLOTS = {3, 4, 5};

	/** What the machine is doing (shown in the GUI and as the front lamp). */
	public enum Status {
		IDLE,
		RUNNING,
		NO_POWER,
		OUTPUT_FULL
	}

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private final int[] progress = new int[LANES];
	private long energy;
	private float experience;
	private Status status = Status.IDLE;
	private long energyReceivedThisTick;
	private long usedLastTick;
	/** Recipe cache per lane: the input it was computed for and the result for one item (EMPTY = no recipe). */
	private final ItemStack[] cachedInput = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private final Smelting.Result[] cachedResult = new Smelting.Result[LANES];

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			SuperSmelterBlockEntity s = SuperSmelterBlockEntity.this;
			return switch (index) {
				case 0 -> (int) (s.energy & 0xFFFF);
				case 1 -> (int) ((s.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (CAPACITY & 0xFFFF);
				case 3 -> (int) ((CAPACITY >>> 16) & 0xFFFF);
				case 4, 5, 6 -> s.progress[index - 4];
				case 7 -> s.status.ordinal();
				case 8 -> (int) Math.min(0x7FFF, s.usedLastTick);
				case 9 -> Math.min(0x7FFF, (int) s.experience);
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

	public SuperSmelterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SUPER_SMELTER, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SuperSmelterBlockEntity smelter) {
		if (level instanceof ServerLevel serverLevel) {
			smelter.tick(serverLevel, state);
		}
	}

	private void tick(ServerLevel level, BlockState state) {
		this.energyReceivedThisTick = 0;
		long used = 0;
		boolean anyWork = false;
		boolean starved = false;
		boolean blocked = false;
		for (int lane = 0; lane < LANES; lane++) {
			ItemStack input = this.items.get(lane);
			Smelting.Result result = this.resultFor(level, lane, input);
			if (result == null) {
				this.progress[lane] = 0;
				continue;
			}

			ItemStack output = this.items.get(lane + LANES);
			if (!canStack(output, result.output())) {
				blocked = true;
				continue;
			}

			if (this.energy < LANE_POWER) {
				starved = true;
				continue;
			}

			this.energy -= LANE_POWER;
			used += LANE_POWER;
			anyWork = true;
			if (++this.progress[lane] >= COOK_TICKS) {
				this.progress[lane] = 0;
				input.shrink(1);
				if (output.isEmpty()) {
					this.items.set(lane + LANES, result.output().copy());
				} else {
					output.grow(result.output().getCount());
				}

				this.experience = Math.min(10_000.0F, this.experience + result.experience());
			}

			this.setChanged();
		}

		this.usedLastTick = used;
		Status next = anyWork ? Status.RUNNING : starved ? Status.NO_POWER : blocked ? Status.OUTPUT_FULL : Status.IDLE;
		if (next != this.status) {
			this.status = next;
			this.setChanged();
			boolean lit = next == Status.RUNNING;
			if (state.hasProperty(SuperSmelterBlock.LIT) && state.getValue(SuperSmelterBlock.LIT) != lit) {
				level.setBlock(this.worldPosition, state.setValue(SuperSmelterBlock.LIT, lit), 3);
			}
		}
	}

	private Smelting.@Nullable Result resultFor(ServerLevel level, int lane, ItemStack input) {
		if (input.isEmpty()) {
			return null;
		}

		if (!ItemStack.isSameItemSameComponents(input, this.cachedInput[lane])) {
			this.cachedInput[lane] = input.copyWithCount(1);
			this.cachedResult[lane] = Smelting.smelt(level, input);
		}

		return this.cachedResult[lane];
	}

	private static boolean canStack(ItemStack output, ItemStack result) {
		if (output.isEmpty()) {
			return true;
		}

		return ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
	}

	public Status getStatus() {
		return this.status;
	}

	public int getProgress(int lane) {
		return this.progress[lane];
	}

	public ContainerData getDataAccess() {
		return this.data;
	}

	/** Gives the stored smelting experience to the player (called when they take results out). */
	public void awardExperience(Player player) {
		int whole = (int) this.experience;
		if (whole > 0 && player instanceof ServerPlayer serverPlayer) {
			this.experience -= whole;
			ExperienceOrb.award(serverPlayer.level(), Vec3.atCenterOf(this.worldPosition), whole);
			this.setChanged();
		}
	}

	public float getStoredExperience() {
		return this.experience;
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
		return CAPACITY;
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		long accepted = Math.max(0, Math.min(amount, Math.min(CAPACITY - this.energy, MAX_INPUT - this.energyReceivedThisTick)));
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
		this.energy = Math.min(CAPACITY, this.energy + Math.max(0, amount));
		this.setChanged();
	}

	// ---- container ----

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot >= 0 && slot < SLOTS ? this.items.get(slot) : ItemStack.EMPTY;
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
		if (slot < 0 || slot >= SLOTS) {
			return;
		}

		this.items.set(slot, stack);
		stack.limitSize(this.getMaxStackSize(stack));
		this.setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot >= LANES) {
			return false;
		}

		return !(this.level instanceof ServerLevel server) || Smelting.smelt(server, stack) != null;
	}

	@Override
	public boolean stillValid(Player player) {
		return net.minecraft.world.Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	public int[] getSlotsForFace(Direction side) {
		return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return side != Direction.DOWN && slot < LANES && this.canPlaceItem(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot >= LANES;
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.super_smelter");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new SuperSmelterMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items.clear();
		ContainerHelper.loadAllItems(input, this.items);
		this.energy = Math.clamp(input.getLongOr("Energy", 0L), 0L, CAPACITY);
		this.experience = Math.max(0.0F, input.getFloatOr("Experience", 0.0F));
		for (int lane = 0; lane < LANES; lane++) {
			this.progress[lane] = Math.clamp(input.getIntOr("Progress" + lane, 0), 0, COOK_TICKS - 1);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putLong("Energy", this.energy);
		output.putFloat("Experience", this.experience);
		for (int lane = 0; lane < LANES; lane++) {
			output.putInt("Progress" + lane, this.progress[lane]);
		}
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.energy = 0;
		java.util.Arrays.fill(this.progress, 0);
		this.setChanged();
		return data;
	}
}
