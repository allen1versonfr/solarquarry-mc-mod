package com.solarquarry.fluid;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.ConnectionShapes;
import com.solarquarry.block.QuarryBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fluid pipe: carries what a quarry's Liquid Remover pumps to Liquid Tanks (or any block that accepts fluids through
 * the Fabric transfer API). The pipe holds nothing itself; the quarry pushes straight through the connected pipes.
 */
public class FluidPipeBlock extends Block {
	public static final MapCodec<FluidPipeBlock> CODEC = simpleCodec(FluidPipeBlock::new);
	private static final ConnectionShapes SHAPES = new ConnectionShapes(5, 11);

	public FluidPipeBlock(Properties properties) {
		super(properties);
		BlockState state = this.stateDefinition.any();
		for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, false);
		}

		this.registerDefaultState(state);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
	}

	private static boolean connectsTo(LevelReader level, BlockPos pos, Direction dir) {
		BlockPos neighbor = pos.relative(dir);
		BlockState state = level.getBlockState(neighbor);
		if (state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof LiquidTankBlock || state.getBlock() instanceof QuarryBlock) {
			return true;
		}

		return level instanceof Level world && state.hasBlockEntity() && FluidStorage.SIDED.find(world, neighbor, dir.getOpposite()) != null;
	}

	private BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir));
		}

		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.withConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
	}

	@Override
	protected BlockState updateShape(
		BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
	) {
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), connectsTo(level, pos, directionToNeighbour));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this)) {
			FluidNetwork.markChanged();
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		FluidNetwork.markChanged();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
