package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.blockentity.SolarPanelBlockEntity;
import com.solarquarry.blockentity.TerminalBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
	public static final BlockEntityType<QuarryBlockEntity> QUARRY = register("quarry",
		FabricBlockEntityTypeBuilder.create(QuarryBlockEntity::new, ModBlocks.QUARRY).build());
	public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL = register("solar_panel",
		FabricBlockEntityTypeBuilder.create(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL_BASIC, ModBlocks.SOLAR_PANEL_ADVANCED, ModBlocks.SOLAR_PANEL_ELITE).build());
	public static final BlockEntityType<BatteryBlockEntity> BATTERY = register("battery",
		FabricBlockEntityTypeBuilder.create(BatteryBlockEntity::new, ModBlocks.BATTERY_BASIC, ModBlocks.BATTERY_ADVANCED, ModBlocks.BATTERY_ELITE).build());
	public static final BlockEntityType<ItemPipeBlockEntity> ITEM_PIPE = register("item_pipe",
		FabricBlockEntityTypeBuilder.create(ItemPipeBlockEntity::new, ModBlocks.ITEM_PIPE, ModBlocks.FILTER_PIPE, ModBlocks.REINFORCED_PIPE, ModBlocks.ADVANCED_PIPE).build());

	public static final BlockEntityType<com.solarquarry.storage.CrateBlockEntity> CRATE = register("crate",
		FabricBlockEntityTypeBuilder.create(com.solarquarry.storage.CrateBlockEntity::new, ModBlocks.CRATE_BASIC, ModBlocks.CRATE_REINFORCED, ModBlocks.CRATE_ADVANCED).build());

	public static final BlockEntityType<com.solarquarry.blockentity.SortingTerminalBlockEntity> SORTING_TERMINAL = register("sorting_terminal",
		FabricBlockEntityTypeBuilder.create(com.solarquarry.blockentity.SortingTerminalBlockEntity::new, ModBlocks.SORTING_TERMINAL).build());

	public static final BlockEntityType<com.solarquarry.fluid.LiquidTankBlockEntity> LIQUID_TANK = register("liquid_tank",
		FabricBlockEntityTypeBuilder.create(com.solarquarry.fluid.LiquidTankBlockEntity::new, ModBlocks.LIQUID_TANK).build());

	public static final BlockEntityType<com.solarquarry.smelting.SuperSmelterBlockEntity> SUPER_SMELTER = register("super_smelter",
		FabricBlockEntityTypeBuilder.create(com.solarquarry.smelting.SuperSmelterBlockEntity::new, ModBlocks.SUPER_SMELTER).build());

	public static final BlockEntityType<com.solarquarry.treefarm.TreeFarmBlockEntity> TREE_FARM = register("tree_farm",
		FabricBlockEntityTypeBuilder.create(com.solarquarry.treefarm.TreeFarmBlockEntity::new, ModBlocks.TREE_FARM).build());

	public static final BlockEntityType<TerminalBlockEntity> TERMINAL = register("remote_terminal",
		FabricBlockEntityTypeBuilder.create(TerminalBlockEntity::new, ModBlocks.REMOTE_TERMINAL).build());

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SolarQuarry.id(name), type);
	}

	public static void init() {
	}
}
