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
		"quarry", "wrench", "ore_prospector", "config_card", "remote_terminal",
		// Energy
		"solar_panel_basic", "solar_panel_advanced", "solar_panel_elite",
		"battery_basic", "battery_advanced", "battery_elite", "energy_cable",
		// Pipes and sorting
		"item_pipe", "reinforced_pipe", "advanced_pipe", "filter_pipe", "sorting_terminal",
		// Quarry upgrades
		"speed_upgrade", "energy_capacity_upgrade", "energy_efficiency_upgrade", "fortune_upgrade", "silk_touch_upgrade",
		"range_upgrade", "range_upgrade_2", "void_upgrade", "chunk_loader_upgrade", "auto_smelter_upgrade", "security_upgrade",
		// Smelting
		"super_smelter",
		// Tree farming
		"tree_farm", "growth_upgrade", "harvest_upgrade",
		// Trader
		"trader_spawn_egg", "guard_spawn_egg",
		// Security
		"security_drone_spawn_egg",
		// Equipment
		"leather_rocket_boots", "iron_rocket_boots", "diamond_rocket_boots", "netherite_rocket_boots",
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
