package com.solarquarry.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rocket Boots absorb part of a player's fall damage (server side). */
@Mixin(LivingEntity.class)
public abstract class FallDamageMixin {
	@ModifyReturnValue(method = "calculateFallDamage", at = @At("RETURN"))
	private int solarquarry$rocketBoots(int damage) {
		if (damage > 0 && (Object) this instanceof ServerPlayer player) {
			return com.solarquarry.boots.RocketBoots.reduceFallDamage(player, damage);
		}

		return damage;
	}
}
