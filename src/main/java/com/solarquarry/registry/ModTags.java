package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	/** Blocks every quarry leaves alone (data-pack configurable). */
	public static final TagKey<Block> QUARRY_IGNORED = TagKey.create(Registries.BLOCK, SolarQuarry.id("quarry_ignored"));

	private ModTags() {
	}
}
