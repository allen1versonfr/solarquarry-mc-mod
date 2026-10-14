package com.solarquarry.block;

import com.mojang.serialization.MapCodec;
import com.solarquarry.blockentity.SolarPanelBlockEntity;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.energy.SolarTier;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SolarPanelBlock extends MachineBlock implements EnergyConnectable {
	public static final BooleanProperty GENERATING = BooleanProperty.create("generating");
	private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 6, 0, 16, 9, 16), Block.box(3, 0, 3, 13, 6, 13));
	private final SolarTier tier;
	private final MapCodec<SolarPanelBlock> codec;

	public SolarPanelBlock(SolarTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		this.codec = simpleCodec(p -> new SolarPanelBlock(tier, p));
		this.registerDefaultState(this.stateDefinition.any().setValue(GENERATING, false));
	}

	public SolarTier getTier() {
		return this.tier;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return this.codec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(GENERATING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SolarPanelBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SOLAR_PANEL, SolarPanelBlockEntity::serverTick);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(GENERATING) && random.nextInt(24) == 0) {
			level.addParticle(ParticleTypes.WAX_ON, pos.getX() + random.nextDouble(), pos.getY() + 0.6, pos.getZ() + random.nextDouble(), 0.0, 0.02, 0.0);
		}
	}
}
