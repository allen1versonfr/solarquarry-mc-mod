package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Server to client: the Storage Terminal's combined contents, sent only to the player who has it open. */
public record StorageSnapshotPayload(BlockPos terminal, List<Entry> entries, int inventories, int usedSlots, int totalSlots, boolean truncated)
	implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 1024;

	/** One kind of item (with its components) and how many the network holds. */
	public record Entry(ItemStack stack, long amount) {
	}

	public static final CustomPacketPayload.Type<StorageSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("storage_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StorageSnapshotPayload> STREAM_CODEC = StreamCodec.ofMember(StorageSnapshotPayload::write, StorageSnapshotPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBlockPos(this.terminal);
		buf.writeVarInt(this.entries.size());
		for (Entry entry : this.entries) {
			ItemStack.STREAM_CODEC.encode(buf, entry.stack().copyWithCount(1));
			buf.writeVarLong(entry.amount());
		}

		buf.writeVarInt(this.inventories);
		buf.writeVarInt(this.usedSlots);
		buf.writeVarInt(this.totalSlots);
		buf.writeBoolean(this.truncated);
	}

	private static StorageSnapshotPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos terminal = buf.readBlockPos();
		int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(ItemStack.STREAM_CODEC.decode(buf), buf.readVarLong()));
		}

		return new StorageSnapshotPayload(terminal, entries, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
