package com.solarquarry.charging;

import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.item.PoweredItem;
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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
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
import org.jspecify.annotations.Nullable;

/**
 * Charging Station: charges powered tools and armour (Rocket Boots, prospectors, the Scanner Helmet) from the Solar
 * Quarry energy network. Slot 0 takes the item to charge, slot 1 receives it when full. Every point of energy put
 * into an item was taken from the station's own buffer first, so nothing is created; the item keeps its charge in
 * its own component, so taking it out half-charged is always safe. Durability and enchantments are not touched.
 * Automation: powered items go in from the top and sides, charged items come out of the bottom.
 */
public class ChargingStationBlockEntity extends BlockEntity implements WorldlyContainer, EnergyNode, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int INPUT = 0;
	public static final int OUTPUT = 1;
	public static final int SLOTS = 2;
	public static final long CAPACITY = 200_000;
	public static final long MAX_INPUT = 2_048;
	/** Highest charging rate (E per tick); items may accept less. */
	public static final long MAX_RATE = 1_000;
	public static final int DATA_COUNT = 6;
	private static final int[] TOP_SLOTS = {INPUT};
	private static final int[] BOTTOM_SLOTS = {OUTPUT};

	public enum Status {
		EMPTY,
		CHARGING,
		NO_POWER,
		DONE,
		OUTPUT_BLOCKED
	}

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private long energy;
	private long energyReceivedThisTick;
	private long rateLastTick;
	private Status status = Status.EMPTY;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			ChargingStationBlockEntity s = ChargingStationBlockEntity.this;
			return switch (index) {
				case 0 -> (int) (s.energy & 0xFFFF);
				case 1 -> (int) ((s.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (CAPACITY & 0xFFFF);
				case 3 -> (int) ((CAPACITY >>> 16) & 0xFFFF);
				case 4 -> (int) Math.min(0x7FFF, s.rateLastTick);
				case 5 -> s.status.ordinal();
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

	public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CHARGING_STATION, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingStationBlockEntity station) {
		if (level instanceof ServerLevel) {
			station.tick(state);
		}
	}

	/** Charging rate for an item: its own limit, the station's limit. */
	public static long rateFor(ItemStack stack) {
		return stack.getItem() instanceof PoweredItem powered ? Math.min(MAX_RATE, powered.chargeRate(stack)) : 0L;
	}

	private void tick(BlockState state) {
		this.energyReceivedThisTick = 0;
		this.rateLastTick = 0;
		ItemStack input = this.items.get(INPUT);
		Status next;
		if (input.isEmpty()) {
			next = this.items.get(OUTPUT).isEmpty() ? Status.EMPTY : Status.DONE;
		} else if (PoweredItem.isFull(input)) {
			if (this.items.get(OUTPUT).isEmpty()) {
				this.items.set(OUTPUT, input);
				this.items.set(INPUT, ItemStack.EMPTY);
				this.setChanged();
				next = Status.DONE;
			} else {
				next = Status.OUTPUT_BLOCKED;
			}
		} else {
			long wanted = PoweredItem.charge(input, rateFor(input), true);
			long move = Math.min(wanted, this.energy);
			if (move > 0) {
				long accepted = PoweredItem.charge(input, move, false);
				this.energy -= accepted;
				this.rateLastTick = accepted;
				this.setChanged();
				next = Status.CHARGING;
			} else {
				next = Status.NO_POWER;
			}
		}

		if (next != this.status) {
			this.status = next;
			this.setChanged();
			boolean lit = next == Status.CHARGING;
			if (state.hasProperty(ChargingStationBlock.LIT) && state.getValue(ChargingStationBlock.LIT) != lit && this.level != null) {
				this.level.setBlock(this.worldPosition, state.setValue(ChargingStationBlock.LIT, lit), 3);
			}
		}
	}

	public Status getStatus() {
		return this.status;
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
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return canPlace(slot, stack);
	}

	/** Slot rules, shared with the client's copy of the container. */
	public static boolean canPlace(int slot, ItemStack stack) {
		return slot == INPUT && PoweredItem.isPowered(stack);
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
		return side == Direction.DOWN ? BOTTOM_SLOTS : TOP_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
		return side != Direction.DOWN && slot == INPUT && this.items.get(INPUT).isEmpty() && canPlace(slot, stack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
		return side == Direction.DOWN && slot == OUTPUT;
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.charging_station");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new ChargingStationMenu(containerId, inventory, this);
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
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, this.items);
		output.putLong("Energy", this.energy);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.items.clear();
		this.energy = 0;
		this.setChanged();
		return data;
	}
}
