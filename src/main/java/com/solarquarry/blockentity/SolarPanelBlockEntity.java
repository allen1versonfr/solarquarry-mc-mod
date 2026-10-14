package com.solarquarry.blockentity;

import com.solarquarry.block.SolarPanelBlock;
import com.solarquarry.energy.EndpointCache;
import com.solarquarry.energy.EnergyNetwork;
import com.solarquarry.energy.EnergyNode;
import com.solarquarry.energy.SolarTier;
import com.solarquarry.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class SolarPanelBlockEntity extends BlockEntity implements EnergyNode {
	private final EndpointCache endpoints = new EndpointCache();
	private long energy;
	private int currentGeneration;
	private int rotation;

	public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SOLAR_PANEL, pos, state);
	}

	public SolarTier getTier() {
		return this.getBlockState().getBlock() instanceof SolarPanelBlock panel ? panel.getTier() : SolarTier.BASIC;
	}

	public int getCurrentGeneration() {
		return this.currentGeneration;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
		if (level instanceof ServerLevel serverLevel) {
			panel.tickServer(serverLevel, state);
		}
	}

	private void tickServer(ServerLevel level, BlockState state) {
		if (level.getGameTime() % 20 == Math.floorMod(this.worldPosition.asLong(), 20)) {
			this.currentGeneration = computeGeneration(level, this.worldPosition, this.getTier());
			boolean generating = this.currentGeneration > 0;
			if (state.hasProperty(SolarPanelBlock.GENERATING) && state.getValue(SolarPanelBlock.GENERATING) != generating) {
				level.setBlock(this.worldPosition, state.setValue(SolarPanelBlock.GENERATING, generating), Block.UPDATE_CLIENTS);
			}
		}

		long capacity = this.getEnergyCapacity();
		if (this.currentGeneration > 0 && this.energy < capacity) {
			this.energy = Math.min(capacity, this.energy + this.currentGeneration);
			this.setChanged();
		}

		if (this.energy > 0) {
			List<BlockPos> targets = this.endpoints.get(level, this.worldPosition);
			if (!targets.isEmpty()) {
				this.rotation++;
				long moved = EnergyNetwork.distribute(level, this, targets, Role.CONSUMER, this.energy, this.rotation);
				if (this.energy > 0) {
					moved += EnergyNetwork.distribute(level, this, targets, Role.STORAGE, this.energy, this.rotation);
				}

				if (moved > 0) {
					this.setChanged();
				}
			}
		}
	}

	/**
	 * Generation follows the sun: it needs a clear view of the sky, peaks at noon, is reduced by rain and storms
	 * where it actually rains (not in deserts) and drops to the tier's night output after sunset.
	 */
	public static int computeGeneration(Level level, BlockPos pos, SolarTier tier) {
		int base = tier.generation(sunFactor(level, pos), level.canSeeSky(pos.above()), level.isRainingAt(pos.above()), level.isThundering() && level.isRainingAt(pos.above()));
		return (int) Math.round(base * com.solarquarry.config.SolarQuarryConfig.get().solarOutputMultiplier());
	}

	/** 0..1, how high the sun is (1 at noon). */
	public static float sunFactor(Level level, BlockPos pos) {
		float sunAngle = level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, pos) * (float) (Math.PI / 180.0);
		float offset = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
		sunAngle += (offset - sunAngle) * 0.2F;
		return Mth.clamp(Mth.cos(sunAngle), 0.0F, 1.0F);
	}

	/** Why the panel produces what it does, for the right-click readout. */
	public Component describe(Level level) {
		SolarTier tier = this.getTier();
		BlockPos pos = this.worldPosition;
		int now = computeGeneration(level, pos, tier);
		if (!level.canSeeSky(pos.above())) {
			return Component.translatable("solarquarry.solar.no_sky");
		}

		float sun = sunFactor(level, pos);
		String reason;
		if (sun < 0.05F) {
			reason = tier.nightFactor() > 0 ? "solarquarry.solar.night_cells" : "solarquarry.solar.night";
		} else if (level.isThundering() && level.isRainingAt(pos.above())) {
			reason = "solarquarry.solar.storm";
		} else if (level.isRainingAt(pos.above())) {
			reason = "solarquarry.solar.rain";
		} else {
			reason = "solarquarry.solar.sun";
		}

		return Component.translatable("solarquarry.solar.status", now, Math.round(tier.peakGeneration() * com.solarquarry.config.SolarQuarryConfig.get().solarOutputMultiplier()), Component.translatable(reason, Math.round(sun * 100)));
	}

	@Override
	public Role energyRole() {
		return Role.PRODUCER;
	}

	@Override
	public long getEnergy() {
		return this.energy;
	}

	@Override
	public long getEnergyCapacity() {
		return this.getTier().bufferCapacity();
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		return 0; // panels only generate
	}

	@Override
	public long extractEnergy(long amount, boolean simulate) {
		long extracted = Math.max(0, Math.min(amount, this.energy));
		if (!simulate && extracted > 0) {
			this.energy -= extracted;
			this.setChanged();
		}

		return extracted;
	}

	@Override
	public void restoreEnergy(long amount) {
		this.energy += Math.max(0, amount);
		this.setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.energy = Math.max(0, input.getLongOr("Energy", 0));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", this.energy);
	}
}
