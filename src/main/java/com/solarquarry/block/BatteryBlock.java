package com.solarquarry.block;

import com.mojang.serialization.MapCodec;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.energy.BatteryTier;
import com.solarquarry.energy.EnergyConnectable;
import com.solarquarry.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.solarquarry.util.EnergyFormat;
import org.jspecify.annotations.Nullable;

public class BatteryBlock extends MachineBlock implements EnergyConnectable {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
	private final BatteryTier tier;
	private final MapCodec<BatteryBlock> codec;

	public BatteryBlock(BatteryTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		this.codec = simpleCodec(p -> new BatteryBlock(tier, p));
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0));
	}

	public BatteryTier getTier() {
		return this.tier;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return this.codec;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CHARGE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BatteryBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.BATTERY, BatteryBlockEntity::serverTick);
	}

	/** Right-click with a powered tool or armour (Rocket Boots, prospectors, helmet) to charge it from the battery. */
	@Override
	protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (com.solarquarry.item.PoweredItem.isPowered(stack)) {
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
				long room = com.solarquarry.item.PoweredItem.charge(stack, Long.MAX_VALUE, true);
				long moved = battery.extractEnergy(Math.min(room, 50_000L), false);
				com.solarquarry.item.PoweredItem.charge(stack, moved, false);
				player.sendOverlayMessage(Component.translatable("solarquarry.powered.charged", stack.getHoverName(),
					EnergyFormat.format(com.solarquarry.item.PoweredItem.energy(stack)), EnergyFormat.format(com.solarquarry.item.PoweredItem.capacityOf(stack))));
			}

			return InteractionResult.SUCCESS;
		}

		return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BatteryBlockEntity battery) {
			player.sendOverlayMessage(Component.translatable(
				"solarquarry.battery.status", EnergyFormat.format(battery.getEnergy()), EnergyFormat.format(battery.getEnergyCapacity()),
				battery.getAverageInput(), battery.getAverageOutput(), battery.getTier().maxTransfer()
			));
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		if (level.getBlockEntity(pos) instanceof BatteryBlockEntity battery && battery.getEnergyCapacity() > 0) {
			return (int) Math.round(15.0 * battery.getEnergy() / battery.getEnergyCapacity());
		}

		return 0;
	}
}
