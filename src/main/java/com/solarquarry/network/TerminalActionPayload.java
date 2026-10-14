package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.blockentity.TerminalBlockEntity;
import com.solarquarry.menu.TerminalMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client to server: a control action sent from a remote terminal. The server checks that the player has that
 * terminal open, that the quarry is on the terminal's network right now, and that the player may control it.
 */
public record TerminalActionPayload(BlockPos terminal, BlockPos quarry, int action) implements CustomPacketPayload {
	public static final int START = 0;
	public static final int PAUSE = 1;
	public static final int RESUME = 2;
	public static final int RESTART = 3;
	public static final int MODE_NEXT = 4;
	public static final int DEPTH_DOWN = 5;
	public static final int DEPTH_UP = 6;

	public static final CustomPacketPayload.Type<TerminalActionPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("terminal_action"));
	public static final StreamCodec<ByteBuf, TerminalActionPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, TerminalActionPayload::terminal,
		BlockPos.STREAM_CODEC, TerminalActionPayload::quarry,
		ByteBufCodecs.VAR_INT, TerminalActionPayload::action,
		TerminalActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(TerminalActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!(player.containerMenu instanceof TerminalMenu menu) || !menu.getPos().equals(payload.terminal()) || !menu.stillValid(player)) {
			return;
		}

		if (!(player.level().getBlockEntity(payload.terminal()) instanceof TerminalBlockEntity terminal)) {
			return;
		}

		QuarryBlockEntity quarry = terminal.findQuarry(payload.quarry());
		if (quarry == null) {
			player.sendOverlayMessage(Component.translatable("solarquarry.terminal.gone"));
			return;
		}

		if (!quarry.isAuthorized(player)) {
			player.sendOverlayMessage(Component.translatable("solarquarry.terminal.locked", quarry.getOwnerName()));
			return;
		}

		switch (payload.action()) {
			case START -> quarry.start();
			case PAUSE -> quarry.pause();
			case RESUME -> quarry.resume();
			case RESTART -> quarry.resetProgress();
			case MODE_NEXT -> quarry.setQuarryMode(quarry.getQuarryMode().next());
			case DEPTH_DOWN -> quarry.setTargetY(quarry.effectiveTargetY() - 10);
			case DEPTH_UP -> quarry.setTargetY(quarry.effectiveTargetY() + 10);
			default -> {
				return;
			}
		}

		menu.requestRefresh();
	}
}
