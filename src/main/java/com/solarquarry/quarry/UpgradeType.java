package com.solarquarry.quarry;

public enum UpgradeType {
	SPEED("speed", 4),
	ENERGY_CAPACITY("energy_capacity", 4),
	ENERGY_EFFICIENCY("energy_efficiency", 4),
	FORTUNE("fortune", 3),
	SILK_TOUCH("silk_touch", 1),
	RANGE("range", 1),
	VOID("void", 1),
	CHUNK_LOADER("chunk_loader", 1),
	LIQUID_REMOVER("liquid_remover", 1);

	private final String id;
	private final int maxStack;

	UpgradeType(String id, int maxStack) {
		this.id = id;
		this.maxStack = maxStack;
	}

	public String id() {
		return this.id;
	}

	/** Maximum number of this upgrade that can be installed (stack size in the upgrade slot). */
	public int maxStack() {
		return this.maxStack;
	}
}
