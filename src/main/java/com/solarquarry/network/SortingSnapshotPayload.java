package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the Sorting Terminal's destinations, sent only to the player who has it open. */
public record SortingSnapshotPayload(BlockPos terminal, List<Entry> entries, long selected, List<String> selectedTags, boolean hasFallback)
	implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 64;
	public static final int FLAG_REGISTERED = 1;
	public static final int FLAG_PRESENT = 2;
	public static final int FLAG_LOADED = 4;
	public static final int FLAG_HAS_ENTRIES = 8;

	public record Entry(BlockPos pos, String icon, int flags, int mode, int priority, int usedSlots, int totalSlots) {
		public boolean registered() {
			return (this.flags & FLAG_REGISTERED) != 0;
		}

		public boolean present() {
			return (this.flags & FLAG_PRESENT) != 0;
		}

		public boolean loaded() {
			return (this.flags & FLAG_LOADED) != 0;
		}

		public boolean hasEntries() {
			return (this.flags & FLAG_HAS_ENTRIES) != 0;
		}
	}

	public static final CustomPacketPayload.Type<SortingSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("sorting_snapshot"));
	public static final StreamCodec<FriendlyByteBuf, SortingSnapshotPayload> STREAM_CODEC = StreamCodec.ofMember(SortingSnapshotPayload::write, SortingSnapshotPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.terminal);
		buf.writeVarInt(this.entries.size());
		for (Entry entry : this.entries) {
			buf.writeBlockPos(entry.pos());
			buf.writeUtf(entry.icon(), 128);
			buf.writeVarInt(entry.flags());
			buf.writeVarInt(entry.mode());
			buf.writeInt(entry.priority());
			buf.writeVarInt(entry.usedSlots());
			buf.writeVarInt(entry.totalSlots());
		}

		buf.writeLong(this.selected);
		for (int i = 0; i < 3; i++) {
			buf.writeUtf(i < this.selectedTags.size() ? this.selectedTags.get(i) : "", 96);
		}

		buf.writeBoolean(this.hasFallback);
	}

	private static SortingSnapshotPayload read(FriendlyByteBuf buf) {
		BlockPos terminal = buf.readBlockPos();
		int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
		List<Entry> entries = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readBlockPos(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));
		}

		long selected = buf.readLong();
		List<String> tags = new ArrayList<>(3);
		for (int i = 0; i < 3; i++) {
			tags.add(buf.readUtf(96));
		}

		return new SortingSnapshotPayload(terminal, entries, selected, tags, buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
