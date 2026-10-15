package com.solarquarry.weather;

import com.solarquarry.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Weather Control Machine menu: no slots, synced state, and buttons that are checked on the server (a command is only
 * accepted in standby, so clicking twice or two players clicking at once start one operation and pay once).
 */
public class WeatherMachineMenu extends AbstractContainerMenu {
	private final ContainerData data;
	private final BlockPos pos;
	private final @Nullable WeatherMachineBlockEntity machine;

	public WeatherMachineMenu(int containerId, Inventory inventory, BlockPos pos) {
		this(containerId, pos, new net.minecraft.world.inventory.SimpleContainerData(WeatherMachineBlockEntity.DATA_COUNT), null);
	}

	public WeatherMachineMenu(int containerId, Inventory inventory, WeatherMachineBlockEntity machine) {
		this(containerId, machine.getBlockPos(), machine.getDataAccess(), machine);
	}

	private WeatherMachineMenu(int containerId, BlockPos pos, ContainerData data, @Nullable WeatherMachineBlockEntity machine) {
		super(ModMenus.WEATHER_MACHINE, containerId);
		checkContainerDataCount(data, WeatherMachineBlockEntity.DATA_COUNT);
		this.data = data;
		this.pos = pos;
		this.machine = machine;
		this.addDataSlots(data);
	}

	public BlockPos getPos() {
		return this.pos;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.machine == null || !this.stillValid(player) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}

		WeatherMachineBlockEntity m = this.machine;
		switch (id) {
			case WeatherMachineBlockEntity.BUTTON_STOP -> m.stop(level);
			case WeatherMachineBlockEntity.BUTTON_CLEAR -> m.startWeather(level, WeatherOperation.CLEAR);
			case WeatherMachineBlockEntity.BUTTON_RAIN -> m.startWeather(level, WeatherOperation.RAIN);
			case WeatherMachineBlockEntity.BUTTON_THUNDER -> m.startWeather(level, WeatherOperation.THUNDER);
			case WeatherMachineBlockEntity.BUTTON_SUSTAIN -> m.toggleSustained();
			case WeatherMachineBlockEntity.BUTTON_DURATION -> m.cycleDuration();
			case WeatherMachineBlockEntity.BUTTON_GRADUAL -> m.toggleGradual();
			case WeatherMachineBlockEntity.BUTTON_SET_TIME -> m.startTime(level, m.manualTarget(), m.isGradual());
			default -> {
				if (id >= WeatherMachineBlockEntity.BUTTON_PRESET && id < WeatherMachineBlockEntity.BUTTON_PRESET + WeatherMachineBlockEntity.PRESETS.length) {
					m.startTime(level, WeatherMachineBlockEntity.PRESETS[id - WeatherMachineBlockEntity.BUTTON_PRESET], m.isGradual());
				} else if (id >= WeatherMachineBlockEntity.BUTTON_MANUAL && id < WeatherMachineBlockEntity.BUTTON_MANUAL + 240) {
					m.setManualTarget((id - WeatherMachineBlockEntity.BUTTON_MANUAL) * 100);
				} else {
					return false;
				}
			}
		}

		return true;
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

	public WeatherStage getStage() {
		return WeatherStage.byOrdinal(this.data.get(4));
	}

	public WeatherOperation getOperation() {
		return WeatherOperation.byOrdinal(this.data.get(5));
	}

	public int getStageProgress() {
		return this.data.get(6);
	}

	public int getRemainingSeconds() {
		return this.data.get(7);
	}

	public boolean isSustained() {
		return this.data.get(8) != 0;
	}

	public int getDurationMinutes() {
		return WeatherMachineBlockEntity.DURATIONS[Math.floorMod(this.data.get(9), WeatherMachineBlockEntity.DURATIONS.length)];
	}

	public boolean isGradual() {
		return this.data.get(10) != 0;
	}

	public int getManualTarget() {
		return this.data.get(11) * 10;
	}

	public int getTargetTime() {
		return this.data.get(12) * 10;
	}

	public WeatherMachineBlockEntity.Error getError() {
		int o = this.data.get(13);
		var values = WeatherMachineBlockEntity.Error.values();
		return o >= 0 && o < values.length ? values[o] : WeatherMachineBlockEntity.Error.NONE;
	}

	public int getInputRate() {
		return this.data.get(14);
	}

	public int getConsumption() {
		return this.data.get(15);
	}

	public boolean isLowPower() {
		return this.data.get(16) != 0;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.machine == null || (!this.machine.isRemoved() && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0);
	}
}
