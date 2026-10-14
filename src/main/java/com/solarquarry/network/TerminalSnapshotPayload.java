package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: what a remote terminal currently sees. Sent only to players with that terminal open. */
public record TerminalSnapshotPayload(BlockPos terminal, long gameTime, List<Entry> machines) implements CustomPacketPayload {
	public static final int FLAG_AUTHORIZED = 1;
	public static final int FLAG_OUTPUT_CONNECTED = 2;
	public static final int FLAG_LOW_SPACE = 4;

	public record Entry(BlockPos pos, int status, int runMode, int quarryMode, long energy, long capacity, int progress,
		int bufferUsed, int width, int length, int currentY, int targetY, long blocksMined, int flags, String owner,
		int inventories, int usedSlots, int totalSlots, int powerIn, int powerUse) {
		public boolean authorized() {
			return (this.flags & FLAG_AUTHORIZED) != 0;
		}

		public boolean outputConnected() {
			return (this.flags & FLAG_OUTPUT_CONNECTED) != 0;
		}

		public boolean lowSpace() {
			return (this.flags & FLAG_LOW_SPACE) != 0;
		}

		void write(FriendlyByteBuf buf) {
			buf.writeBlockPos(this.pos);
			buf.writeVarInt(this.status);
			buf.writeVarInt(this.runMode);
			buf.writeVarInt(this.quarryMode);
			buf.writeVarLong(this.energy);
			buf.writeVarLong(this.capacity);
			buf.writeVarInt(this.progress);
			buf.writeVarInt(this.bufferUsed);
			buf.writeVarInt(this.width);
			buf.writeVarInt(this.length);
			buf.writeInt(this.currentY);
			buf.writeInt(this.targetY);
			buf.writeVarLong(this.blocksMined);
			buf.writeVarInt(this.flags);
			buf.writeUtf(this.owner, 32);
			buf.writeVarInt(this.inventories);
			buf.writeVarInt(this.usedSlots);
			buf.writeVarInt(this.totalSlots);
			buf.writeVarInt(this.powerIn);
			buf.writeVarInt(this.powerUse);
		}

		static Entry read(FriendlyByteBuf buf) {
			return new Entry(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarLong(),
				buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}
	}

	public static final CustomPacketPayload.Type<TerminalSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("terminal_snapshot"));
	public static final StreamCodec<FriendlyByteBuf, TerminalSnapshotPayload> STREAM_CODEC = StreamCodec.ofMember(TerminalSnapshotPayload::write, TerminalSnapshotPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(this.terminal);
		buf.writeVarLong(this.gameTime);
		buf.writeVarInt(this.machines.size());
		for (Entry entry : this.machines) {
			entry.write(buf);
		}
	}

	private static TerminalSnapshotPayload read(FriendlyByteBuf buf) {
		BlockPos terminal = buf.readBlockPos();
		long time = buf.readVarLong();
		int count = Math.min(buf.readVarInt(), 64);
		List<Entry> list = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			list.add(Entry.read(buf));
		}

		return new TerminalSnapshotPayload(terminal, time, list);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
