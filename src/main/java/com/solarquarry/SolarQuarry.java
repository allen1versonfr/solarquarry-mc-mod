package com.solarquarry;

import com.solarquarry.config.SolarQuarryCommand;
import com.solarquarry.config.SolarQuarryConfig;
import com.solarquarry.energy.EnergyNetwork;
import com.solarquarry.item.ProspectorScans;
import com.solarquarry.network.ConfigSyncPayload;
import com.solarquarry.quarry.QuarryChunkLoading;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import com.solarquarry.network.FilterConfigPayload;
import com.solarquarry.network.QuarryConfigPayload;
import com.solarquarry.network.TerminalActionPayload;
import com.solarquarry.network.TerminalSnapshotPayload;
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
		SolarQuarryConfig.load(FabricLoader.getInstance().getConfigDir());
		ModSounds.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();
		QuarryChunkLoading.init();
		net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((tank, side) -> tank.storage, ModBlockEntities.LIQUID_TANK);
		net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((quarry, side) -> quarry.pumpTank, ModBlockEntities.QUARRY);

		PayloadTypeRegistry.serverboundPlay().register(QuarryConfigPayload.TYPE, QuarryConfigPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuarryConfigPayload.TYPE, QuarryConfigPayload::handle);
		PayloadTypeRegistry.serverboundPlay().register(FilterConfigPayload.TYPE, FilterConfigPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FilterConfigPayload.TYPE, FilterConfigPayload::handle);
		PayloadTypeRegistry.serverboundPlay().register(TerminalActionPayload.TYPE, TerminalActionPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TerminalActionPayload.TYPE, TerminalActionPayload::handle);
		PayloadTypeRegistry.clientboundPlay().register(TerminalSnapshotPayload.TYPE, TerminalSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(com.solarquarry.network.SortingSnapshotPayload.TYPE, com.solarquarry.network.SortingSnapshotPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(com.solarquarry.network.SortingActionPayload.TYPE, com.solarquarry.network.SortingActionPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(com.solarquarry.network.SortingActionPayload.TYPE, com.solarquarry.network.SortingActionPayload::handle);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SolarQuarryCommand.sync(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SolarQuarryCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(QuarryChunkLoading::restoreAll);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ProspectorScans.tick(server);
			QuarryChunkLoading.prune(server);
		});
		// Route caches hold level references: drop them when a server stops.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PipeRouter.invalidateCache();
			EnergyNetwork.markTopologyChanged();
			ProspectorScans.clear();
		});

		if (Boolean.getBoolean("solarquarry.selftest")) {
			SelfTest.register();
		}

		LOGGER.info("Solar Quarry initialized");
	}
}
