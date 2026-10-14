package com.solarquarry.client;

import com.solarquarry.client.render.ItemPipeRenderer;
import com.solarquarry.client.render.QuarryRenderer;
import com.solarquarry.client.screen.FilterPipeScreen;
import com.solarquarry.client.screen.QuarryScreen;
import com.solarquarry.client.screen.TerminalScreen;
import com.solarquarry.config.SolarQuarryConfig;
import com.solarquarry.network.ConfigSyncPayload;
import com.solarquarry.network.TerminalSnapshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import com.solarquarry.item.TooltipLines;
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
		MenuScreens.register(ModMenus.FABRICATION_BENCH, com.solarquarry.client.screen.FabricationBenchScreen::new);
		MenuScreens.register(ModMenus.CRATE, com.solarquarry.client.screen.CrateScreen::new);
		MenuScreens.register(ModMenus.SUPER_SMELTER, com.solarquarry.client.screen.SuperSmelterScreen::new);
		MenuScreens.register(ModMenus.TERMINAL, TerminalScreen::new);
		MenuScreens.register(ModMenus.SORTING_TERMINAL, com.solarquarry.client.screen.SortingTerminalScreen::new);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof TooltipLines tooltip) {
				tooltip.addTooltipLines(stack, lines::add);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TerminalSnapshotPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof TerminalScreen screen) {
				screen.accept(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.solarquarry.network.BenchCatalogPayload.TYPE, (payload, context) -> {
			com.solarquarry.client.screen.FabricationBenchScreen.catalogue = java.util.List.copyOf(payload.entries());
			if (context.client().gui.screen() instanceof com.solarquarry.client.screen.FabricationBenchScreen screen) {
				screen.refresh();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(com.solarquarry.network.SortingSnapshotPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof com.solarquarry.client.screen.SortingTerminalScreen screen) {
				screen.accept(payload);
			}
		});
		// The server's settings apply while connected; this machine's own file applies again after leaving.
		ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.TYPE, (payload, context) -> SolarQuarryConfig.applyFromServer(payload.config()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SolarQuarryConfig.restoreLocal());
		BlockEntityRenderers.register(ModBlockEntities.QUARRY, QuarryRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.ITEM_PIPE, ItemPipeRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.LIQUID_TANK, com.solarquarry.client.render.LiquidTankRenderer::new);
	}
}
