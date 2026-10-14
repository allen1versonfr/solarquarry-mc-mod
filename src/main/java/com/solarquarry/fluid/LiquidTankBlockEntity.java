package com.solarquarry.fluid;

import com.solarquarry.item.Dismantlable;
import com.solarquarry.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Liquid Tank: holds one fluid, up to {@link #CAPACITY_BUCKETS} buckets. Fills from a quarry's Liquid Remover through
 * fluid pipes (or any Fabric fluid transfer source); empties with buckets or any fluid transfer consumer.
 */
public class LiquidTankBlockEntity extends BlockEntity implements Dismantlable {
	public static final long CAPACITY_BUCKETS = 64;
	public static final long CAPACITY = CAPACITY_BUCKETS * FluidConstants.BUCKET;

	public final SingleVariantStorage<FluidVariant> storage = new SingleVariantStorage<>() {
		@Override
		protected FluidVariant getBlankVariant() {
			return FluidVariant.blank();
		}

		@Override
		protected long getCapacity(FluidVariant variant) {
			return CAPACITY;
		}

		@Override
		protected void onFinalCommit() {
			LiquidTankBlockEntity.this.changed();
		}
	};

	public LiquidTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LIQUID_TANK, pos, state);
	}

	public FluidVariant fluid() {
		return this.storage.variant;
	}

	public long amount() {
		return this.storage.amount;
	}

	/** 0..1 */
	public float fill() {
		return this.storage.amount <= 0 ? 0.0F : Math.min(1.0F, (float) this.storage.amount / CAPACITY);
	}

	private void changed() {
		this.setChanged();
		if (this.level != null && !this.level.isClientSide()) {
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
			this.level.updateNeighbourForOutputSignal(this.worldPosition, state.getBlock());
		}
	}

	/** Comparator output 0..15. */
	public int signal() {
		if (this.storage.amount <= 0) {
			return 0;
		}

		return 1 + (int) Math.min(14, this.storage.amount * 14 / CAPACITY);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		SingleVariantStorage.readValue(this.storage, FluidVariant.CODEC, FluidVariant::blank, input);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		SingleVariantStorage.writeValue(this.storage, FluidVariant.CODEC, output);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.storage.variant = FluidVariant.blank();
		this.storage.amount = 0;
		this.setChanged();
		return data;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		SingleVariantStorage.writeValue(this.storage, FluidVariant.CODEC, output);
		return output.buildResult();
	}
}
