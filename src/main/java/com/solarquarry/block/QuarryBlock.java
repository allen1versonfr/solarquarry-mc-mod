package com.solarquarry.block;

import com.mojang.serialization.MapCodec;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.quarry.QuarryLamp;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.particles.ParticleTypes;
import org.jspecify.annotations.Nullable;

public class QuarryBlock extends MachineBlock implements EnergyConnectable, ItemConnectable {
	public static final MapCodec<QuarryBlock> CODEC = simpleCodec(QuarryBlock::new);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<QuarryLamp> LAMP = EnumProperty.create("lamp", QuarryLamp.class);

	public QuarryBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LAMP, QuarryLamp.OFF));
	}

	@Override
	protected MapCodec<QuarryBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LAMP);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new QuarryBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (!level.isClientSide() && by != null && level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
			quarry.setOwner(by);
		}
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.QUARRY, QuarryBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) {
			player.openMenu(quarry);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		QuarryLamp lamp = state.getValue(LAMP);
		if (lamp == QuarryLamp.GREEN && random.nextInt(4) == 0) {
			double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
			level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0.0, 0.03, 0.0);
		} else if (lamp == QuarryLamp.RED && random.nextInt(10) == 0) {
			Direction facing = state.getValue(FACING);
			double x = pos.getX() + 0.5 + facing.getStepX() * 0.52;
			double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 0.8, z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry ? quarry.getComparatorOutput() : 0;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		net.minecraft.world.Containers.updateNeighboursAfterDestroy(state, level, pos);
	}
}
