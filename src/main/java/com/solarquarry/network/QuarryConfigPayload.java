package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.menu.QuarryMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: changes the quarry's target depth from the configuration page. */
public record QuarryConfigPayload(BlockPos pos, int targetY) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<QuarryConfigPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("quarry_config"));
	public static final StreamCodec<ByteBuf, QuarryConfigPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, QuarryConfigPayload::pos,
		ByteBufCodecs.VAR_INT, QuarryConfigPayload::targetY,
		QuarryConfigPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(QuarryConfigPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		// Only accept changes for the quarry whose menu this player legitimately has open.
		if (!(player.containerMenu instanceof QuarryMenu menu) || !menu.getPos().equals(payload.pos()) || !menu.stillValid(player)) {
			return;
		}

		if (player.level().getBlockEntity(payload.pos()) instanceof QuarryBlockEntity quarry) {
			quarry.setTargetY(payload.targetY());
		}
	}
}
