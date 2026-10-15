package com.solarquarry.splitter;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.ItemConnectable;
import com.solarquarry.block.MachineBlock;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Filtered Splitter block. The block state only mirrors the two output faces (green plate: matching items, orange
 * plate: everything else) so they are visible in the world; the block entity owns the settings.
 */
public class FilteredSplitterBlock extends MachineBlock implements ItemConnectable {
	public static final MapCodec<FilteredSplitterBlock> CODEC = simpleCodec(FilteredSplitterBlock::new);
	public static final EnumProperty<Direction> MATCH = EnumProperty.create("match", Direction.class);
	public static final EnumProperty<Direction> OTHER = EnumProperty.create("other", Direction.class);

	public FilteredSplitterBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(MATCH, Direction.NORTH).setValue(OTHER, Direction.EAST));
	}

	@Override
	protected MapCodec<FilteredSplitterBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MATCH, OTHER);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction look = context.getHorizontalDirection();
		return this.defaultBlockState().setValue(MATCH, look).setValue(OTHER, look.getClockWise());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, placer, itemStack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FilteredSplitterBlockEntity splitter) {
			splitter.orient(state.getValue(MATCH));
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new FilteredSplitterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.FILTERED_SPLITTER, FilteredSplitterBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FilteredSplitterBlockEntity splitter) {
			player.openMenu(splitter);
		}

		return InteractionResult.SUCCESS;
	}
}
