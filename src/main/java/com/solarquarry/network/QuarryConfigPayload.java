package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.menu.QuarryMenu;
import com.solarquarry.quarry.QuarryMode;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: changes one setting of the quarry whose menu the player has open.
 * The server re-validates and clamps every value; the client never sets inventories or energy.
 */
public record QuarryConfigPayload(BlockPos pos, int setting, int value) implements CustomPacketPayload {
	public static final int TARGET_Y = 0;
	public static final int MODE = 1;
	public static final int WIDTH = 2;
	public static final int LENGTH = 3;
	public static final int HEIGHT = 4;
	public static final int ORES_ONLY = 5;

	public static final CustomPacketPayload.Type<QuarryConfigPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("quarry_config"));
	public static final StreamCodec<ByteBuf, QuarryConfigPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, QuarryConfigPayload::pos,
		ByteBufCodecs.VAR_INT, QuarryConfigPayload::setting,
		ByteBufCodecs.VAR_INT, QuarryConfigPayload::value,
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

		if (!(player.level().getBlockEntity(payload.pos()) instanceof QuarryBlockEntity quarry)) {
			return;
		}

		apply(quarry, payload.setting(), payload.value());
	}

	/** Applies one validated setting. Shared with the remote terminal. */
	public static void apply(QuarryBlockEntity quarry, int setting, int value) {
		switch (setting) {
			case TARGET_Y -> quarry.setTargetY(value);
			case MODE -> {
				if (value >= 0 && value < QuarryMode.values().length) {
					quarry.setQuarryMode(QuarryMode.byOrdinal(value));
				}
			}
			case WIDTH -> quarry.setWidth(value);
			case LENGTH -> quarry.setLength(value);
			case HEIGHT -> quarry.setHeight(value);
			case ORES_ONLY -> quarry.setOresOnly(value != 0);
			default -> {
			}
		}
	}
}
