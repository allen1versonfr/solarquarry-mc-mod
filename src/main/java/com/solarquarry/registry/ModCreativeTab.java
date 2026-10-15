package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Solar Quarry creative tab, grouped by category. Items are listed by id so the order is explicit; anything
 * registered by the mod but missing from the list is appended at the end, so no item can be left out.
 */
public final class ModCreativeTab {
	/** Display order, one category after another. */
	public static final List<String> ORDER = List.of(
		// Quarry and tools
		"quarry", "fabrication_bench", "wrench", "config_card", "remote_terminal",
		// Energy
		"solar_panel_basic", "solar_panel_advanced", "solar_panel_elite",
		"battery_basic", "battery_advanced", "battery_elite", "energy_cable",
		// Pipes and sorting
		"item_pipe", "reinforced_pipe", "advanced_pipe", "filter_pipe", "filtered_splitter", "sorting_terminal",
		// Storage
		"crate_basic", "crate_reinforced", "crate_advanced", "fluid_pipe", "liquid_tank",
		// Quarry upgrades
		"speed_upgrade", "energy_capacity_upgrade", "energy_efficiency_upgrade", "fortune_upgrade", "silk_touch_upgrade",
		"range_upgrade", "range_upgrade_2", "void_upgrade", "chunk_loader_upgrade", "liquid_remover_upgrade", "auto_smelter_upgrade", "security_upgrade", "miner_drone",
		// Smelting
		"super_smelter",
		// Endgame
		"weather_control_machine",
		// Tree farming
		"tree_farm", "growth_upgrade", "harvest_upgrade",
		// Kelp farming
		"kelp_farm",
		// Mechanic
		"trader_spawn_egg", "guard_spawn_egg",
		// Security
		"laser_turret_basic", "laser_turret_advanced", "laser_turret_elite", "security_drone_spawn_egg",
		// Powered equipment
		"charging_station", "ore_prospector", "liquid_prospector", "slime_prospector", "advanced_mining_scanner", "scanner_helmet",
		"night_vision_goggles", "mining_helmet", "portable_power_cell", "prototype_battery", "emergency_energy_injector", "industrial_repair_kit",
		"industrial_power_hammer", "reinforced_mining_pickaxe", "advanced_powered_pickaxe",
		"leather_rocket_boots", "iron_rocket_boots", "diamond_rocket_boots", "helionite_rocket_boots",
		// Helionite
		"helionite_ore", "deepslate_helionite_ore", "raw_helionite", "helionite_ingot", "helionite_block",
		"helionite_sword", "helionite_pickaxe", "helionite_axe", "helionite_shovel", "helionite_hoe",
		"helionite_helmet", "helionite_chestplate", "helionite_leggings", "helionite_boots",
		// Components
		"circuit_board", "advanced_circuit", "photovoltaic_cell", "machine_frame", "drill_head", "upgrade_base"
	);

	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		SolarQuarry.id("main"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.solarquarry.main"))
			.icon(() -> new ItemStack(ModBlocks.QUARRY))
			.displayItems((parameters, output) -> {
				for (Item item : orderedItems()) {
					output.accept(item);
				}
			})
			.build()
	);

	private ModCreativeTab() {
	}

	/** Every mod item in tab order (listed ones first, then any unlisted ones). */
	public static List<Item> orderedItems() {
		Set<Item> result = new LinkedHashSet<>();
		for (String id : ORDER) {
			BuiltInRegistries.ITEM.getOptional(SolarQuarry.id(id)).ifPresent(result::add);
		}

		List<Item> all = new ArrayList<>(ModBlocks.BLOCK_ITEMS);
		all.addAll(ModItems.ITEMS);
		result.addAll(all);
		return new ArrayList<>(result);
	}

	public static void init() {
	}
}
