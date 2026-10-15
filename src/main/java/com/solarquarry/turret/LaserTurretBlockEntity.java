package com.solarquarry.turret;

import com.solarquarry.energy.EnergyNode;
import com.solarquarry.item.Dismantlable;
import com.solarquarry.registry.ModBlockEntities;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Laser Turret: looks for hostile mobs in range ({@link TurretTargeting}) at most twice a second, keeps its target
 * while it stays valid, in range and in sight, and fires a laser that deals real damage on the server, paid from its
 * energy buffer per shot. Without energy it stops firing. Structure turrets are either damaged (never fire) or hold
 * a finite charge and no power supply, so they fall silent once it is spent.
 */
public class LaserTurretBlockEntity extends BlockEntity implements EnergyNode, ExtendedMenuProvider<BlockPos>, Dismantlable {
	public static final int SCAN_INTERVAL = 10;
	public static final int DATA_COUNT = 10;
	public static final int BUTTON_POWER = 0;
	public static final int BUTTON_MODE = 1;
	private static final DustParticleOptions BEAM = new DustParticleOptions(0xFF3B2E, 1.0F);

	public enum Status {
		OFF,
		IDLE,
		FIRING,
		NO_POWER,
		DAMAGED
	}

	private long energy;
	private boolean enabled = true;
	private boolean damaged;
	private TurretTargeting.Mode mode = TurretTargeting.Mode.ALL_HOSTILE;
	private Status status = Status.IDLE;
	private int cooldown;
	private long ticks;
	private @Nullable UUID targetId;
	private int targetNetworkId = -1;
	private long shots;
	private long energyReceivedThisTick;
	private long receivedThisSecond;
	private long receivedLastSecond;

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			LaserTurretBlockEntity t = LaserTurretBlockEntity.this;
			long capacity = t.tier().capacity();
			return switch (index) {
				case 0 -> (int) (t.energy & 0xFFFF);
				case 1 -> (int) ((t.energy >>> 16) & 0xFFFF);
				case 2 -> (int) (capacity & 0xFFFF);
				case 3 -> (int) ((capacity >>> 16) & 0xFFFF);
				case 4 -> t.status.ordinal();
				case 5 -> t.mode.ordinal();
				case 6 -> t.tier().ordinal();
				case 7 -> t.targetNetworkId;
				case 8 -> (int) Math.min(0x7FFF, t.receivedLastSecond / 20);
				case 9 -> (int) Math.min(0x7FFF, t.shots);
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

	public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.LASER_TURRET, pos, state);
	}

	public TurretTier tier() {
		return this.getBlockState().getBlock() instanceof LaserTurretBlock block ? block.tier() : TurretTier.BASIC;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, LaserTurretBlockEntity turret) {
		if (level instanceof ServerLevel server) {
			turret.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		this.ticks++;
		this.energyReceivedThisTick = 0;
		if (this.ticks % 20 == 0) {
			this.receivedLastSecond = this.receivedThisSecond;
			this.receivedThisSecond = 0;
		}

		if (this.cooldown > 0) {
			this.cooldown--;
		}

		if (this.damaged) {
			this.setStatus(Status.DAMAGED);
			if (this.ticks % 40 == 0) {
				Vec3 c = this.emitter();
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.2, 0.1, 0.2, 0.05);
			}

			return;
		}

		if (!this.enabled) {
			this.target(null);
			this.setStatus(Status.OFF);
			return;
		}

		LivingEntity target = this.currentTarget(level);
		if (target == null && this.ticks % SCAN_INTERVAL == 0) {
			target = this.findTarget(level);
			this.target(target);
		}

		if (target == null) {
			this.setStatus(this.energy < this.tier().shotCost() ? Status.NO_POWER : Status.IDLE);
			return;
		}

		if (this.energy < this.tier().shotCost()) {
			this.setStatus(Status.NO_POWER);
			return;
		}

		this.setStatus(Status.FIRING);
		if (this.cooldown <= 0) {
			this.fire(level, target);
		}
	}

	private void setStatus(Status status) {
		if (this.status != status) {
			this.status = status;
			this.setChanged();
		}
	}

	private void target(@Nullable LivingEntity target) {
		this.targetId = target == null ? null : target.getUUID();
		this.targetNetworkId = target == null ? -1 : target.getId();
	}

	public Vec3 emitter() {
		return Vec3.atCenterOf(this.worldPosition).add(0, 0.25, 0);
	}

	/** The kept target if it is still valid, in range and in sight. */
	private @Nullable LivingEntity currentTarget(ServerLevel level) {
		if (this.targetId == null) {
			return null;
		}

		Entity entity = level.getEntity(this.targetId);
		if (entity instanceof LivingEntity living && this.canShoot(level, living)) {
			return living;
		}

		this.target(null);
		return null;
	}

	public boolean canShoot(ServerLevel level, LivingEntity target) {
		if (!TurretTargeting.isTarget(target, this.mode)) {
			return false;
		}

		double range = this.tier().range();
		if (target.distanceToSqr(this.emitter()) > range * range) {
			return false;
		}

		Vec3 eye = target.getEyePosition();
		Vec3 from = this.emitter();
		// Start the sight line just outside the turret's own head, which would otherwise block it.
		Vec3 start = from.add(eye.subtract(from).normalize().scale(0.8));
		return level.clip(new ClipContext(start, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext_empty())).getType() == HitResult.Type.MISS;
	}

	private static net.minecraft.world.phys.shapes.CollisionContext CollisionContext_empty() {
		return net.minecraft.world.phys.shapes.CollisionContext.empty();
	}

	/** Nearest valid target in range and in sight (one bounded entity query). */
	public @Nullable LivingEntity findTarget(ServerLevel level) {
		int range = this.tier().range();
		AABB box = new AABB(this.worldPosition).inflate(range);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> TurretTargeting.isTarget(m, this.mode))) {
			double d = mob.distanceToSqr(this.emitter());
			if (d < bestDistance && this.canShoot(level, mob)) {
				best = mob;
				bestDistance = d;
			}
		}

		return best;
	}

	/** One shot: pays first, then damages on the server and draws the beam. */
	private void fire(ServerLevel level, LivingEntity target) {
		TurretTier tier = this.tier();
		this.energy -= tier.shotCost();
		this.cooldown = tier.cooldown();
		this.shots++;
		this.setChanged();
		target.hurtServer(level, level.damageSources().magic(), tier.damage());
		Vec3 from = this.emitter();
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 step = to.subtract(from);
		int points = (int) Math.ceil(step.length() * 3);
		for (int i = 0; i <= points; i++) {
			Vec3 p = from.add(step.scale(i / (double) points));
			level.sendParticles(BEAM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}

		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 4, 0.15, 0.15, 0.15, 0.05);
		level.playSound(null, this.worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.35F, 1.9F);
	}

	// ---- settings ----

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		this.setChanged();
	}

	public TurretTargeting.Mode getMode() {
		return this.mode;
	}

	public void setMode(TurretTargeting.Mode mode) {
		this.mode = mode;
		this.target(null);
		this.setChanged();
	}

	public boolean isDamaged() {
		return this.damaged;
	}

	/** Structure turrets: a wrecked one never fires (and drops only as a block). */
	public void setDamaged(boolean damaged) {
		this.damaged = damaged;
		this.setChanged();
	}

	public Status getStatus() {
		return this.status;
	}

	public long getShots() {
		return this.shots;
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
		return this.tier().capacity();
	}

	@Override
	public long insertEnergy(long amount, boolean simulate) {
		long accepted = Math.max(0, Math.min(amount, Math.min(this.tier().capacity() - this.energy, this.tier().maxInput() - this.energyReceivedThisTick)));
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
		this.energy = Math.min(this.tier().capacity(), this.energy + Math.max(0, amount));
		this.setChanged();
	}

	// ---- menu ----

	@Override
	public Component getDisplayName() {
		return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new LaserTurretMenu(containerId, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	// ---- persistence ----

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.energy = Math.clamp(input.getLongOr("Energy", 0L), 0L, this.tier().capacity());
		this.enabled = input.getBooleanOr("Enabled", true);
		this.damaged = input.getBooleanOr("Damaged", false);
		this.mode = TurretTargeting.Mode.byOrdinal(input.getIntOr("Mode", 0));
		this.shots = Math.max(0, input.getLongOr("Shots", 0L));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("Energy", this.energy);
		output.putBoolean("Enabled", this.enabled);
		output.putBoolean("Damaged", this.damaged);
		output.putInt("Mode", this.mode.ordinal());
		output.putLong("Shots", this.shots);
	}

	@Override
	public CompoundTag extractForPickup(HolderLookup.Provider registries) {
		CompoundTag data = this.saveCustomOnly(registries);
		this.energy = 0;
		this.setChanged();
		return data;
	}
}
