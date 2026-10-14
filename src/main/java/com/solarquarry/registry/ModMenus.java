package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.menu.FilterPipeMenu;
import com.solarquarry.menu.QuarryMenu;
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

	private ModMenus() {
	}

	public static void init() {
	}
}
