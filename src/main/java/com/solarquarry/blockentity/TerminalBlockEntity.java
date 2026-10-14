package com.solarquarry.blockentity;

import com.solarquarry.energy.EnergyNetwork;
import com.solarquarry.menu.TerminalMenu;
import com.solarquarry.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Remote terminal. Connection rule: it sees every quarry reachable through energy cables from the terminal
 * (same dimension, loaded chunks, up to {@link EnergyNetwork#MAX_CABLES} cables), nearest first, at most {@link #MAX_MACHINES}.
 * Control is limited to quarries the player placed (or any quarry for operators); everything is validated on the server.
 */
public class TerminalBlockEntity extends BlockEntity implements ExtendedMenuProvider<BlockPos> {
	public static final int MAX_MACHINES = 32;

	public TerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.TERMINAL, pos, state);
	}

	public List<QuarryBlockEntity> findQuarries() {
		List<QuarryBlockEntity> result = new ArrayList<>();
		if (this.level == null) {
			return result;
		}

		for (BlockPos pos : EnergyNetwork.discoverEndpoints(this.level, this.worldPosition)) {
			if (EnergyNetwork.resolve(this.level, pos) instanceof QuarryBlockEntity quarry) {
				result.add(quarry);
			}
		}

		result.sort(Comparator.comparingDouble(q -> q.getBlockPos().distSqr(this.worldPosition)));
		return result.size() > MAX_MACHINES ? new ArrayList<>(result.subList(0, MAX_MACHINES)) : result;
	}

	public @Nullable QuarryBlockEntity findQuarry(BlockPos pos) {
		for (QuarryBlockEntity quarry : this.findQuarries()) {
			if (quarry.getBlockPos().equals(pos)) {
				return quarry;
			}
		}

		return null;
	}

	public boolean stillValid(Player player) {
		return !this.isRemoved() && this.level != null && this.level.getBlockEntity(this.worldPosition) == this
			&& player.isWithinBlockInteractionRange(this.worldPosition, 4.0);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.remote_terminal");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new TerminalMenu(containerId, inventory, this);
	}
}
