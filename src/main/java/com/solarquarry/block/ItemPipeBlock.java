package com.solarquarry.block;

import com.mojang.serialization.MapCodec;
import com.solarquarry.blockentity.ItemPipeBlockEntity;
import com.solarquarry.item.WrenchItem;
import com.solarquarry.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ItemPipeBlock extends BaseEntityBlock {
	public static final MapCodec<ItemPipeBlock> CODEC = simpleCodec(p -> new ItemPipeBlock(false, p));
	public static final MapCodec<ItemPipeBlock> FILTER_CODEC = simpleCodec(p -> new ItemPipeBlock(true, p));
	private static final ConnectionShapes SHAPES = new ConnectionShapes(5, 11);
	private final boolean filter;

	public ItemPipeBlock(boolean filter, Properties properties) {
		super(properties);
		this.filter = filter;
		BlockState state = this.stateDefinition.any();
		for (BooleanProperty property : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, false);
		}

		this.registerDefaultState(state);
	}

	public boolean isFilter() {
		return this.filter;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return this.filter ? FILTER_CODEC : CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
	}

	/** Pipes connect to other pipes, to quarries and to anything exposing an item inventory on that face. */
	public static boolean connectsTo(BlockGetter level, BlockPos pos, Direction dir, BlockState neighbor) {
		if (neighbor.getBlock() instanceof ItemPipeBlock || neighbor.getBlock() instanceof ItemConnectable) {
			return true;
		}

		if (neighbor.isAir()) {
			return false;
		}

		BlockPos neighborPos = pos.relative(dir);
		if (level instanceof Level realLevel) {
			return ItemStorage.SIDED.find(realLevel, neighborPos, neighbor, null, dir.getOpposite()) != null;
		}

		return neighbor.hasBlockEntity() && level.getBlockEntity(neighborPos) instanceof Container;
	}

	private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction dir : Direction.values()) {
			BlockState neighbor = level.getBlockState(pos.relative(dir));
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir, neighbor));
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
		if (level instanceof Level real && !real.isClientSide()) {
			// A neighbour appeared, vanished or changed: cached routes may point at it.
			com.solarquarry.pipe.PipeRouter.invalidateCache();
		}

		return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), connectsTo(level, pos, directionToNeighbour, neighbourState));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide()) {
			com.solarquarry.pipe.PipeRouter.invalidateCache();
		}
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ItemPipeBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ITEM_PIPE, ItemPipeBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (itemStack.getItem() instanceof com.solarquarry.item.MachineTool) {
			return InteractionResult.PASS;
		}

		return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!this.filter) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ItemPipeBlockEntity pipe) {
			player.openMenu(pipe);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}
}
