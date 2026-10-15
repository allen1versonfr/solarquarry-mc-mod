package com.solarquarry.trader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * The Mechanic's guard: patrols near the house, fights hostile mobs that come close and whatever hurts the Mechanic.
 * It never targets players, pets, villagers, the Mechanic or neutral mobs (endermen, piglins...), and keeps away from creepers' explosions by ignoring
 * them. Its iron gear never drops. Only the guard registered in {@link ShopState} is kept.
 */
public class ShopGuard extends PathfinderMob {
	/** Set for the guard the shop spawned; any other copy of a shop guard removes itself. */
	private boolean shopMember;

	public ShopGuard(EntityType<? extends ShopGuard> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 40.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.ARMOR_TOUGHNESS, 2.0);
	}

	/** Which mobs the guard attacks: hostile ones only. */
	public static boolean isThreat(LivingEntity target) {
		return target instanceof Enemy && !(target instanceof Creeper) && !(target instanceof NeutralMob) && !(target instanceof Player) && target.isAlive();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 0.8));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, (target, level) -> isThreat(target)));
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, @Nullable SpawnGroupData groupData) {
		this.equip();
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	/** Gunmetal plating: armour dyed near-black. */
	public static final int PLATING = 0x2B2D31;

	/**
	 * Solar Quarry industrial armour: gunmetal-dyed plates with gold (hazard yellow) sentry trim, and an iron sword.
	 * The armour value comes from the guard's own attributes; none of it drops.
	 */
	public void equip() {
		this.setItemSlot(EquipmentSlot.HEAD, plate(Items.LEATHER_HELMET));
		this.setItemSlot(EquipmentSlot.CHEST, plate(Items.LEATHER_CHESTPLATE));
		this.setItemSlot(EquipmentSlot.LEGS, plate(Items.LEATHER_LEGGINGS));
		this.setItemSlot(EquipmentSlot.FEET, plate(Items.LEATHER_BOOTS));
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 0.0F);
		}
	}

	private ItemStack plate(net.minecraft.world.item.Item item) {
		ItemStack stack = new ItemStack(item);
		stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(PLATING));
		var registries = this.level().registryAccess();
		var material = registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).get(net.minecraft.world.item.equipment.trim.TrimMaterials.GOLD);
		var pattern = registries.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN).get(net.minecraft.world.item.equipment.trim.TrimPatterns.SENTRY);
		if (material.isPresent() && pattern.isPresent()) {
			stack.set(net.minecraft.core.component.DataComponents.TRIM, new net.minecraft.world.item.equipment.trim.ArmorTrim(material.get(), pattern.get()));
		}

		return stack;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return isThreat(target) && super.canAttack(target);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.updateSwingTime();
		if (this.level() instanceof ServerLevel level && this.tickCount % 20 == 0) {
			if (this.shopMember && !ShopManager.isRegisteredGuard(level, this)) {
				this.discard();
				return;
			}

			this.defendMechanic(level);
		}
	}

	/** Whatever hostile mob hurt the Mechanic becomes the guard's target (never a player). */
	private void defendMechanic(ServerLevel level) {
		ShopState state = ShopState.get(level.getServer().overworld());
		if (state.trader().isEmpty() || (this.getTarget() != null && this.getTarget().isAlive())) {
			return;
		}

		if (level.getEntity(state.trader().get()) instanceof ShopTrader mechanic && mechanic.distanceToSqr(this) < 32 * 32) {
			LivingEntity attacker = mechanic.getLastHurtByMob();
			if (attacker != null && isThreat(attacker)) {
				this.setTarget(attacker);
			}
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			ShopManager.onGuardDied(level, this);
		}
	}

	public void setShopMember(boolean member) {
		this.shopMember = member;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("ShopMember", this.shopMember);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shopMember = input.getBooleanOr("ShopMember", false);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}
}
