package com.solarquarry.block;

import com.mojang.serialization.MapCodec;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.energy.EnergyNetwork;
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

public class EnergyCableBlock extends Block {
	public static final MapCodec<EnergyCableBlock> CODEC = simpleCodec(EnergyCableBlock::new);
	private static final ConnectionShapes SHAPES = new ConnectionShapes(6, 10);

	public EnergyCableBlock(Properties properties) {
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

	public static boolean connectsTo(BlockState neighbor) {
		return neighbor.getBlock() instanceof EnergyCableBlock || neighbor.getBlock() instanceof EnergyConnectable;
	}

	private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level.getBlockState(pos.relative(dir))));
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
		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), connectsTo(neighbourState));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(this)) {
			EnergyNetwork.markTopologyChanged();
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		EnergyNetwork.markTopologyChanged();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
