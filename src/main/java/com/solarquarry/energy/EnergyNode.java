package com.solarquarry.energy;

/**
 * A block entity that participates in a Solar Quarry energy network.
 * All transfers are exact: an amount is only added to a receiver after the same
 * amount was removed from a sender, so energy can never be duplicated.
 */
public interface EnergyNode {
	enum Role {
		PRODUCER,
		STORAGE,
		CONSUMER
	}

	Role energyRole();

	long getEnergy();

	long getEnergyCapacity();

	/** @return the amount accepted (or that would be accepted when simulating). */
	long insertEnergy(long amount, boolean simulate);

	/** @return the amount removed (or that would be removed when simulating). */
	long extractEnergy(long amount, boolean simulate);

	/** Puts back energy that was just extracted from this node but could not be delivered. */
	void restoreEnergy(long amount);
}
