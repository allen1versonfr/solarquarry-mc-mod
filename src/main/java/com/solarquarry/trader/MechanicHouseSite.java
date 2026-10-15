package com.solarquarry.trader;

import com.solarquarry.construction.ConstructionHost;
import com.solarquarry.construction.ConstructionSite;
import com.solarquarry.construction.ConstructionState;
import com.solarquarry.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Runs the Mechanic's construction of his house on the server: one shared {@link ConstructionSite} per world, saved
 * on the Overworld ({@link ShopState#HOUSE_SITE}). While the house's area is loaded, the Mechanic walks to the block
 * being placed, swings his wrench and every block appears with its sound and particles. When the area is unloaded the
 * work simply waits (no chunks are loaded for it). The supply chest is stocked once, ever.
 *
 * <p>A finished house is not maintained: blocks a player breaks stay broken, so nothing can be farmed by breaking
 * and waiting for a rebuild.
 */
public final class MechanicHouseSite {
	/** Ticks between construction rounds and blocks per round (the self-test speeds this up). */
	public static int interval = 4;
	public static int stepsPerRound = 1;
	private static final int SAVE_EVERY = 40;

	private static @Nullable MinecraftServer server;
	private static @Nullable ConstructionSite site;
	private static boolean supplied;
	private static boolean dirty;

	private MechanicHouseSite() {
	}

	/** Starts building at {@code floor} (called once, when the shop's place is chosen). */
	public static void start(ServerLevel overworld, BlockPos floor) {
		server = overworld.getServer();
		site = new ConstructionSite(interval, stepsPerRound);
		site.plan(MechanicHouse.blueprint(overworld, floor));
		supplied = false;
		save(overworld);
	}

	/** The world's house site, loaded on first use; null if this world has none (3.x shop or not built yet). */
	public static @Nullable ConstructionSite site(ServerLevel overworld) {
		if (server != overworld.getServer()) {
			server = overworld.getServer();
			site = null;
			supplied = false;
			CompoundTag tag = overworld.getAttached(ShopState.HOUSE_SITE);
			if (tag != null) {
				ConstructionSite loaded = new ConstructionSite(interval, stepsPerRound);
				if (loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, overworld.registryAccess(), tag))) {
					site = loaded;
				}

				supplied = tag.getBooleanOr("Supplied", false);
			}
		}

		return site;
	}

	/** Forgets the cached site (the self-test resets the shop; a new world). */
	public static void forget() {
		server = null;
		site = null;
		supplied = false;
	}

	public static boolean isSupplied() {
		return supplied;
	}

	public static void save(ServerLevel overworld) {
		if (site == null) {
			overworld.removeAttached(ShopState.HOUSE_SITE);
			return;
		}

		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, overworld.registryAccess());
		site.save(out);
		out.putBoolean("Supplied", supplied);
		overworld.setAttached(ShopState.HOUSE_SITE, out.buildResult());
		dirty = false;
	}

	/** Called every server tick. */
	public static void tick(MinecraftServer mc) {
		ServerLevel overworld = mc.overworld();
		ShopState state = ShopState.get(overworld);
		if (!state.built() || !state.isMechanicHouse() || state.center().isEmpty()) {
			return;
		}

		ConstructionSite s = site(overworld);
		if (s == null) {
			return;
		}

		BlockPos floor = state.center().get();
		if (s.isComplete() || s.state() == ConstructionState.ERROR) {
			if (dirty) {
				save(overworld);
			}

			return; // finished houses are not maintained (see class comment)
		}

		if (!overworld.isLoaded(floor)) {
			return; // nobody near: the work waits, nothing is loaded for it
		}

		ConstructionState before = s.state();
		boolean changed = s.tick(overworld, new Host(overworld, state));
		dirty |= changed;
		if (dirty && (s.state() != before || mc.getTickCount() % SAVE_EVERY == 0)) {
			save(overworld);
		}

		if (mc.getTickCount() % 10 == 0) {
			guideMechanic(overworld, state, s);
		}
	}

	/** Walks the Mechanic to the work and puts the wrench in his hand while he builds. */
	private static void guideMechanic(ServerLevel overworld, ShopState state, ConstructionSite s) {
		ShopTrader mechanic = mechanic(overworld, state);
		if (mechanic == null || mechanic.isTrading()) {
			return;
		}

		BlockPos floor = state.center().get();
		BlockPos target = s.currentTarget();
		if (s.isUnderConstruction() && target != null) {
			mechanic.setHomeTo(floor, 12);
			if (!mechanic.getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.WRENCH)) {
				mechanic.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WRENCH));
			}

			if (mechanic.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 16.0) {
				mechanic.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.6);
			}

			mechanic.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		}
	}

	private static @Nullable ShopTrader mechanic(ServerLevel overworld, ShopState state) {
		if (state.trader().isEmpty()) {
			return null;
		}

		Entity entity = overworld.getEntity(state.trader().get());
		return entity instanceof ShopTrader trader ? trader : null;
	}

	/** Called when construction finishes: the Mechanic goes back behind his counter. */
	private static void finished(ServerLevel overworld, ShopState state) {
		ShopTrader mechanic = mechanic(overworld, state);
		if (mechanic != null) {
			mechanic.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			mechanic.anchorTo(MechanicHouse.traderSpot(state.center().get()));
		}

		dirty = true;
	}

	/** Stocks the supply chest once and writes the sign. */
	private static void furnish(ServerLevel overworld, BlockPos floor, BlockPos pos) {
		if (pos.equals(MechanicHouse.chestPos(floor)) && !supplied && overworld.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.isEmpty()) {
			chest.setItem(0, new ItemStack(Items.TORCH, 32));
			chest.setItem(1, new ItemStack(Items.BREAD, 8));
			chest.setItem(2, new ItemStack(Items.COAL, 16));
			chest.setItem(3, new ItemStack(Items.IRON_PICKAXE));
			chest.setItem(4, new ItemStack(Items.OAK_LOG, 16));
			supplied = true;
			save(overworld); // right away: a crash must never let the chest be stocked twice
		}

		if (pos.equals(MechanicHouse.signPos(floor)) && overworld.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			SignText text = new SignText()
				.setMessage(0, Component.translatable("solarquarry.shop.sign.0"))
				.setMessage(1, Component.translatable("solarquarry.shop.sign.1"))
				.setMessage(2, Component.translatable("solarquarry.shop.sign.2"));
			sign.setText(text, true);
			sign.setWaxed(true);
		}
	}

	/** The house's side of the construction: free materials, his own replace rules, effects. */
	private record Host(ServerLevel level, ShopState state) implements ConstructionHost {
		@Override
		public boolean payForStep() {
			return true; // the Mechanic builds from his own stock
		}

		@Override
		public boolean canReplace(BlockState existing) {
			return MechanicHouse.canReplace(existing);
		}

		@Override
		public void onStepDone(ServerLevel level, @Nullable BlockPos pos) {
			if (pos == null) {
				return;
			}

			BlockState placed = level.getBlockState(pos);
			if (!placed.isAir()) {
				level.playSound(null, pos, placed.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, placed), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
			}

			ShopTrader mechanic = mechanic(level, this.state);
			if (mechanic != null && !mechanic.isTrading()) {
				mechanic.swing(InteractionHand.MAIN_HAND, true);
			}

			furnish(level, this.state.center().get(), pos);
		}

		@Override
		public void onCompleted(ServerLevel level) {
			finished(level, this.state);
		}
	}
}
