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
	// Weather Control Machine (original sounds made for the mod).
	public static final SoundEvent WEATHER_IDLE = register("weather.idle_hum");
	public static final SoundEvent WEATHER_CAPACITOR = register("weather.capacitor_hum");
	public static final SoundEvent WEATHER_POWER_INTAKE = register("weather.power_intake");
	public static final SoundEvent WEATHER_CORE_CHARGE = register("weather.core_charge");
	public static final SoundEvent WEATHER_RING = register("weather.ring_rotation");
	public static final SoundEvent WEATHER_SERVO_LOCK = register("weather.servo_lock");
	public static final SoundEvent WEATHER_ARC = register("weather.arc");
	public static final SoundEvent WEATHER_BURST = register("weather.activation_burst");
	public static final SoundEvent WEATHER_FIELD_ACTIVATE = register("weather.field_activate");
	public static final SoundEvent WEATHER_STORM_ACTIVATE = register("weather.storm_activate");
	public static final SoundEvent WEATHER_TIME_ACTIVATE = register("weather.time_activate");
	public static final SoundEvent WEATHER_OPERATING = register("weather.operating_hum");
	public static final SoundEvent WEATHER_LOW_POWER = register("weather.low_power");
	public static final SoundEvent WEATHER_SHUTDOWN = register("weather.shutdown");
	public static final SoundEvent WEATHER_COMPLETE = register("weather.complete");
	public static final SoundEvent WEATHER_ERROR = register("weather.error");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = SolarQuarry.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Machine sound volume scaled by the server's machineSoundVolume setting (0 = silent). */
	public static float volume(float base) {
		return (float) (base * com.solarquarry.config.SolarQuarryConfig.get().machineSoundVolume());
	}

	public static void init() {
	}
}
