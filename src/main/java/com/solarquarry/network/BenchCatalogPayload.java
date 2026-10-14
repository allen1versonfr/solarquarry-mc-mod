package com.solarquarry.network;

import com.solarquarry.SolarQuarry;
import com.solarquarry.crafting.BenchRecipes;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Server to client: the Fabrication Bench catalogue, sent when the bench is opened. */
public record BenchCatalogPayload(List<BenchRecipes.Entry> entries) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BenchCatalogPayload> TYPE = new CustomPacketPayload.Type<>(SolarQuarry.id("bench_catalog"));
	private static final StreamCodec<RegistryFriendlyByteBuf, BenchRecipes.Need> NEED_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(16)), BenchRecipes.Need::options,
		ByteBufCodecs.VAR_INT, BenchRecipes.Need::count,
		BenchRecipes.Need::new
	);
	private static final StreamCodec<RegistryFriendlyByteBuf, BenchRecipes.Entry> ENTRY_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(256), BenchRecipes.Entry::id,
		ItemStack.OPTIONAL_STREAM_CODEC, BenchRecipes.Entry::result,
		NEED_CODEC.apply(ByteBufCodecs.list(9)), BenchRecipes.Entry::needs,
		BenchRecipes.Entry::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, BenchCatalogPayload> STREAM_CODEC = StreamCodec.composite(
		ENTRY_CODEC.apply(ByteBufCodecs.list(512)), BenchCatalogPayload::entries,
		BenchCatalogPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
