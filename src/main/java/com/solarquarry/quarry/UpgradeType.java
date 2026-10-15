package com.solarquarry.quarry;

/** Upgrade kinds, how many of each fit in one machine, and which machines take them. */
public enum UpgradeType {
	SPEED("speed", 4, true, true),
	ENERGY_CAPACITY("energy_capacity", 4, true, true),
	ENERGY_EFFICIENCY("energy_efficiency", 4, true, true),
	FORTUNE("fortune", 3, true, false),
	SILK_TOUCH("silk_touch", 1, true, false),
	RANGE("range", 1, true, true),
	VOID("void", 1, true, false),
	CHUNK_LOADER("chunk_loader", 1, true, false),
	LIQUID_REMOVER("liquid_remover", 1, true, false),
	AUTO_SMELTER("auto_smelter", 1, true, false),
	SECURITY("security", 1, true, false),
	GROWTH("growth", 2, false, true),
	HARVEST("harvest", 1, false, true);

	private final String id;
	private final int maxStack;
	private final boolean quarry;
	private final boolean treeFarm;

	UpgradeType(String id, int maxStack, boolean quarry, boolean treeFarm) {
		this.id = id;
		this.maxStack = maxStack;
		this.quarry = quarry;
		this.treeFarm = treeFarm;
	}

	public String id() {
		return this.id;
	}

	/** Maximum number of this upgrade that can be installed (stack size in the upgrade slot). */
	public int maxStack() {
		return this.maxStack;
	}

	public boolean fitsQuarry() {
		return this.quarry;
	}

	public boolean fitsTreeFarm() {
		return this.treeFarm;
	}

	/** Kelp Farm: speed, energy capacity, efficiency and growth. */
	public boolean fitsKelpFarm() {
		return this == SPEED || this == ENERGY_CAPACITY || this == ENERGY_EFFICIENCY || this == GROWTH;
	}
}
