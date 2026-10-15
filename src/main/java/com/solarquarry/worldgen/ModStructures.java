package com.solarquarry.worldgen;

import com.solarquarry.SolarQuarry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/** Registration of the structure type, its piece type, and the keys of the ten structures and their loot tables. */
public final class ModStructures {
	public static final StructureType<IndustrialStructure> INDUSTRIAL = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, SolarQuarry.id("industrial"),
		() -> IndustrialStructure.CODEC);
	public static final StructurePieceType INDUSTRIAL_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, SolarQuarry.id("industrial_piece"),
		(StructurePieceType) (context, tag) -> new IndustrialPiece(tag));

	private ModStructures() {
	}

	public static ResourceKey<Structure> structure(Layout layout) {
		return ResourceKey.create(Registries.STRUCTURE, SolarQuarry.id(layout.id()));
	}

	public static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, SolarQuarry.id("chests/" + name));
	}

	public static void init() {
	}
}
