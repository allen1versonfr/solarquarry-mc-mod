package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.splitter.FilteredSplitterBlockEntity;
import com.solarquarry.splitter.FilteredSplitterMenu;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Client to server, for the Filtered Splitter whose menu the player has open: set an item-tag rule
 * ({@link #SET_TAG}) or add an item picked in the search list to the filter ({@link #ADD_ITEM}).
 */
public record SplitterActionPayload(BlockPos pos, int action, int index, String text) implements CustomPacketPayload {
	public static final int SET_TAG = 0;
	public static final int ADD_ITEM = 1;
	public static final CustomPacketPayload.Type<SplitterActionPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("splitter_action"));
	public static final StreamCodec<ByteBuf, SplitterActionPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SplitterActionPayload::pos,
		ByteBufCodecs.VAR_INT, SplitterActionPayload::action,
		ByteBufCodecs.VAR_INT, SplitterActionPayload::index,
		ByteBufCodecs.stringUtf8(FilteredSplitterBlockEntity.MAX_TAG_LENGTH + 8), SplitterActionPayload::text,
		SplitterActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SplitterActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!(player.containerMenu instanceof FilteredSplitterMenu menu) || !menu.getPos().equals(payload.pos()) || !menu.stillValid(player)) {
			return;
		}

		if (!(player.level().getBlockEntity(payload.pos()) instanceof FilteredSplitterBlockEntity splitter)) {
			return;
		}

		if (payload.action() == SET_TAG) {
			splitter.setTag(payload.index(), payload.text());
		} else if (payload.action() == ADD_ITEM) {
			Identifier id = Identifier.tryParse(payload.text());
			if (id != null) {
				BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR)
					.ifPresent(item -> FilteredSplitterMenu.addTemplate(splitter.getFilter(), new ItemStack(item)));
			}
		}
	}
}
