package com.solarquarry.energy;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Per-node cache of reachable endpoints, rebuilt when the topology changes or every few seconds. */
public final class EndpointCache {
	private static final int REFRESH_INTERVAL = 100;
	private List<BlockPos> endpoints = List.of();
	private int version = -1;
	private long refreshedAt = Long.MIN_VALUE;

	public List<BlockPos> get(Level level, BlockPos origin) {
		long now = level.getGameTime();
		int currentVersion = EnergyNetwork.topologyVersion();
		if (currentVersion != this.version || now - this.refreshedAt >= REFRESH_INTERVAL || now < this.refreshedAt) {
			this.endpoints = EnergyNetwork.discoverEndpoints(level, origin);
			this.version = currentVersion;
			this.refreshedAt = now;
		}

		return this.endpoints;
	}
}
