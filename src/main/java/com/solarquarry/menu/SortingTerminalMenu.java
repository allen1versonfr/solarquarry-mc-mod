package com.solarquarry.menu;

import com.solarquarry.blockentity.SortingTerminalBlockEntity;
import com.solarquarry.network.SortingSnapshotPayload;
import com.solarquarry.pipe.SortRule;
import com.solarquarry.pipe.StorageScan;
import com.solarquarry.registry.ModMenus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Sorting Terminal menu: 9 template slots for the selected destination plus the player's inventory. The server
 * streams the destination list to its player once a second (and right after every change).
 */
public class SortingTerminalMenu extends AbstractContainerMenu {
	public static final int GHOST_X = 8;
	public static final int GHOST_Y = 158;
	public static final int INVENTORY_Y = 158;
	public static final int HOTBAR_Y = 216;
	/** Player inventory sits to the right of the 3x3 template grid. */
	public static final int INVENTORY_X = 88;
	public static final int PLAYER_START = SortRule.MAX_ITEMS;
	private static final int SNAPSHOT_INTERVAL = 20;

	private final BlockPos pos;
	private final @Nullable SortingTerminalBlockEntity terminal;
	private final @Nullable ServerPlayer player;
	private final SimpleContainer templates = new SimpleContainer(SortRule.MAX_ITEMS) {
		@Override
		public void setChanged() {
			super.setChanged();
			SortingTerminalMenu.this.saveTemplates();
		}
	};
	private boolean templatesActive;
	private final List<GhostSlot> ghostSlots = new ArrayList<>();
	private @Nullable BlockPos selected;
	private boolean loading;
	private boolean refresh = true;
	private int ticks;

	/** Client constructor. */
	public SortingTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, inventory, pos, null);
	}

	/** Server constructor. */
	public SortingTerminalMenu(int containerId, Inventory inventory, SortingTerminalBlockEntity terminal) {
		this(containerId, inventory, terminal.getBlockPos(), terminal);
	}

	private SortingTerminalMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable SortingTerminalBlockEntity terminal) {
		super(ModMenus.SORTING_TERMINAL, containerId);
		this.pos = pos;
		this.terminal = terminal;
		this.player = inventory.player instanceof ServerPlayer serverPlayer && terminal != null ? serverPlayer : null;
		for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
			GhostSlot slot = new GhostSlot(this.templates, i, GHOST_X + (i % 3) * 18, GHOST_Y + (i / 3) * 18);
			slot.setActiveCondition(() -> this.templatesActive);
			this.ghostSlots.add(slot);
			this.addSlot(slot);
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	public BlockPos getPos() {
		return this.pos;
	}

	public @Nullable BlockPos getSelected() {
		return this.selected;
	}

	/** Client: show or hide the template slots for the selected destination (from the snapshot). */
	public void setTemplatesActive(boolean active) {
		this.templatesActive = active;
	}

	// ---- server actions (validated by the payload handler) ----

	public void select(@Nullable BlockPos target) {
		this.selected = target;
		this.loadTemplates();
		this.refresh = true;
	}

	private void loadTemplates() {
		if (this.terminal == null) {
			return;
		}

		SortRule rule = this.selected == null ? null : this.terminal.getRule(this.selected);
		this.loading = true;
		for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
			this.templates.setItem(i, rule == null ? ItemStack.EMPTY : SortingTerminalBlockEntity.stackOf(rule.items().get(i)));
		}

		this.loading = false;
		boolean active = rule != null && rule.mode() != SortRule.Mode.FALLBACK;
		this.setTemplatesActive(active);
	}

	private void saveTemplates() {
		if (this.loading || this.terminal == null || this.selected == null) {
			return;
		}

		SortRule rule = this.terminal.getRule(this.selected);
		if (rule == null) {
			return;
		}

		for (int i = 0; i < SortRule.MAX_ITEMS; i++) {
			ItemStack stack = this.templates.getItem(i);
			rule.setItem(i, stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}

		this.terminal.changed();
		this.refresh = true;
	}

	/** Destinations the player may act on: inventories on the network plus registered ones that went missing. */
	public boolean isKnownTarget(BlockPos target) {
		if (this.terminal == null) {
			return false;
		}

		return this.terminal.getRule(target) != null || this.terminal.discover().contains(target);
	}

	public void afterRuleChange() {
		this.loadTemplates();
		this.refresh = true;
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.terminal == null || this.player == null) {
			return;
		}

		this.ticks++;
		if (!this.refresh && this.ticks % SNAPSHOT_INTERVAL != 0) {
			return;
		}

		this.refresh = false;
		Level level = this.terminal.getLevel();
		Set<BlockPos> targets = new LinkedHashSet<>(this.terminal.discover());
		for (SortRule rule : this.terminal.getRules()) {
			targets.add(BlockPos.of(rule.pos()));
		}

		List<SortingSnapshotPayload.Entry> entries = new ArrayList<>();
		for (BlockPos target : targets) {
			if (entries.size() >= SortingSnapshotPayload.MAX_ENTRIES) {
				break;
			}

			SortRule rule = this.terminal.getRule(target);
			boolean loaded = level != null && level.isLoaded(target);
			boolean present = loaded && StorageScan.slots(level, target)[2] > 0;
			long[] slots = present ? StorageScan.slots(level, target) : new long[3];
			String icon = loaded ? BuiltInRegistries.ITEM.getKey(level.getBlockState(target).getBlock().asItem()).toString() : "minecraft:barrier";
			int flags = (rule != null ? SortingSnapshotPayload.FLAG_REGISTERED : 0) | (present ? SortingSnapshotPayload.FLAG_PRESENT : 0)
				| (loaded ? SortingSnapshotPayload.FLAG_LOADED : 0) | (rule != null && rule.hasEntries() ? SortingSnapshotPayload.FLAG_HAS_ENTRIES : 0);
			entries.add(new SortingSnapshotPayload.Entry(target, icon, flags, rule == null ? 0 : rule.mode().ordinal(),
				rule == null ? 0 : rule.priority(), (int) slots[1], (int) slots[2]));
		}

		SortRule selectedRule = this.selected == null ? null : this.terminal.getRule(this.selected);
		List<String> tags = selectedRule == null ? List.of("", "", "") : List.copyOf(selectedRule.tags());
		ServerPlayNetworking.send(this.player, new SortingSnapshotPayload(this.pos, entries,
			this.selected == null ? Long.MIN_VALUE : this.selected.asLong(), tags, this.terminal.hasFallback()));
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
		if (slotIndex >= 0 && slotIndex < PLAYER_START) {
			Slot slot = this.slots.get(slotIndex);
			if (slot.isActive() && (containerInput == ContainerInput.PICKUP || containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.CLONE)) {
				ItemStack carried = this.getCarried();
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
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.terminal == null || this.terminal.stillValid(player);
	}
}
