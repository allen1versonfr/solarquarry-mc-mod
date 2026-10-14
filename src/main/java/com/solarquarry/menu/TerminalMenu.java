package com.solarquarry.menu;

import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.blockentity.TerminalBlockEntity;
import com.solarquarry.network.TerminalSnapshotPayload;
import com.solarquarry.pipe.StorageScan;
import com.solarquarry.registry.ModMenus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Slot-less menu for the remote terminal. On the server it streams snapshots to its player twice a second. */
public class TerminalMenu extends AbstractContainerMenu {
	private static final int SNAPSHOT_INTERVAL = 10;
	private static final int STORAGE_INTERVAL = 40;
	private final BlockPos pos;
	private final @Nullable TerminalBlockEntity terminal;
	private final @Nullable ServerPlayer player;
	private final Map<BlockPos, StorageScan.Result> storageCache = new HashMap<>();
	private int ticks;
	private boolean refresh = true;

	/** Client constructor. */
	public TerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
		super(ModMenus.TERMINAL, containerId);
		this.pos = pos;
		this.terminal = null;
		this.player = null;
	}

	/** Server constructor. */
	public TerminalMenu(int containerId, Inventory inventory, TerminalBlockEntity terminal) {
		super(ModMenus.TERMINAL, containerId);
		this.pos = terminal.getBlockPos();
		this.terminal = terminal;
		this.player = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
	}

	public BlockPos getPos() {
		return this.pos;
	}

	public void requestRefresh() {
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

		boolean refreshStorage = this.refresh || this.ticks % STORAGE_INTERVAL == 0;
		this.refresh = false;
		List<TerminalSnapshotPayload.Entry> entries = new ArrayList<>();
		for (QuarryBlockEntity quarry : this.terminal.findQuarries()) {
			StorageScan.Result storage = this.storageCache.get(quarry.getBlockPos());
			if (storage == null || refreshStorage) {
				storage = StorageScan.scan(quarry.getLevel(), quarry.getBlockPos());
				this.storageCache.put(quarry.getBlockPos(), storage);
			}

			int flags = (quarry.isAuthorized(this.player) ? TerminalSnapshotPayload.FLAG_AUTHORIZED : 0)
				| (quarry.isOutputConnected() ? TerminalSnapshotPayload.FLAG_OUTPUT_CONNECTED : 0)
				| (storage.lowSpace() ? TerminalSnapshotPayload.FLAG_LOW_SPACE : 0);
			entries.add(new TerminalSnapshotPayload.Entry(quarry.getBlockPos(), quarry.getStatus().ordinal(), quarry.getMode().ordinal(),
				quarry.getQuarryMode().ordinal(), quarry.getEnergy(), quarry.getEnergyCapacity(), quarry.progressPermille(),
				quarry.getUsedBufferSlots(), quarry.getWidth(), quarry.getLength(), quarry.currentY(), quarry.effectiveTargetY(),
				quarry.getBlocksMined(), flags, quarry.getOwnerName(), storage.inventories(), storage.usedSlots(), storage.totalSlots(),
				quarry.getAverageInput(), quarry.getAverageUse()));
		}

		ServerPlayNetworking.send(this.player, new TerminalSnapshotPayload(this.pos, this.player.level().getGameTime(), entries));
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
