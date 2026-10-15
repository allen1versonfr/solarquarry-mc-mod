package com.solarquarry.client.weather;

import com.solarquarry.registry.ModSounds;
import com.solarquarry.weather.WeatherMachineBlockEntity;
import com.solarquarry.weather.WeatherStage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Looping sounds of the Weather Control Machine: one positional loop per machine, chosen by its stage (capacitor hum
 * while charging, rings while calibrating, the operating hum while it works, a quiet hum otherwise). A loop stops by
 * itself when the stage changes, when the machine is removed or unloaded, or when it falls silent; it is never
 * restarted every tick, and nearby machines share no loops.
 */
public final class WeatherMachineSounds {
	private static final Map<BlockPos, Loop> PLAYING = new HashMap<>();

	private WeatherMachineSounds() {
	}

	public static void register() {
		WeatherMachineBlockEntity.clientStageListener = WeatherMachineSounds::update;
	}

	static SoundEvent loopFor(WeatherStage stage) {
		return switch (stage) {
			case STANDBY -> null;
			case POWER_INTAKE, CORE_CHARGING -> ModSounds.WEATHER_CAPACITOR;
			case CALIBRATION, SYNCHRONIZATION -> ModSounds.WEATHER_RING;
			case OPERATION, STABILIZATION -> ModSounds.WEATHER_OPERATING;
			case SHUTDOWN -> ModSounds.WEATHER_IDLE;
		};
	}

	private static void update(WeatherMachineBlockEntity machine) {
		BlockPos pos = machine.getBlockPos().immutable();
		Loop current = PLAYING.get(pos);
		SoundEvent wanted = machine.isRemoved() ? null : loopFor(machine.getStage());
		if (current != null && (current.isStopped() || current.event != wanted)) {
			current.end();
			PLAYING.remove(pos);
			current = null;
		}

		if (current == null && wanted != null) {
			Loop loop = new Loop(machine, wanted);
			PLAYING.put(pos, loop);
			Minecraft.getInstance().getSoundManager().play(loop);
		}
	}

	static final class Loop extends AbstractTickableSoundInstance {
		private final WeatherMachineBlockEntity machine;
		final SoundEvent event;
		private final WeatherStage stage;

		Loop(WeatherMachineBlockEntity machine, SoundEvent event) {
			super(event, SoundSource.BLOCKS, RandomSource.create());
			this.machine = machine;
			this.event = event;
			this.stage = machine.getStage();
			this.looping = true;
			this.delay = 0;
			this.volume = 0.6F;
			this.pitch = 1.0F;
			BlockPos p = machine.getBlockPos();
			this.x = p.getX() + 0.5;
			this.y = p.getY() + 1.0;
			this.z = p.getZ() + 0.5;
		}

		void end() {
			this.stop();
		}

		@Override
		public void tick() {
			if (this.machine.isRemoved() || this.machine.getStage() != this.stage) {
				this.stop();
				return;
			}

			// Charging builds up: louder and higher as the stage runs.
			if (this.stage == WeatherStage.POWER_INTAKE || this.stage == WeatherStage.CORE_CHARGING) {
				float f = Math.min(1.0F, this.machine.getStageTicks() / 40.0F);
				this.volume = 0.35F + 0.4F * f;
				this.pitch = 0.8F + 0.5F * f;
			}
		}
	}
}
