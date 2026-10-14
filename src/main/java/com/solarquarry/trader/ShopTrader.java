package com.solarquarry.trader;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.InteractGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.TradeWithPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Solar Quarry trader: lives at the shop, never despawns, sells mod parts and buys ores and blocks for emeralds
 * through the vanilla merchant screen (so every trade is validated by the game's own server logic). Stock refills
 * every morning. Only the trader registered in {@link ShopState} is kept; any other copy removes itself.
 */
public class ShopTrader extends AbstractVillager {
	private long lastRestockDay = -1;
	/** Set for the trader the shop spawned; any other copy of a shop trader removes itself. */
	private boolean shopMember;

	public ShopTrader(EntityType<? extends ShopTrader> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.MAX_HEALTH, 30.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TradeWithPlayerGoal(this));
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Zombie.class, 8.0F, 0.5, 0.5));
		this.goalSelector.addGoal(1, new PanicGoal(this, 0.5));
		this.goalSelector.addGoal(1, new LookAtTradingPlayerGoal(this));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.35));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.3));
		this.goalSelector.addGoal(9, new InteractGoal(this, Player.class, 3.0F, 1.0F));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (this.isAlive() && !this.isTrading() && !this.isBaby()) {
			if (hand == InteractionHand.MAIN_HAND) {
				player.awardStat(Stats.TALKED_TO_VILLAGER);
			}

			if (!this.level().isClientSide()) {
				if (this.getOffers().isEmpty()) {
					return InteractionResult.CONSUME;
				}

				this.setTradingPlayer(player);
				this.openTradingScreen(player, this.getDisplayName(), 1);
			}

			return InteractionResult.SUCCESS;
		}

		return super.mobInteract(player, hand);
	}

	@Override
	protected void updateTrades(ServerLevel level) {
		MerchantOffers offers = this.getOffers();
		offers.addAll(ShopTrades.offers());
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0) {
			if (this.shopMember && !ShopManager.isRegisteredTrader(level, this)) {
				this.discard(); // an extra copy (old save, chunk restore): the shop has exactly one trader
				return;
			}

			long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
			if (day != this.lastRestockDay && !this.isTrading()) {
				this.lastRestockDay = day;
				for (MerchantOffer offer : this.getOffers()) {
					offer.resetUses();
				}
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			ShopManager.onTraderDied(level, this);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	protected void rewardTradeXp(MerchantOffer offer) {
		if (offer.shouldRewardExp()) {
			this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), 2 + this.random.nextInt(3)));
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("RestockDay", this.lastRestockDay);
		output.putBoolean("ShopMember", this.shopMember);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lastRestockDay = input.getLongOr("RestockDay", -1L);
		this.shopMember = input.getBooleanOr("ShopMember", false);
	}

	public void setShopMember(boolean member) {
		this.shopMember = member;
	}

	public boolean isShopMember() {
		return this.shopMember;
	}

	/** Keeps the trader near its shop. */
	public void anchorTo(BlockPos center) {
		this.setHomeTo(center, 4);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isTrading() ? SoundEvents.WANDERING_TRADER_TRADE : SoundEvents.WANDERING_TRADER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WANDERING_TRADER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WANDERING_TRADER_DEATH;
	}

	@Override
	protected SoundEvent getTradeUpdatedSound(boolean validTrade) {
		return validTrade ? SoundEvents.WANDERING_TRADER_YES : SoundEvents.WANDERING_TRADER_NO;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.WANDERING_TRADER_YES;
	}
}
