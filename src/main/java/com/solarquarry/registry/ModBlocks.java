package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.block.BatteryBlock;
import com.solarquarry.block.EnergyCableBlock;
import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.block.SolarPanelBlock;
import com.solarquarry.block.TerminalBlock;
import com.solarquarry.energy.BatteryTier;
import com.solarquarry.energy.SolarTier;
import com.solarquarry.item.MachineBlockItem;
import com.solarquarry.quarry.QuarryLamp;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final List<Item> BLOCK_ITEMS = new ArrayList<>();

	public static final QuarryBlock QUARRY = register("quarry", QuarryBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 8.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(QuarryBlock.LAMP) == QuarryLamp.OFF ? 0 : 5),
		"tooltip.solarquarry.quarry", Rarity.UNCOMMON);

	public static final SolarPanelBlock SOLAR_PANEL_BASIC = registerSolar(SolarTier.BASIC, Rarity.COMMON);
	public static final SolarPanelBlock SOLAR_PANEL_ADVANCED = registerSolar(SolarTier.ADVANCED, Rarity.UNCOMMON);
	public static final SolarPanelBlock SOLAR_PANEL_ELITE = registerSolar(SolarTier.ELITE, Rarity.RARE);

	public static final BatteryBlock BATTERY_BASIC = registerBattery(BatteryTier.BASIC, Rarity.COMMON);
	public static final BatteryBlock BATTERY_ADVANCED = registerBattery(BatteryTier.ADVANCED, Rarity.UNCOMMON);
	public static final BatteryBlock BATTERY_ELITE = registerBattery(BatteryTier.ELITE, Rarity.RARE);

	public static final EnergyCableBlock ENERGY_CABLE = register("energy_cable", EnergyCableBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.COPPER).noOcclusion(),
		"tooltip.solarquarry.energy_cable", Rarity.COMMON);

	public static final ItemPipeBlock ITEM_PIPE = register("item_pipe", p -> new ItemPipeBlock(false, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.COPPER_GRATE).noOcclusion(),
		"tooltip.solarquarry.item_pipe", Rarity.COMMON);

	public static final ItemPipeBlock FILTER_PIPE = register("filter_pipe", p -> new ItemPipeBlock(true, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.COPPER_GRATE).noOcclusion(),
		"tooltip.solarquarry.filter_pipe", Rarity.COMMON);

	public static final ItemPipeBlock REINFORCED_PIPE = register("reinforced_pipe", p -> new ItemPipeBlock(false, com.solarquarry.pipe.PipeTier.REINFORCED, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.COPPER_GRATE).noOcclusion(),
		"tooltip.solarquarry.reinforced_pipe", Rarity.UNCOMMON);

	public static final ItemPipeBlock ADVANCED_PIPE = register("advanced_pipe", p -> new ItemPipeBlock(false, com.solarquarry.pipe.PipeTier.ADVANCED, p),
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.COPPER_GRATE).noOcclusion(),
		"tooltip.solarquarry.advanced_pipe", Rarity.RARE);

	public static final com.solarquarry.splitter.FilteredSplitterBlock FILTERED_SPLITTER = register("filtered_splitter", com.solarquarry.splitter.FilteredSplitterBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
		"tooltip.solarquarry.filtered_splitter", Rarity.UNCOMMON);

	public static final com.solarquarry.block.SortingTerminalBlock SORTING_TERMINAL = register("sorting_terminal", com.solarquarry.block.SortingTerminalBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state -> 4),
		"tooltip.solarquarry.sorting_terminal", Rarity.UNCOMMON);

	public static final com.solarquarry.storage.CrateBlock CRATE_BASIC = registerCrate(com.solarquarry.storage.CrateTier.BASIC, Rarity.COMMON);
	public static final com.solarquarry.storage.CrateBlock CRATE_REINFORCED = registerCrate(com.solarquarry.storage.CrateTier.REINFORCED, Rarity.UNCOMMON);
	public static final com.solarquarry.storage.CrateBlock CRATE_ADVANCED = registerCrate(com.solarquarry.storage.CrateTier.ADVANCED, Rarity.RARE);

	public static final com.solarquarry.fluid.FluidPipeBlock FLUID_PIPE = register("fluid_pipe", com.solarquarry.fluid.FluidPipeBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.0F).sound(SoundType.COPPER_GRATE).noOcclusion(),
		"tooltip.solarquarry.fluid_pipe", Rarity.COMMON);

	public static final com.solarquarry.fluid.LiquidTankBlock LIQUID_TANK = register("liquid_tank", com.solarquarry.fluid.LiquidTankBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
		"tooltip.solarquarry.liquid_tank", Rarity.UNCOMMON);

	public static final com.solarquarry.smelting.SuperSmelterBlock SUPER_SMELTER = register("super_smelter", com.solarquarry.smelting.SuperSmelterBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
			.lightLevel(state -> state.getValue(com.solarquarry.smelting.SuperSmelterBlock.LIT) ? 13 : 0),
		"tooltip.solarquarry.super_smelter", Rarity.RARE);

	public static final com.solarquarry.crafting.FabricationBenchBlock FABRICATION_BENCH = register("fabrication_bench", com.solarquarry.crafting.FabricationBenchBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 6.0F).sound(SoundType.METAL),
		"tooltip.solarquarry.fabrication_bench", Rarity.COMMON);

	public static final com.solarquarry.treefarm.TreeFarmBlock TREE_FARM = register("tree_farm", com.solarquarry.treefarm.TreeFarmBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
		"tooltip.solarquarry.tree_farm", Rarity.UNCOMMON);

	public static final com.solarquarry.kelpfarm.KelpFarmBlock KELP_FARM = register("kelp_farm", com.solarquarry.kelpfarm.KelpFarmBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
		"tooltip.solarquarry.kelp_farm", Rarity.UNCOMMON);

	public static final TerminalBlock REMOTE_TERMINAL = register("remote_terminal", TerminalBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state -> 4),
		"tooltip.solarquarry.remote_terminal", Rarity.UNCOMMON);

	private ModBlocks() {
	}

	private static SolarPanelBlock registerSolar(SolarTier tier, Rarity rarity) {
		return register("solar_panel_" + tier.id(), p -> new SolarPanelBlock(tier, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops(),
			"tooltip.solarquarry.solar_panel_" + tier.id(), rarity);
	}

	private static com.solarquarry.storage.CrateBlock registerCrate(com.solarquarry.storage.CrateTier tier, Rarity rarity) {
		return register("crate_" + tier.id(), p -> new com.solarquarry.storage.CrateBlock(tier, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
			"tooltip.solarquarry.crate_" + tier.id(), rarity);
	}

	private static BatteryBlock registerBattery(BatteryTier tier, Rarity rarity) {
		return register("battery_" + tier.id(), p -> new BatteryBlock(tier, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops(),
			"tooltip.solarquarry.battery_" + tier.id(), rarity);
	}

	private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties, String tooltip, Rarity rarity) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, SolarQuarry.id(name));
		T block = factory.apply(properties.setId(blockKey));
		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SolarQuarry.id(name));
		Item.Properties itemProperties = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().rarity(rarity);
		MachineBlockItem item = new MachineBlockItem(block, tooltip, itemProperties);
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		BLOCK_ITEMS.add(item);
		return block;
	}

	public static void init() {
	}
}
