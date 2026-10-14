package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		SolarQuarry.id("main"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.solarquarry.main"))
			.icon(() -> new ItemStack(ModBlocks.QUARRY))
			.displayItems((parameters, output) -> {
				for (Item item : ModBlocks.BLOCK_ITEMS) {
					output.accept(item);
				}

				for (Item item : ModItems.ITEMS) {
					output.accept(item);
				}
			})
			.build()
	);

	private ModCreativeTab() {
	}

	public static void init() {
	}
}
