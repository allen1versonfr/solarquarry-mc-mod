package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.config.SolarQuarryConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the server's Solar Quarry settings, sent on join and after {@code /solarquarry reload}. */
public record ConfigSyncPayload(SolarQuarryConfig config) implements CustomPacketPayload {
	public static final StreamCodec<ByteBuf, SolarQuarryConfig> CONFIG_CODEC = new StreamCodec<>() {
		@Override
		public SolarQuarryConfig decode(ByteBuf buf) {
			return new SolarQuarryConfig(
				ByteBufCodecs.DOUBLE.decode(buf),
				ByteBufCodecs.DOUBLE.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.BOOL.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.DOUBLE.decode(buf)
			).validated();
		}

		@Override
		public void encode(ByteBuf buf, SolarQuarryConfig config) {
			ByteBufCodecs.DOUBLE.encode(buf, config.energyCostMultiplier());
			ByteBufCodecs.DOUBLE.encode(buf, config.solarOutputMultiplier());
			ByteBufCodecs.VAR_INT.encode(buf, config.maxRangeTier());
			ByteBufCodecs.BOOL.encode(buf, config.allowChunkLoading());
			ByteBufCodecs.VAR_INT.encode(buf, config.chunkLoaderUpkeep());
			ByteBufCodecs.BOOL.encode(buf, config.respectProtection());
			ByteBufCodecs.VAR_INT.encode(buf, config.prospectorCooldownTicks());
			ByteBufCodecs.VAR_INT.encode(buf, config.terminalMaxQuarries());
			ByteBufCodecs.DOUBLE.encode(buf, config.machineSoundVolume());
		}
	};

	public static final CustomPacketPayload.Type<ConfigSyncPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("config_sync"));
	public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.ofMember(
		(payload, buf) -> CONFIG_CODEC.encode(buf, payload.config),
		buf -> new ConfigSyncPayload(CONFIG_CODEC.decode(buf))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
