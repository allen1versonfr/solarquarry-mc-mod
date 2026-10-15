package com.solarquarry.weather;

import com.solarquarry.energy.EnergyNode;
import com.solarquarry.registry.ModBlockEntities;
import com.solarquarry.registry.ModSounds;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Weather Control Machine: changes the world's real weather (server weather data) and time of day (the dimension's
 * world clock, never the total game time) for very large amounts of energy.
 *
 * <p>A command is accepted only in {@link WeatherStage#STANDBY}, only where the dimension has the feature (weather
 * needs an open sky; time needs a world clock) and only with enough stored energy, which is taken at once, exactly
 * once. The machine then runs its startup sequence (power intake, core charging, calibration, synchronisation), applies
 * the change and either shuts down (one-time changes) or keeps the weather for the chosen duration, paying upkeep
 * every tick; out of energy it performs a controlled shutdown and the world's normal weather cycle takes over again.
 * Weather is shared by the whole server, so with several machines the last command wins.
 */
public class WeatherMachineBlockEntity extends BlockEntity implements EnergyNode, ExtendedMenuProvider<BlockPos> {
	public static final long CAPACITY = 5_000_000;
	public static final long MAX_INPUT = 8_192;
	public static final int DATA_COUNT = 17;
	/** Weather is re-applied this often while held, with a hold timer of {@link #HOLD}. */
	public static final int REFRESH = 100;
	public static final int HOLD = 600;
	/** Clock ticks moved per tick during a gradual time change (a full day takes about 25 seconds). */
	public static final int TIME_STEP = 48;
	public static final int[] DURATIONS = {5, 10, 20, 40};
	public static final int[] PRESETS = {23_000, 1_000, 6_000, 12_000, 14_000, 18_000};

	public static final int BUTTON_STOP = 0;
	public static final int BUTTON_CLEAR = 1;
	public static final int BUTTON_RAIN = 2;
	public static final int BUTTON_THUNDER = 3;
	public static final int BUTTON_SUSTAIN = 4;
	public static final int BUTTON_DURATION = 5;
	public static final int BUTTON_GRADUAL = 6;
	public static final int BUTTON_SET_TIME = 7;
	public static final int BUTTON_PRESET = 10;
	public static final int BUTTON_MANUAL = 100;

	public enum Error {
		NONE,
		BUSY,
		NO_ENERGY,
		NO_WEATHER,
		NO_CLOCK,
		POWER_LOST
	}

	/** Client hook: called on the client when the machine's stage changes (starts and stops looping sounds). */
	public static @Nullable Consumer<WeatherMachineBlockEntity> clientStageListener;

	private long energy;
	private WeatherStage stage = WeatherStage.STANDBY;
	private int stageTicks;
	private WeatherOperation operation = WeatherOperation.NONE;
	private boolean sustained;
	private int durationIndex = 1;
	private boolean gradual;
	private int manualTarget = 6_000;
	private int targetTime;
	private int remaining;
	private Error error = Error.NONE;
	private boolean lowPower;
	private long energyReceivedThisTick;
	private long receivedThisSecond;
	private long receivedLastSecond;
	private long usedThisSecond;
	private long usedLastSecond;
	private long ticks;
	private boolean syncDirty;
	private long operationsCompleted;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			WeatherMachineBlockEntity m = WeatherMachineBlockEntity.this;
			return switch (index) {
				case 0 -> (int) (m.energy & 0xFFFF);
				case 1 -> (int) ((m.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (CAPACITY & 0xFFFF);
				case 3 -> (int) ((CAPACITY >>> 16) & 0xFFFF);
				case 4 -> m.stage.ordinal();
				case 5 -> m.operation.ordinal();
				case 6 -> m.stage.ticks() == 0 ? 0 : Math.min(100, m.stageTicks * 100 / m.stage.ticks());
				case 7 -> Math.min(0x7FFF, m.remaining / 20);
				case 8 -> m.sustained ? 1 : 0;
				case 9 -> m.durationIndex;
				case 10 -> m.gradual ? 1 : 0;
				case 11 -> m.manualTarget / 10;
				case 12 -> m.targetTime / 10;
				case 13 -> m.error.ordinal();
				case 14 -> (int) Math.min(0x7FFF, m.receivedLastSecond / 20);
				case 15 -> (int) Math.min(0x7FFF, m.usedLastSecond / 20);
				case 16 -> m.lowPower ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public WeatherMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.WEATHER_MACHINE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WeatherMachineBlockEntity machine) {
		if (level instanceof ServerLevel server) {
			machine.tick(server);
		}
	}

	// ---- commands (validated on the server) ----

	/** Energy to start an operation now: clearing costs more against active rain (x1.5) or a thunderstorm (x2). */
	public long startCost(ServerLevel level, WeatherOperation op) {
		long base = op.startCost();
		if (op == WeatherOperation.CLEAR) {
			// The server's weather state, not the sky's fade-in level (that lags several seconds behind a change).
			var weather = level.getWeatherData();
			if (weather.isThundering()) {
				return base * 2;
			}

			if (weather.isRaining()) {
				return base * 3 / 2;
			}
		}

		return base;
	}

	public static boolean hasWeather(Level level) {
		return level.canHaveWeather();
	}

	public static @Nullable Holder<WorldClock> clock(Level level) {
		return level.dimensionType().defaultClock().orElse(null);
	}

	/** Starts a weather change; returns the error (NONE when accepted). */
	public Error startWeather(ServerLevel level, WeatherOperation op) {
		if (!op.isWeather()) {
			return Error.BUSY;
		}

		if (this.stage != WeatherStage.STANDBY) {
			return this.fail(Error.BUSY);
		}

		if (!hasWeather(level)) {
			return this.fail(Error.NO_WEATHER);
		}

		return this.begin(level, op, startCost(level, op));
	}

	/** Starts a time change to {@code target} (0-23999 ticks of the day). */
	public Error startTime(ServerLevel level, int target, boolean gradually) {
		if (this.stage != WeatherStage.STANDBY) {
			return this.fail(Error.BUSY);
		}

		if (clock(level) == null) {
			return this.fail(Error.NO_CLOCK);
		}

		this.targetTime = Math.floorMod(target, 24_000);
		WeatherOperation op = gradually ? WeatherOperation.TIME_GRADUAL : WeatherOperation.TIME_PRESET;
		return this.begin(level, op, op.startCost());
	}

	private Error begin(ServerLevel level, WeatherOperation op, long cost) {
		if (this.energy < cost) {
			return this.fail(Error.NO_ENERGY);
		}

		this.energy -= cost;
		this.usedThisSecond += cost;
		this.operation = op;
		this.error = Error.NONE;
		this.enter(level, WeatherStage.POWER_INTAKE);
		this.play(level, ModSounds.WEATHER_POWER_INTAKE, 1.0F);
		return Error.NONE;
	}

	/** Stops whatever runs: a controlled shutdown (energy already used is not returned). */
	public void stop(ServerLevel level) {
		if (this.stage != WeatherStage.STANDBY && this.stage != WeatherStage.SHUTDOWN) {
			this.enter(level, WeatherStage.SHUTDOWN);
		}
	}

	private Error fail(Error error) {
		this.error = error;
		this.syncDirty = true;
		if (this.level instanceof ServerLevel server) {
			this.play(server, ModSounds.WEATHER_ERROR, 0.8F);
		}

		return error;
	}

	public void toggleSustained() {
		if (this.stage == WeatherStage.STANDBY) {
			this.sustained = !this.sustained;
			this.setChanged();
		}
	}

	public void cycleDuration() {
		this.durationIndex = (this.durationIndex + 1) % DURATIONS.length;
		this.setChanged();
	}

	public void toggleGradual() {
		this.gradual = !this.gradual;
		this.setChanged();
	}

	public void setManualTarget(int target) {
		this.manualTarget = Math.floorMod(target, 24_000);
		this.setChanged();
	}

	public int manualTarget() {
		return this.manualTarget;
	}

	public boolean isGradual() {
		return this.gradual;
	}

	public boolean isSustained() {
		return this.sustained;
	}

	public void setSustained(boolean sustained) {
		this.sustained = sustained;
	}

	public void setDurationIndex(int index) {
		this.durationIndex = Math.floorMod(index, DURATIONS.length);
	}

	// ---- ticking ----

	private void tick(ServerLevel level) {
		this.ticks++;
		this.energyReceivedThisTick = 0;
		if (this.ticks % 20 == 0) {
			this.receivedLastSecond = this.receivedThisSecond;
			this.receivedThisSecond = 0;
			this.usedLastSecond = this.usedThisSecond;
			this.usedThisSecond = 0;
		}

		switch (this.stage) {
			case STANDBY -> {
			}
			case POWER_INTAKE, CORE_CHARGING, CALIBRATION -> this.advanceStartup(level);
			case SYNCHRONIZATION -> {
				if (++this.stageTicks >= this.stage.ticks()) {
					this.enter(level, WeatherStage.OPERATION);
					this.operate(level);
				}
			}
			case OPERATION -> this.operate(level);
			case STABILIZATION -> this.stabilize(level);
			case SHUTDOWN -> {
				if (++this.stageTicks >= this.stage.ticks()) {
					this.operation = WeatherOperation.NONE;
					this.remaining = 0;
					this.lowPower = false;
					this.enter(level, WeatherStage.STANDBY);
				}
			}
		}

		if (this.stage.isActive() && this.ticks % 4 == 0) {
			this.effects(level);
		}

		if (this.syncDirty && this.ticks % 5 == 0) {
			this.syncDirty = false;
			this.setChanged();
			level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	private void advanceStartup(ServerLevel level) {
		if (++this.stageTicks < this.stage.ticks()) {
			return;
		}

		WeatherStage next = WeatherStage.values()[this.stage.ordinal() + 1];
		this.enter(level, next);
		this.play(level, switch (next) {
			case CORE_CHARGING -> ModSounds.WEATHER_CORE_CHARGE;
			case CALIBRATION -> ModSounds.WEATHER_SERVO_LOCK;
			case SYNCHRONIZATION -> this.operation == WeatherOperation.THUNDER ? ModSounds.WEATHER_STORM_ACTIVATE : ModSounds.WEATHER_ARC;
			default -> ModSounds.WEATHER_ARC;
		}, 1.0F);
	}

	/** Applies the operation (weather at once; time at once or one step of a gradual change). */
	private void operate(ServerLevel level) {
		switch (this.operation) {
			case CLEAR, RAIN, THUNDER -> {
				this.applyWeather(level, !this.sustained);
				this.play(level, this.operation == WeatherOperation.THUNDER ? ModSounds.WEATHER_STORM_ACTIVATE : ModSounds.WEATHER_FIELD_ACTIVATE, 1.2F);
				this.play(level, ModSounds.WEATHER_BURST, 1.0F);
				if (this.sustained) {
					this.remaining = DURATIONS[this.durationIndex] * 60 * 20;
					this.enter(level, WeatherStage.STABILIZATION);
				} else {
					this.complete(level);
				}
			}
			case TIME_PRESET -> {
				Holder<WorldClock> clock = clock(level);
				if (clock != null) {
					long now = level.getServer().clockManager().getTotalTicks(clock);
					int forward = Math.floorMod(this.targetTime - (int) Math.floorMod(now, 24_000L), 24_000);
					level.getServer().clockManager().addTicks(clock, forward);
				}

				this.play(level, ModSounds.WEATHER_TIME_ACTIVATE, 1.0F);
				this.complete(level);
			}
			case TIME_GRADUAL -> {
				Holder<WorldClock> clock = clock(level);
				if (clock == null) {
					this.complete(level);
					return;
				}

				if (this.stageTicks == 0) {
					this.play(level, ModSounds.WEATHER_TIME_ACTIVATE, 1.0F);
				}

				this.stageTicks++;
				int delta = timeDelta(level.getServer().clockManager().getTotalTicks(clock), this.targetTime);
				if (delta == 0) {
					this.complete(level);
					return;
				}

				long upkeep = WeatherOperation.TIME_GRADUAL.upkeep();
				if (this.energy < upkeep) {
					this.error = Error.POWER_LOST;
					this.play(level, ModSounds.WEATHER_LOW_POWER, 1.0F);
					this.enter(level, WeatherStage.SHUTDOWN);
					return;
				}

				this.energy -= upkeep;
				this.usedThisSecond += upkeep;
				int step = Math.clamp(delta, -TIME_STEP, TIME_STEP);
				level.getServer().clockManager().addTicks(clock, step);
				this.remaining = Math.abs(delta - step) / TIME_STEP;
				this.syncDirty = true;
				if (step == delta) {
					// Landed on the target. Finish now: the clock's own tick would otherwise move it one past and
					// the machine would chase that single tick forever.
					this.complete(level);
				}
			}
			default -> this.complete(level);
		}
	}

	/** Shortest signed way (in clock ticks) from the current time of day to the target. */
	public static int timeDelta(long totalTicks, int target) {
		int now = (int) Math.floorMod(totalTicks, 24_000L);
		int forward = Math.floorMod(target - now, 24_000);
		return forward <= 12_000 ? forward : forward - 24_000;
	}

	private void complete(ServerLevel level) {
		this.operationsCompleted++;
		this.play(level, ModSounds.WEATHER_COMPLETE, 1.0F);
		this.enter(level, WeatherStage.SHUTDOWN);
	}

	private void stabilize(ServerLevel level) {
		long upkeep = this.operation.upkeep();
		if (this.energy < upkeep) {
			this.error = Error.POWER_LOST;
			this.play(level, ModSounds.WEATHER_LOW_POWER, 1.0F);
			this.enter(level, WeatherStage.SHUTDOWN);
			return;
		}

		this.energy -= upkeep;
		this.usedThisSecond += upkeep;
		boolean low = this.energy < upkeep * 600; // under 30 seconds left
		if (low && !this.lowPower && this.ticks % 20 == 0) {
			this.play(level, ModSounds.WEATHER_LOW_POWER, 0.8F);
		}

		this.lowPower = low;
		if (this.stageTicks++ % REFRESH == 0) {
			this.applyWeather(level, false);
		}

		if (--this.remaining <= 0) {
			this.complete(level);
		}
	}

	/** Sets the server's weather: a natural-length change, or a short hold that is refreshed while maintained. */
	private void applyWeather(ServerLevel level, boolean natural) {
		var random = level.getRandom();
		switch (this.operation) {
			case CLEAR -> level.getServer().setWeatherParameters(natural ? 12_000 + random.nextInt(6_000) : HOLD, 0, false, false);
			case RAIN -> level.getServer().setWeatherParameters(0, natural ? ServerLevel.RAIN_DURATION.sample(random) : HOLD, true, false);
			case THUNDER -> level.getServer().setWeatherParameters(0, natural ? ServerLevel.THUNDER_DURATION.sample(random) : HOLD, true, true);
			default -> {
			}
		}
	}

	private void enter(ServerLevel level, WeatherStage next) {
		this.stage = next;
		this.stageTicks = 0;
		this.syncDirty = true;
		this.setChanged();
		if (next == WeatherStage.SHUTDOWN) {
			this.play(level, ModSounds.WEATHER_SHUTDOWN, 1.0F);
		}
	}

	private void play(ServerLevel level, SoundEvent sound, float volume) {
		level.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, ModSounds.volume(volume), 1.0F);
	}

	/** Electrical arcs and the energy column; stronger for thunderstorms (server particles, cheap). */
	private void effects(ServerLevel level) {
		double x = this.worldPosition.getX() + 0.5;
		double y = this.worldPosition.getY() + 1.6;
		double z = this.worldPosition.getZ() + 0.5;
		if (this.stage == WeatherStage.CALIBRATION || this.stage == WeatherStage.SYNCHRONIZATION || this.stage == WeatherStage.STABILIZATION) {
			int arcs = this.operation == WeatherOperation.THUNDER ? 6 : 3;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, arcs, 0.8, 0.6, 0.8, 0.15);
		}

		if (this.stage == WeatherStage.OPERATION || this.stage == WeatherStage.STABILIZATION) {
			for (int i = 0; i < 6; i++) {
				level.sendParticles(this.operation.isTime() ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, x, y + 1 + i * 1.5, z, 1, 0.05, 0.3, 0.05, 0.0);
			}
		}
	}

	/** Test support only (the self-test simulates a drained buffer). */
	public void setEnergyForTest(long energy) {
		this.energy = Math.clamp(energy, 0L, CAPACITY);
		this.setChanged();
	}

	// ---- queries ----

	public WeatherStage getStage() {
		return this.stage;
	}

	public int getStageTicks() {
		return this.stageTicks;
	}

	public WeatherOperation getOperation() {
		return this.operation;
	}

	public Error getError() {
		return this.error;
	}

	public int getRemaining() {
		return this.remaining;
	}

	public boolean isLowPower() {
		return this.lowPower;
	}

	public long getOperationsCompleted() {
		return this.operationsCompleted;
	}

	public ContainerData getDataAccess() {
		return this.data;
	}

	// ---- energy ----

	@Override
	public Role energyRole() {
		return Role.CONSUMER;
	}

	@Override
	public long getEnergy() {
		return this.energy;
	}

	@Override
	public long getEnergyCapacity() {
		return CAPACITY;
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		long accepted = Math.max(0, Math.min(amount, Math.min(CAPACITY - this.energy, MAX_INPUT - this.energyReceivedThisTick)));
		if (!simulate && accepted > 0) {
			this.energy += accepted;
			this.energyReceivedThisTick += accepted;
			this.receivedThisSecond += accepted;
			this.setChanged();
		}

		return accepted;
	}

	@Override
	public long extractEnergy(long amount, boolean simulate) {
		return 0;
	}

	@Override
	public void restoreEnergy(long amount) {
		this.energy = Math.min(CAPACITY, this.energy + Math.max(0, amount));
		this.setChanged();
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.solarquarry.weather_control_machine");
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new WeatherMachineMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence and sync ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		WeatherStage before = this.stage;
		this.energy = Math.clamp(input.getLongOr("Energy", this.energy), 0L, CAPACITY);
		this.stage = WeatherStage.byOrdinal(input.getIntOr("Stage", 0));
		this.stageTicks = Math.max(0, input.getIntOr("StageTicks", 0));
		this.operation = WeatherOperation.byOrdinal(input.getIntOr("Operation", 0));
		this.sustained = input.getBooleanOr("Sustained", false);
		this.durationIndex = Math.floorMod(input.getIntOr("Duration", 1), DURATIONS.length);
		this.gradual = input.getBooleanOr("Gradual", false);
		this.manualTarget = Math.floorMod(input.getIntOr("ManualTarget", 6_000), 24_000);
		this.targetTime = Math.floorMod(input.getIntOr("TargetTime", 0), 24_000);
		this.remaining = Math.max(0, input.getIntOr("Remaining", 0));
		this.error = Error.values()[Math.clamp(input.getIntOr("Error", 0), 0, Error.values().length - 1)];
		this.lowPower = input.getBooleanOr("LowPower", false);
		this.operationsCompleted = Math.max(0, input.getLongOr("Completed", 0L));
		if (this.level != null && this.level.isClientSide() && before != this.stage && clientStageListener != null) {
			clientStageListener.accept(this);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", this.energy);
		this.writeSync(output);
		output.putBoolean("Sustained", this.sustained);
		output.putInt("Duration", this.durationIndex);
		output.putBoolean("Gradual", this.gradual);
		output.putInt("ManualTarget", this.manualTarget);
		output.putInt("TargetTime", this.targetTime);
		output.putInt("Remaining", this.remaining);
		output.putInt("Error", this.error.ordinal());
		output.putBoolean("LowPower", this.lowPower);
		output.putLong("Completed", this.operationsCompleted);
	}

	private void writeSync(ValueOutput output) {
		output.putInt("Stage", this.stage.ordinal());
		output.putInt("StageTicks", this.stageTicks);
		output.putInt("Operation", this.operation.ordinal());
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		this.writeSync(output);
		output.putLong("Energy", this.energy);
		return output.buildResult();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (this.level != null && this.level.isClientSide() && clientStageListener != null) {
			clientStageListener.accept(this);
		}
	}
}
