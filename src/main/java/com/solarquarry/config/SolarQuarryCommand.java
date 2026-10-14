package com.solarquarry.config;

import com.mojang.brigadier.CommandDispatcher;
import com.solarquarry.network.ConfigSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /solarquarry config} shows the active settings to anyone; {@code /solarquarry reload} (operators) re-reads
 * {@code config/solarquarry.json} and sends the new values to every connected player.
 */
public final class SolarQuarryCommand {
	private SolarQuarryCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("solarquarry")
			.then(Commands.literal("config").executes(context -> {
				show(context.getSource());
				return 1;
			}))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
				SolarQuarryConfig.load(FabricLoader.getInstance().getConfigDir());
				syncAll(context.getSource().getServer());
				context.getSource().sendSuccess(() -> Component.translatable("solarquarry.command.reloaded").withStyle(ChatFormatting.GREEN), true);
				show(context.getSource());
				return 1;
			})));
	}

	private static void show(CommandSourceStack source) {
		SolarQuarryConfig c = SolarQuarryConfig.get();
		source.sendSystemMessage(Component.translatable("solarquarry.command.header").withStyle(ChatFormatting.AQUA));
		line(source, "energyCostMultiplier", String.valueOf(c.energyCostMultiplier()));
		line(source, "solarOutputMultiplier", String.valueOf(c.solarOutputMultiplier()));
		line(source, "maxRangeTier", c.maxRangeTier() + " (" + (16 << c.maxRangeTier()) + "x" + (16 << c.maxRangeTier()) + ")");
		line(source, "allowChunkLoading", String.valueOf(c.allowChunkLoading()));
		line(source, "chunkLoaderUpkeep", c.chunkLoaderUpkeep() + " E/t");
		line(source, "respectProtection", String.valueOf(c.respectProtection()));
		line(source, "prospectorCooldownTicks", String.valueOf(c.prospectorCooldownTicks()));
		line(source, "terminalMaxQuarries", String.valueOf(c.terminalMaxQuarries()));
		line(source, "machineSoundVolume", String.valueOf(c.machineSoundVolume()));
	}

	private static void line(CommandSourceStack source, String key, String value) {
		source.sendSystemMessage(Component.literal("  " + key + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE)));
	}

	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ConfigSyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ConfigSyncPayload(SolarQuarryConfig.get()));
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
		}
	}
}
