package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.menu.FilterPipeMenu;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.menu.SortingTerminalMenu;
import com.solarquarry.menu.TerminalMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModMenus {
	public static final ExtendedMenuType<QuarryMenu, BlockPos> QUARRY = Registry.register(
		BuiltInRegistries.MENU, SolarQuarry.id("quarry"), new ExtendedMenuType<>(QuarryMenu::new, BlockPos.STREAM_CODEC)
	);
	public static final ExtendedMenuType<FilterPipeMenu, BlockPos> FILTER_PIPE = Registry.register(
		BuiltInRegistries.MENU, SolarQuarry.id("filter_pipe"), new ExtendedMenuType<>(FilterPipeMenu::new, BlockPos.STREAM_CODEC)
	);

	public static final ExtendedMenuType<TerminalMenu, BlockPos> TERMINAL = Registry.register(
		BuiltInRegistries.MENU, SolarQuarry.id("remote_terminal"), new ExtendedMenuType<>(TerminalMenu::new, BlockPos.STREAM_CODEC)
	);

	public static final ExtendedMenuType<SortingTerminalMenu, BlockPos> SORTING_TERMINAL = Registry.register(
		BuiltInRegistries.MENU, SolarQuarry.id("sorting_terminal"), new ExtendedMenuType<>(SortingTerminalMenu::new, BlockPos.STREAM_CODEC)
	);

	public static final ExtendedMenuType<com.solarquarry.storage.CrateMenu, Integer> CRATE = Registry.register(
		BuiltInRegistries.MENU, SolarQuarry.id("crate"), new ExtendedMenuType<>(
			(id, inventory, tier) -> new com.solarquarry.storage.CrateMenu(id, inventory, com.solarquarry.storage.CrateTier.values()[Math.clamp(tier, 0, com.solarquarry.storage.CrateTier.values().length - 1)]),
			net.minecraft.network.codec.ByteBufCodecs.VAR_INT.cast())
	);

	private ModMenus() {
	}

	public static void init() {
	}
}
