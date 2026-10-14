package com.solarquarry.quarry;

import net.minecraft.util.StringRepresentable;

/** The status light shown on the quarry's front face (a block state property). */
public enum QuarryLamp implements StringRepresentable {
	OFF("off"),
	GREEN("green"),
	YELLOW("yellow"),
	RED("red"),
	BLUE("blue");

	private final String name;

	QuarryLamp(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public static QuarryLamp forStatus(QuarryStatus status) {
		return switch (status) {
			case IDLE -> OFF;
			case RUNNING, SCANNING -> GREEN;
			case PAUSED -> YELLOW;
			case WAITING_FOR_POWER, OUTPUT_BLOCKED, WAITING_FOR_CHUNK -> RED;
			case COMPLETE -> BLUE;
		};
	}
}
