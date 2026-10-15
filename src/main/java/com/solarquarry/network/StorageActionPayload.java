package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.storage.StorageTerminalMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Client to server: a Storage Terminal request. Only the item kind travels; the server decides the amount from what
 * the network really holds and what the player has room for, so a modified client cannot create items.
 */
public record StorageActionPayload(BlockPos terminal, int action, ItemStack item, int amount) implements CustomPacketPayload {
	public static final int WITHDRAW_TO_CURSOR = 0;
	public static final int WITHDRAW_TO_INVENTORY = 1;
	public static final int DEPOSIT_CURSOR = 2;
	public static final int DEPOSIT_ONE_FROM_CURSOR = 3;
	public static final int DEPOSIT_INVENTORY = 4;
	public static final int OPEN_RULES = 5;
	public static final int REFRESH = 6;

	public static final CustomPacketPayload.Type<StorageActionPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("storage_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, StorageActionPayload::terminal,
		ByteBufCodecs.VAR_INT, StorageActionPayload::action,
		ItemStack.OPTIONAL_STREAM_CODEC, StorageActionPayload::item,
		ByteBufCodecs.VAR_INT, StorageActionPayload::amount,
		StorageActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(StorageActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!(player.containerMenu instanceof StorageTerminalMenu menu) || !menu.getPos().equals(payload.terminal()) || !menu.stillValid(player)) {
			return;
		}

		menu.perform(player, payload.action(), payload.item(), payload.amount());
	}
}
