package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.crafting.BenchRecipes;
import com.solarquarry.crafting.FabricationBenchMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: craft a catalogue recipe at the open Fabrication Bench (checked against the open menu). */
public record BenchCraftPayload(String recipe, int times) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BenchCraftPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("bench_craft"));
	public static final StreamCodec<ByteBuf, BenchCraftPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(256), BenchCraftPayload::recipe,
		ByteBufCodecs.VAR_INT, BenchCraftPayload::times,
		BenchCraftPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(BenchCraftPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (player.containerMenu instanceof FabricationBenchMenu menu && menu.stillValid(player)) {
			BenchRecipes.craft(player, payload.recipe(), payload.times());
		}
	}
}
