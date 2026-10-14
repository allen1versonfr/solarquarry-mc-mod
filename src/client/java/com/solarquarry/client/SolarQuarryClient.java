package com.solarquarry.client;

import com.solarquarry.client.render.ItemPipeRenderer;
import com.solarquarry.client.render.QuarryRenderer;
import com.solarquarry.client.screen.FilterPipeScreen;
import com.solarquarry.client.screen.QuarryScreen;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class SolarQuarryClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.QUARRY, QuarryScreen::new);
		MenuScreens.register(ModMenus.FILTER_PIPE, FilterPipeScreen::new);
		BlockEntityRenderers.register(ModBlockEntities.QUARRY, QuarryRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.ITEM_PIPE, ItemPipeRenderer::new);
	}
}
