package com.solarquarry.weather;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.MachineBlock;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Weather Control Machine block: the capacitor base is the block; the rings, antennae, arms and core above it are
 * drawn by its renderer and move with the machine's real state.
 */
public class WeatherMachineBlock extends MachineBlock implements EnergyConnectable {
	public static final MapCodec<WeatherMachineBlock> CODEC = simpleCodec(WeatherMachineBlock::new);
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

	public WeatherMachineBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<WeatherMachineBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WeatherMachineBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.WEATHER_MACHINE, WeatherMachineBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof WeatherMachineBlockEntity machine) {
			player.openMenu(machine);
		}

		return InteractionResult.SUCCESS;
	}
}
