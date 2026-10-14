package com.solarquarry.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Precomputed voxel shapes for six-way connecting blocks (cables and pipes). */
public final class ConnectionShapes {
	private final VoxelShape[] shapes = new VoxelShape[64];

	public ConnectionShapes(double min, double max) {
		VoxelShape core = Block.box(min, min, min, max, max, max);
		Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
		arms.put(Direction.DOWN, Block.box(min, 0, min, max, min, max));
		arms.put(Direction.UP, Block.box(min, max, min, max, 16, max));
		arms.put(Direction.NORTH, Block.box(min, min, 0, max, max, min));
		arms.put(Direction.SOUTH, Block.box(min, min, max, max, max, 16));
		arms.put(Direction.WEST, Block.box(0, min, min, min, max, max));
		arms.put(Direction.EAST, Block.box(max, min, min, 16, max, max));

		for (int mask = 0; mask < 64; mask++) {
			VoxelShape shape = core;
			for (Direction dir : Direction.values()) {
				if ((mask & (1 << dir.ordinal())) != 0) {
					shape = Shapes.or(shape, arms.get(dir));
				}
			}

			this.shapes[mask] = shape.optimize();
		}
	}

	public VoxelShape get(BlockState state) {
		int mask = 0;
		for (Direction dir : Direction.values()) {
			BooleanProperty property = PipeBlock.PROPERTY_BY_DIRECTION.get(dir);
			if (state.hasProperty(property) && state.getValue(property)) {
				mask |= 1 << dir.ordinal();
			}
		}

		return this.shapes[mask];
	}
}
