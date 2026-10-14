package com.solarquarry.fluid;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.MachineBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Liquid Tank block (see {@link LiquidTankBlockEntity}). */
public class LiquidTankBlock extends MachineBlock {
	public static final MapCodec<LiquidTankBlock> CODEC = simpleCodec(LiquidTankBlock::new);

	public LiquidTankBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<LiquidTankBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new LiquidTankBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		if (itemStack.getItem() instanceof com.solarquarry.item.MachineTool) {
			return InteractionResult.PASS;
		}

		if (level.getBlockEntity(pos) instanceof LiquidTankBlockEntity tank && FluidStorageUtil.interactWithFluidStorage(tank.storage, player, hand)) {
			return InteractionResult.SUCCESS;
		}

		return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LiquidTankBlockEntity tank) {
			player.sendOverlayMessage(describe(tank));
		}

		return InteractionResult.SUCCESS;
	}

	public static Component describe(LiquidTankBlockEntity tank) {
		long buckets = tank.amount() / FluidConstants.BUCKET;
		if (tank.fluid().isBlank() || tank.amount() <= 0) {
			return Component.translatable("solarquarry.tank.empty", LiquidTankBlockEntity.CAPACITY_BUCKETS);
		}

		return Component.translatable("solarquarry.tank.contents", FluidVariantAttributes.getName(tank.fluid()), buckets, LiquidTankBlockEntity.CAPACITY_BUCKETS);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof LiquidTankBlockEntity tank ? tank.signal() : 0;
	}
}
