package com.solarquarry.blockentity;

import com.solarquarry.block.BatteryBlock;
import com.solarquarry.energy.BatteryTier;
import com.solarquarry.energy.EndpointCache;
import com.solarquarry.energy.EnergyNetwork;
import com.solarquarry.energy.EnergyNode;
import com.solarquarry.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BatteryBlockEntity extends BlockEntity implements EnergyNode {
	private final EndpointCache endpoints = new EndpointCache();
	private long energy;
	private long receivedThisTick;
	private long lastTick = Long.MIN_VALUE;
	private int rotation;
	private long windowIn;
	private long windowOut;
	private int avgIn;
	private int avgOut;

	public BatteryBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BATTERY, pos, state);
	}

	public BatteryTier getTier() {
		return this.getBlockState().getBlock() instanceof BatteryBlock battery ? battery.getTier() : BatteryTier.BASIC;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
		if (level instanceof ServerLevel serverLevel) {
			battery.tickServer(serverLevel, state);
		}
	}

	private void tickServer(ServerLevel level, BlockState state) {
		this.resetRateWindow(level);
		if (this.energy > 0) {
			List<BlockPos> targets = this.endpoints.get(level, this.worldPosition);
			if (!targets.isEmpty()) {
				this.rotation++;
				long budget = Math.min(this.energy, this.getTier().maxTransfer());
				if (EnergyNetwork.distribute(level, this, targets, Role.CONSUMER, budget, this.rotation) > 0) {
					this.setChanged();
				}
			}
		}

		if (level.getGameTime() % 20 == 0) {
			this.avgIn = (int) Math.min(Integer.MAX_VALUE, this.windowIn / 20);
			this.avgOut = (int) Math.min(Integer.MAX_VALUE, this.windowOut / 20);
			this.windowIn = 0;
			this.windowOut = 0;
		}

		if (level.getGameTime() % 10 == 0) {
			int charge = this.chargeLevel();
			if (state.hasProperty(BatteryBlock.CHARGE) && state.getValue(BatteryBlock.CHARGE) != charge) {
				level.setBlock(this.worldPosition, state.setValue(BatteryBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
			}
		}
	}

	private void resetRateWindow(Level level) {
		long now = level.getGameTime();
		if (now != this.lastTick) {
			this.lastTick = now;
			this.receivedThisTick = 0;
		}
	}

	/** 0..4 bars for the front indicator. */
	public int chargeLevel() {
		long capacity = this.getEnergyCapacity();
		if (this.energy <= 0 || capacity <= 0) {
			return 0;
		}

		return (int) Math.max(1, Math.min(4, Math.ceil(this.energy * 4.0 / capacity)));
	}

	@Override
	public Role energyRole() {
		return Role.STORAGE;
	}

	@Override
	public long getEnergy() {
		return this.energy;
	}

	@Override
	public long getEnergyCapacity() {
		return this.getTier().capacity();
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		if (this.level != null) {
			this.resetRateWindow(this.level);
		}

		long room = Math.max(0, this.getEnergyCapacity() - this.energy);
		long rate = Math.max(0, this.getTier().maxTransfer() - this.receivedThisTick);
		long accepted = Math.max(0, Math.min(amount, Math.min(room, rate)));
		if (!simulate && accepted > 0) {
			this.energy += accepted;
			this.receivedThisTick += accepted;
			this.windowIn += accepted;
			this.setChanged();
		}

		return accepted;
	}

	@Override
	public long extractEnergy(long amount, boolean simulate) {
		long extracted = Math.max(0, Math.min(amount, this.energy));
		if (!simulate && extracted > 0) {
			this.energy -= extracted;
			this.windowOut += extracted;
			this.setChanged();
		}

		return extracted;
	}

	@Override
	public void restoreEnergy(long amount) {
		this.energy = Math.min(this.getEnergyCapacity(), this.energy + Math.max(0, amount));
		this.windowOut = Math.max(0, this.windowOut - Math.max(0, amount));
		this.setChanged();
	}

	public int getAverageInput() {
		return this.avgIn;
	}

	public int getAverageOutput() {
		return this.avgOut;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.energy = Math.max(0, Math.min(input.getLongOr("Energy", 0), this.getEnergyCapacity()));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", this.energy);
	}

	/** Returns the battery's data for a wrench pickup and empties it so the removal cannot duplicate energy. */
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag tag = this.saveCustomOnly(registries);
		this.energy = 0;
		this.setChanged();
		return tag;
	}
}
