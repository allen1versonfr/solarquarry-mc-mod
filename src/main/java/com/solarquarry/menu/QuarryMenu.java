package com.solarquarry.menu;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.item.UpgradeItem;
import com.solarquarry.quarry.QuarryMode;
import com.solarquarry.quarry.QuarryStatus;
import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class QuarryMenu extends AbstractContainerMenu {
	public static final int BUTTON_START = 0;
	public static final int BUTTON_PAUSE = 1;
	public static final int BUTTON_RESUME = 2;
	public static final int BUTTON_RESTART = 3;

	public static final int BUFFER_START = 0;
	public static final int UPGRADE_START = QuarryBlockEntity.BUFFER_SLOTS;
	public static final int LIST_START = QuarryBlockEntity.TOTAL_SLOTS;
	public static final int LIST_COUNT = QuarryBlockEntity.FILTER_SLOTS * QuarryBlockEntity.ListKind.values().length;
	public static final int PLAYER_START = LIST_START + LIST_COUNT;
	public static final int PLAYER_END = PLAYER_START + 36;

	// Layout shared with the screen.
	public static final int BUFFER_X = 8;
	public static final int BUFFER_Y = 126;
	public static final int UPGRADE_X = 196;
	public static final int UPGRADE_Y = 36;
	public static final int LIST_X = 188;
	public static final int LIST_Y = 38;
	public static final int INVENTORY_X = 176;
	public static final int INVENTORY_Y = 126;
	public static final int HOTBAR_Y = 184;

	private final Container container;
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable QuarryBlockEntity quarry;
	/** Client-side view state. */
	private boolean configPage;
	private QuarryBlockEntity.ListKind listKind = QuarryBlockEntity.ListKind.VOID;

	/** Client constructor. */
	public QuarryMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, new SimpleContainer(QuarryBlockEntity.TOTAL_SLOTS),
			new SimpleContainer[] {new SimpleContainer(QuarryBlockEntity.FILTER_SLOTS), new SimpleContainer(QuarryBlockEntity.FILTER_SLOTS), new SimpleContainer(QuarryBlockEntity.FILTER_SLOTS)},
			new SimpleContainerData(QuarryBlockEntity.DATA_COUNT), null);
	}

	/** Server constructor. */
	public QuarryMenu(int containerId, Inventory inventory, QuarryBlockEntity quarry) {
		this(containerId, inventory, quarry.getBlockPos(), quarry,
			new SimpleContainer[] {quarry.getVoidFilter(), quarry.getSkipFilter(), quarry.getOreFilter()},
			quarry.getDataAccess(), quarry);
	}

	private QuarryMenu(int containerId, Inventory inventory, BlockPos pos, Container container, SimpleContainer[] lists, ContainerData data, @Nullable QuarryBlockEntity quarry) {
		super(ModMenus.QUARRY, containerId);
		checkContainerSize(container, QuarryBlockEntity.TOTAL_SLOTS);
		checkContainerDataCount(data, QuarryBlockEntity.DATA_COUNT);
		this.container = container;
		this.data = data;
		this.pos = pos;
		this.quarry = quarry;
		container.startOpen(inventory.player);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new OutputSlot(container, col + row * 9, BUFFER_X + col * 18, BUFFER_Y + row * 18));
			}
		}

		for (int i = 0; i < QuarryBlockEntity.UPGRADE_SLOTS; i++) {
			UpgradeSlot slot = new UpgradeSlot(container, UPGRADE_START + i, UPGRADE_X + (i % 2) * 18, UPGRADE_Y + (i / 2) * 18);
			slot.setActiveCondition(() -> !this.configPage);
			this.addSlot(slot);
		}

		QuarryBlockEntity.ListKind[] kinds = QuarryBlockEntity.ListKind.values();
		for (int k = 0; k < kinds.length; k++) {
			QuarryBlockEntity.ListKind kind = kinds[k];
			for (int i = 0; i < QuarryBlockEntity.FILTER_SLOTS; i++) {
				GhostSlot slot = new GhostSlot(lists[k], i, LIST_X + (i % 3) * 18, LIST_Y + (i / 3) * 18);
				slot.setActiveCondition(() -> this.configPage && this.listKind == kind);
				this.addSlot(slot);
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}

		this.addDataSlots(data);
	}

	public BlockPos getPos() {
		return this.pos;
	}

	public boolean isConfigPage() {
		return this.configPage;
	}

	public void setConfigPage(boolean configPage) {
		this.configPage = configPage;
	}

	public QuarryBlockEntity.ListKind getListKind() {
		return this.listKind;
	}

	public void setListKind(QuarryBlockEntity.ListKind listKind) {
		this.listKind = listKind;
	}

	// ---- synced values ----

	private int unsigned(int index) {
		return this.data.get(index) & 0xFFFF;
	}

	private int signed(int index) {
		return (short) this.data.get(index);
	}

	public long getEnergy() {
		return ((long) this.unsigned(1) << 16) | this.unsigned(0);
	}

	public long getEnergyCapacity() {
		return ((long) this.unsigned(3) << 16) | this.unsigned(2);
	}

	public QuarryStatus getStatus() {
		return QuarryStatus.byOrdinal(this.data.get(4));
	}

	public int getProgressPermille() {
		return this.data.get(5);
	}

	public int getCursorLateral() {
		return this.data.get(6);
	}

	public int getCursorForward() {
		return this.data.get(7);
	}

	public int getCurrentY() {
		return this.signed(8);
	}

	public int getTargetY() {
		return this.signed(9);
	}

	public int getAreaWidth() {
		return this.data.get(10);
	}

	public int getAreaLength() {
		return this.data.get(11);
	}

	public int getAreaHeight() {
		return this.data.get(12);
	}

	public QuarryMode getQuarryMode() {
		return QuarryMode.byOrdinal(this.data.get(13));
	}

	public QuarryBlockEntity.Mode getMode() {
		int ordinal = this.data.get(14);
		QuarryBlockEntity.Mode[] modes = QuarryBlockEntity.Mode.values();
		return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : QuarryBlockEntity.Mode.IDLE;
	}

	public long getBlocksMined() {
		return ((long) this.unsigned(16) << 16) | this.unsigned(15);
	}

	private boolean flag(int bit) {
		return (this.data.get(17) & bit) != 0;
	}

	public boolean hasVoidUpgrade() {
		return this.flag(1);
	}

	public boolean isOresOnly() {
		return this.flag(2);
	}

	public boolean isOutputConnected() {
		return this.flag(4);
	}

	public boolean hasSilkTouch() {
		return this.flag(8);
	}

	public boolean isScanComplete() {
		return this.flag(16);
	}

	public boolean hasChunkLoader() {
		return this.flag(32);
	}

	public boolean isChunkLoadingActive() {
		return this.flag(64);
	}

	public boolean isChunkLoadingDisabled() {
		return this.flag(128);
	}

	public int getMinY() {
		return this.signed(18);
	}

	public int getMaxAreaSize() {
		return Math.max(1, this.data.get(19));
	}

	public int getBlocksPerMinute() {
		return this.data.get(20);
	}

	public int getEnergyPerBlock() {
		return this.data.get(21);
	}

	public int getAverageInput() {
		return this.data.get(22);
	}

	public int getAverageUse() {
		return this.data.get(23);
	}

	public int getOreQueueSize() {
		return this.data.get(24);
	}

	public int getMaxY() {
		return this.signed(25);
	}

	public int getTypicalPowerDraw() {
		return this.data.get(26);
	}

	/** Blocks actually mined per second (rolling 10 s), from the server. */
	public double getMeasuredBlocksPerSecond() {
		return this.data.get(27) / 100.0;
	}

	public int getMaxLength() {
		return this.getQuarryMode() == QuarryMode.TUNNEL_BORE ? this.getMaxAreaSize() * com.solarquarry.quarry.MiningPattern.TUNNEL_LENGTH_FACTOR : this.getMaxAreaSize();
	}

	public int getMaxHeight() {
		return this.getQuarryMode() == QuarryMode.TUNNEL_BORE ? com.solarquarry.quarry.MiningPattern.MAX_TUNNEL_HEIGHT : com.solarquarry.quarry.MiningPattern.MAX_CLEAR_HEIGHT;
	}

	public int getUsedBufferSlots() {
		int used = 0;
		for (int i = 0; i < QuarryBlockEntity.BUFFER_SLOTS; i++) {
			if (!this.slots.get(i).getItem().isEmpty()) {
				used++;
			}
		}

		return used;
	}

	// ---- interaction ----

	@Override
	public boolean clickMenuButton(Player player, int buttonId) {
		if (this.quarry == null) {
			return false;
		}

		switch (buttonId) {
			case BUTTON_START -> this.quarry.start();
			case BUTTON_PAUSE -> this.quarry.pause();
			case BUTTON_RESUME -> this.quarry.resume();
			case BUTTON_RESTART -> this.quarry.resetProgress();
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
		if (slotIndex >= LIST_START && slotIndex < PLAYER_START) {
			if (containerInput == ContainerInput.PICKUP || containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.CLONE) {
				ItemStack carried = this.getCarried();
				Slot slot = this.slots.get(slotIndex);
				slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
				slot.setChanged();
			}

			return;
		}

		super.clicked(slotIndex, buttonNum, containerInput, player);
	}

	@Override
	public boolean canDragTo(Slot slot) {
		return !(slot instanceof GhostSlot) && super.canDragTo(slot);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slot instanceof GhostSlot || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (slotIndex < LIST_START) {
			if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.getItem() instanceof UpgradeItem && this.moveItemStackTo(stack, UPGRADE_START, LIST_START, false)) {
			// moved into an upgrade slot
		} else if (slotIndex < PLAYER_START + 27) {
			if (!this.moveItemStackTo(stack, PLAYER_START + 27, PLAYER_END, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false)) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.container.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.container.stopOpen(player);
	}
}
