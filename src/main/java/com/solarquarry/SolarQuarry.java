package com.solarquarry;

import com.solarquarry.energy.EnergyNetwork;
import com.solarquarry.network.FilterConfigPayload;
import com.solarquarry.network.QuarryConfigPayload;
import com.solarquarry.pipe.PipeRouter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModBlocks;
import com.solarquarry.registry.ModCreativeTab;
import com.solarquarry.registry.ModItems;
import com.solarquarry.registry.ModMenus;
import com.solarquarry.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolarQuarry implements ModInitializer {
	public static final String MOD_ID = "solarquarry";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();

		PayloadTypeRegistry.serverboundPlay().register(QuarryConfigPayload.TYPE, QuarryConfigPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuarryConfigPayload.TYPE, QuarryConfigPayload::handle);
		PayloadTypeRegistry.serverboundPlay().register(FilterConfigPayload.TYPE, FilterConfigPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FilterConfigPayload.TYPE, FilterConfigPayload::handle);
		// Route caches hold level references: drop them when a server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PipeRouter.invalidateCache();
			EnergyNetwork.markTopologyChanged();
		});

		if (Boolean.getBoolean("solarquarry.selftest")) {
			SelfTest.register();
		}

		LOGGER.info("Solar Quarry initialized");
	}
}
