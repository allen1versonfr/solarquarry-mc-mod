package com.solarquarry.turret;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.MachineBlock;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Laser Turret block: a hazard-striped base with an armoured emitter head (see {@link LaserTurretBlockEntity}). */
public class LaserTurretBlock extends MachineBlock implements EnergyConnectable {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(4, 6, 4, 12, 9, 12), Block.box(3, 9, 3, 13, 15, 13));
	private final TurretTier tier;
	private final MapCodec<LaserTurretBlock> codec;

	public LaserTurretBlock(TurretTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		this.codec = simpleCodec(p -> new LaserTurretBlock(tier, p));
	}

	public TurretTier tier() {
		return this.tier;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return this.codec;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LaserTurretBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.LASER_TURRET, LaserTurretBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LaserTurretBlockEntity turret) {
			player.openMenu(turret);
		}

		return InteractionResult.SUCCESS;
	}
}
