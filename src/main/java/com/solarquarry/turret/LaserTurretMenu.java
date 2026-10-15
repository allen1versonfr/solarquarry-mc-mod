package com.solarquarry.turret;

import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Laser Turret menu: no slots, synced numbers and two buttons (power, targeting mode) checked on the server. */
public class LaserTurretMenu extends AbstractContainerMenu {
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable LaserTurretBlockEntity turret;

	public LaserTurretMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, pos, new SimpleContainerData(LaserTurretBlockEntity.DATA_COUNT), null);
	}

	public LaserTurretMenu(int containerId, Inventory inventory, LaserTurretBlockEntity turret) {
		this(containerId, turret.getBlockPos(), turret.getDataAccess(), turret);
	}

	private LaserTurretMenu(int containerId, BlockPos pos, ContainerData data, @Nullable LaserTurretBlockEntity turret) {
		super(ModMenus.LASER_TURRET, containerId);
		checkContainerDataCount(data, LaserTurretBlockEntity.DATA_COUNT);
		this.data = data;
		this.pos = pos;
		this.turret = turret;
		this.addDataSlots(data);
	}

	public BlockPos getPos() {
		return this.pos;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.turret == null || !this.stillValid(player)) {
			return false;
		}

		if (id == LaserTurretBlockEntity.BUTTON_POWER) {
			this.turret.setEnabled(!this.turret.isEnabled());
			return true;
		}

		if (id == LaserTurretBlockEntity.BUTTON_MODE) {
			this.turret.setMode(this.turret.getMode().next());
			return true;
		}

		return false;
	}

	private int unsigned(int index) {
		return this.data.get(index) & 0xFFFF;
	}

	public long getEnergy() {
		return this.unsigned(0) | ((long) this.unsigned(1) << 16);
	}

	public long getCapacity() {
		return this.unsigned(2) | ((long) this.unsigned(3) << 16);
	}

	public LaserTurretBlockEntity.Status getStatus() {
		int o = this.data.get(4);
		var values = LaserTurretBlockEntity.Status.values();
		return o >= 0 && o < values.length ? values[o] : LaserTurretBlockEntity.Status.IDLE;
	}

	public TurretTargeting.Mode getMode() {
		return TurretTargeting.Mode.byOrdinal(this.data.get(5));
	}

	public TurretTier getTier() {
		return TurretTier.byOrdinal(this.data.get(6));
	}

	public int getTargetId() {
		return this.data.get(7);
	}

	/** Average energy received per tick over the last second (0 = not connected or not supplied). */
	public int getInputRate() {
		return this.data.get(8);
	}

	public int getShots() {
		return this.data.get(9);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.turret == null || (!this.turret.isRemoved() && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0);
	}
}
