package com.solarquarry.storage;

import com.mojang.serialization.MapCodec;
import com.solarquarry.block.MachineBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Storage crate block (see {@link CrateTier}). */
public class CrateBlock extends MachineBlock {
	private static final Map<CrateTier, MapCodec<CrateBlock>> CODECS = new HashMap<>();
	private final CrateTier tier;

	public CrateBlock(CrateTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public CrateTier tier() {
		return this.tier;
	}

	@Override
	protected MapCodec<CrateBlock> codec() {
		CrateTier tier = this.tier;
		return CODECS.computeIfAbsent(tier, t -> simpleCodec(p -> new CrateBlock(t, p)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrateBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CrateBlockEntity crate) {
			player.openMenu(crate);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}
}
