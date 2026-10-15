package com.solarquarry.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The ten Solar Quarry structures: footprint (x, y, z), whether they are dug in underground (and how deep below the
 * surface their floor sits) and how uneven the ground under a surface structure may be.
 */
public enum Layout implements StringRepresentable {
	MINING_FACILITY("mining_facility", 31, 9, 31, 24, 0),
	POWER_STATION("power_station", 27, 9, 21, 0, 4),
	RESEARCH_LABORATORY("research_laboratory", 25, 7, 25, 36, 0),
	AUTOMATED_FACTORY("automated_factory", 29, 9, 19, 0, 4),
	TREE_FARM("tree_farm", 21, 10, 21, 0, 5),
	SUPPLY_BUNKER("supply_bunker", 15, 14, 11, 9, 0),
	SUBSTATION("substation", 19, 10, 19, 0, 3),
	PROTOTYPE_FACILITY("prototype_facility", 31, 9, 31, 40, 0),
	TRAIN_DEPOT("train_depot", 33, 8, 17, 0, 3),
	SOLAR_ARRAY("solar_array", 25, 6, 25, 0, 4);

	public static final Codec<Layout> CODEC = StringRepresentable.fromEnum(Layout::values);

	private final String id;
	public final int sizeX;
	public final int sizeY;
	public final int sizeZ;
	/** 0 for surface structures; otherwise how far below the surface the floor is. */
	public final int depth;
	/** Surface structures: the most the ground may rise or fall across the footprint. */
	public final int maxSlope;

	Layout(String id, int sizeX, int sizeY, int sizeZ, int depth, int maxSlope) {
		this.id = id;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.depth = depth;
		this.maxSlope = maxSlope;
	}

	public boolean underground() {
		return this.depth > 0;
	}

	public String id() {
		return this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}
