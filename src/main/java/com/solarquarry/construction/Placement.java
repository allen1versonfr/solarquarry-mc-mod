package com.solarquarry.construction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One block of a blueprint, already resolved to a world position. */
public record Placement(BlockPos pos, BlockState state) {
	public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("pos").forGetter(Placement::pos),
		BlockState.CODEC.fieldOf("state").forGetter(Placement::state)
	).apply(i, Placement::new));
	public static final Codec<List<Placement>> LIST_CODEC = CODEC.listOf();
}
