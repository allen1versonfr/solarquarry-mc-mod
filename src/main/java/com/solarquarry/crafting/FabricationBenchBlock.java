package com.solarquarry.crafting;

import com.mojang.serialization.MapCodec;
import com.solarquarry.network.BenchCatalogPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Fabrication Bench: the Solar Quarry workbench with a catalogue of every mod recipe. */
public class FabricationBenchBlock extends HorizontalDirectionalBlock {
	public static final MapCodec<FabricationBenchBlock> CODEC = simpleCodec(FabricationBenchBlock::new);

	public FabricationBenchBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new BenchCatalogPayload(BenchRecipes.catalogue(serverLevel)));
			player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FabricationBenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
				Component.translatable("block.solarquarry.fabrication_bench")));
		}

		return InteractionResult.SUCCESS;
	}
}
