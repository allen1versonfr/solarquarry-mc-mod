package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.menu.FilterPipeMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: sets one item-tag rule of the filter pipe whose menu the player has open. */
public record FilterConfigPayload(BlockPos pos, int index, String tag) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FilterConfigPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("filter_config"));
	public static final StreamCodec<ByteBuf, FilterConfigPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, FilterConfigPayload::pos,
		ByteBufCodecs.VAR_INT, FilterConfigPayload::index,
		ByteBufCodecs.stringUtf8(ItemPipeBlockEntity.MAX_TAG_LENGTH + 8), FilterConfigPayload::tag,
		FilterConfigPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(FilterConfigPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!(player.containerMenu instanceof FilterPipeMenu menu) || !menu.getPos().equals(payload.pos()) || !menu.stillValid(player)) {
			return;
		}

		if (player.level().getBlockEntity(payload.pos()) instanceof ItemPipeBlockEntity pipe && pipe.isFilter()) {
			pipe.setTag(payload.index(), payload.tag());
		}
	}
}
