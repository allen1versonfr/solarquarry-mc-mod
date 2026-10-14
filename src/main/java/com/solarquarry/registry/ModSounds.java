package com.solarquarry.registry;

import com.solarquarry.SolarQuarry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
	public static final SoundEvent QUARRY_DRILL = register("quarry_drill");
	public static final SoundEvent MACHINE_START = register("machine_start");
	public static final SoundEvent POWER_DOWN = register("power_down");
	public static final SoundEvent PIPE_DELIVER = register("pipe_deliver");
	public static final SoundEvent WRENCH = register("wrench");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = SolarQuarry.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
