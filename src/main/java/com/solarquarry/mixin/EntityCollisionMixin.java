package com.solarquarry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solarquarry.quarry.QuarryFrames;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes quarry frames solid: when an entity moves, the frame beams near it are added to the list of entity
 * collision shapes the game already gathers. No blocks or entities are created for the frame.
 */
@Mixin(Entity.class)
abstract class EntityCollisionMixin {
	@WrapOperation(
		method = "collide",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
	)
	private List<VoxelShape> solarquarry$addFrameCollisions(Level level, Entity entity, AABB area, Operation<List<VoxelShape>> original) {
		return QuarryFrames.addCollisions(level, area, original.call(level, entity, area));
	}
}
