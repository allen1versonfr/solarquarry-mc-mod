package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.blockentity.SortingTerminalBlockEntity;
import com.solarquarry.menu.SortingTerminalMenu;
import com.solarquarry.pipe.SortRule;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: a Sorting Terminal edit. Every request is checked against the menu the player has open. */
public record SortingActionPayload(BlockPos terminal, int action, BlockPos target, int value, String text) implements CustomPacketPayload {
	public static final int SELECT = 0;
	public static final int REGISTER = 1;
	public static final int UNREGISTER = 2;
	public static final int NEXT_MODE = 3;
	public static final int PRIORITY = 4;
	public static final int TAG = 5;
	/** Switch to the storage view (no target needed). */
	public static final int OPEN_STORAGE = 6;

	public static final CustomPacketPayload.Type<SortingActionPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("sorting_action"));
	public static final StreamCodec<ByteBuf, SortingActionPayload> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, SortingActionPayload::terminal,
		ByteBufCodecs.VAR_INT, SortingActionPayload::action,
		BlockPos.STREAM_CODEC, SortingActionPayload::target,
		ByteBufCodecs.INT, SortingActionPayload::value,
		ByteBufCodecs.stringUtf8(104), SortingActionPayload::text,
		SortingActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void handle(SortingActionPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		if (!(player.containerMenu instanceof SortingTerminalMenu menu) || !menu.getPos().equals(payload.terminal()) || !menu.stillValid(player)) {
			return;
		}

		if (!(player.level().getBlockEntity(payload.terminal()) instanceof SortingTerminalBlockEntity terminal)) {
			return;
		}

		if (payload.action() == OPEN_STORAGE) {
			player.openMenu(com.solarquarry.storage.StorageTerminalMenu.provider(terminal));
			return;
		}

		apply(terminal, menu, payload.action(), payload.target(), payload.value(), payload.text());
	}

	/** Shared by the network handler and the self-test. */
	public static boolean apply(SortingTerminalBlockEntity terminal, SortingTerminalMenu menu, int action, BlockPos target, int value, String text) {
		if (!menu.isKnownTarget(target)) {
			return false;
		}

		SortRule rule = terminal.getRule(target);
		boolean ok = switch (action) {
			case SELECT -> {
				menu.select(target);
				yield true;
			}
			case REGISTER -> terminal.register(target);
			case UNREGISTER -> terminal.unregister(target);
			case NEXT_MODE -> {
				if (rule == null) {
					yield false;
				}

				rule.setMode(rule.mode().next());
				terminal.changed();
				yield true;
			}
			case PRIORITY -> {
				if (rule == null) {
					yield false;
				}

				rule.setPriority(rule.priority() + Integer.signum(value));
				terminal.changed();
				yield true;
			}
			case TAG -> {
				String clean = text == null ? "" : text.trim();
				String id = clean.startsWith("#") ? clean.substring(1) : clean;
				if (rule == null || value < 0 || value >= SortRule.MAX_TAGS || (!id.isEmpty() && Identifier.tryParse(id) == null)) {
					yield false;
				}

				rule.setTag(value, id);
				terminal.changed();
				yield true;
			}
			default -> false;
		};

		if (ok) {
			menu.afterRuleChange();
		}

		return ok;
	}
}
