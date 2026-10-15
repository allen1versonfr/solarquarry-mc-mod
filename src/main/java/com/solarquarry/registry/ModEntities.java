package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import com.solarquarry.trader.ShopGuard;
import com.solarquarry.trader.ShopTrader;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<ShopTrader> SHOP_TRADER = register("trader",
		EntityType.Builder.of(ShopTrader::new, MobCategory.CREATURE).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));
	public static final EntityType<ShopGuard> SHOP_GUARD = register("guard",
		EntityType.Builder.of(ShopGuard::new, MobCategory.CREATURE).sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<com.solarquarry.quarry.SecurityDrone> SECURITY_DRONE = register("security_drone",
		EntityType.Builder.of(com.solarquarry.quarry.SecurityDrone::new, MobCategory.MISC).sized(0.7F, 0.4F).clientTrackingRange(8).noSave());

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SolarQuarry.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SHOP_TRADER, ShopTrader.createAttributes());
		FabricDefaultAttributeRegistry.register(SHOP_GUARD, ShopGuard.createAttributes());
		FabricDefaultAttributeRegistry.register(SECURITY_DRONE, com.solarquarry.quarry.SecurityDrone.createAttributes());
	}
}
