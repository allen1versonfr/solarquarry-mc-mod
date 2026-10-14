package com.solarquarry.quarry;

import com.solarquarry.config.SolarQuarryConfig;
import com.solarquarry.item.UpgradeItem;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Snapshot of the upgrades installed in a quarry and the machine stats they produce. */
public record QuarryUpgrades(int speed, int capacity, int efficiency, int fortune, boolean silkTouch, int rangeTier, boolean voiding, boolean chunkLoader) {
	public static final QuarryUpgrades NONE = new QuarryUpgrades(0, 0, 0, 0, false, 0, false, false);
	public static final long BASE_CAPACITY = 100_000L;
	private static final int[] INTERVALS = {10, 6, 4, 2, 1};

	public static QuarryUpgrades of(Container upgrades, int first, int count) {
		int speed = 0;
		int capacity = 0;
		int efficiency = 0;
		int fortune = 0;
		boolean silk = false;
		int range = 0;
		boolean voiding = false;
		boolean chunkLoader = false;

		for (int i = first; i < first + count; i++) {
			ItemStack stack = upgrades.getItem(i);
			if (stack.isEmpty() || !(stack.getItem() instanceof UpgradeItem upgrade)) {
				continue;
			}

			int n = Math.min(stack.getCount(), upgrade.type().maxStack());
			switch (upgrade.type()) {
				case SPEED -> speed += n;
				case ENERGY_CAPACITY -> capacity += n;
				case ENERGY_EFFICIENCY -> efficiency += n;
				case FORTUNE -> fortune += n;
				case SILK_TOUCH -> silk = true;
				case RANGE -> range = Math.max(range, upgrade.tier());
				case VOID -> voiding = true;
				case CHUNK_LOADER -> chunkLoader = true;
			}
		}

		if (silk) {
			fortune = 0;
		}

		return new QuarryUpgrades(
			Math.min(speed, UpgradeType.SPEED.maxStack()),
			Math.min(capacity, UpgradeType.ENERGY_CAPACITY.maxStack()),
			Math.min(efficiency, UpgradeType.ENERGY_EFFICIENCY.maxStack()),
			Math.min(fortune, UpgradeType.FORTUNE.maxStack()),
			silk,
			Math.min(range, 2),
			voiding,
			chunkLoader
		);
	}

	/** Largest allowed width/length of the mining area: 16, 32 or 64 (range upgrade tier 0, 1, 2, capped by the server config). */
	public int maxAreaSize() {
		int cap = SolarQuarryConfig.get().maxRangeTier();
		return 16 << Math.max(0, Math.min(Math.min(2, cap), this.rangeTier));
	}

	public int ticksPerOperation() {
		return INTERVALS[Math.min(this.speed, INTERVALS.length - 1)];
	}

	public int blocksPerOperation() {
		return this.speed >= 4 ? 2 : 1;
	}

	/** Blocks per minute at full power (for display). */
	public int blocksPerMinute() {
		return 1200 / this.ticksPerOperation() * this.blocksPerOperation();
	}

	public long energyCapacity() {
		return BASE_CAPACITY * (1 + this.capacity);
	}

	/** Maximum energy the quarry accepts per tick from the network. */
	public long maxInput() {
		return 2_048L * (1 + this.speed);
	}

	public long energyPerBlock(float hardness) {
		double base = 60.0 + 15.0 * Math.max(0.0F, Math.min(hardness, 50.0F));
		return Math.max(1L, Math.round(base * this.costMultiplier()));
	}

	/** Typical energy per block for stone, used for display. */
	public long typicalEnergyPerBlock() {
		return this.energyPerBlock(1.5F);
	}

	/** Average power draw in E/t while mining stone continuously at full speed. */
	public long typicalPowerDraw() {
		return Math.max(1L, this.typicalEnergyPerBlock() * this.blocksPerOperation() / this.ticksPerOperation());
	}

	/** Combined energy-cost multiplier from speed, efficiency, silk touch, fortune and the server's energyCostMultiplier. */
	public double costMultiplier() {
		return (1.0 + 0.25 * this.speed) * (1.0 - 0.15 * this.efficiency) * (this.silkTouch ? 1.25 : 1.0) * (1.0 + 0.2 * this.fortune)
			* SolarQuarryConfig.get().energyCostMultiplier();
	}
}
