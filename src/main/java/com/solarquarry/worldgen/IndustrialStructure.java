package com.solarquarry.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * One structure type for all ten Solar Quarry structures; the {@link Layout} picks the building. Surface structures
 * need ground that is dry and no more uneven than the layout allows (they get a foundation down to the ground);
 * underground ones are dug in at a fixed depth below the surface. Rarity, spacing and biomes come from the data pack
 * (structure set and biome tag), so they can be changed without code.
 */
public class IndustrialStructure extends Structure {
	public static final MapCodec<IndustrialStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Layout.CODEC.fieldOf("layout").forGetter(s -> s.layout)
	).apply(i, IndustrialStructure::new));

	private final Layout layout;

	public IndustrialStructure(StructureSettings settings, Layout layout) {
		super(settings);
		this.layout = layout;
	}

	public Layout layout() {
		return this.layout;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int cx = context.chunkPos().getMiddleBlockX();
		int cz = context.chunkPos().getMiddleBlockZ();
		int x0 = cx - this.layout.sizeX / 2;
		int z0 = cz - this.layout.sizeZ / 2;
		int[] xs = {x0, x0 + this.layout.sizeX - 1, x0, x0 + this.layout.sizeX - 1, cx};
		int[] zs = {z0, z0, z0 + this.layout.sizeZ - 1, z0 + this.layout.sizeZ - 1, cz};
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < xs.length; i++) {
			int h = context.chunkGenerator().getFirstOccupiedHeight(xs[i], zs[i], Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
			min = Math.min(min, h);
			max = Math.max(max, h);
			var column = context.chunkGenerator().getBaseColumn(xs[i], zs[i], context.heightAccessor(), context.randomState());
			if (!this.layout.underground() && !column.getBlock(h).getFluidState().isEmpty()) {
				return Optional.empty(); // water or lava on the site
			}
		}

		int floor;
		if (this.layout.underground()) {
			floor = Math.max(context.heightAccessor().getMinY() + 6, min - this.layout.depth);
			if (min - floor < this.layout.depth) {
				return Optional.empty(); // too close to the bottom of the world here
			}
		} else {
			if (max - min > this.layout.maxSlope || min <= context.heightAccessor().getMinY() + 2) {
				return Optional.empty();
			}

			floor = context.chunkGenerator().getFirstOccupiedHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		}

		BlockPos origin = new BlockPos(x0, floor, z0);
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new IndustrialPiece(this.layout, origin))));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.INDUSTRIAL;
	}
}
