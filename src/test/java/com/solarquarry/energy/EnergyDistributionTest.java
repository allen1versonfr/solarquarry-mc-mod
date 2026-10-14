package com.solarquarry.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnergyDistributionTest {
	/** Minimal node with a capacity and a per-call transfer limit. */
	static final class Node implements EnergyNode {
		long energy;
		final long capacity;
		final long rate;

		Node(long energy, long capacity, long rate) {
			this.energy = energy;
			this.capacity = capacity;
			this.rate = rate;
		}

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
			return this.capacity;
		}

		@Override
		public long insertEnergy(long amount, boolean simulate) {
			long accepted = Math.max(0, Math.min(amount, Math.min(this.rate, this.capacity - this.energy)));
			if (!simulate) {
				this.energy += accepted;
			}

			return accepted;
		}

		@Override
		public long extractEnergy(long amount, boolean simulate) {
			long extracted = Math.max(0, Math.min(amount, this.energy));
			if (!simulate) {
				this.energy -= extracted;
			}

			return extracted;
		}

		@Override
		public void restoreEnergy(long amount) {
			this.energy += amount;
		}
	}

	@Test
	void energyIsConservedAndLimited() {
		Node source = new Node(1_000, 1_000, Long.MAX_VALUE);
		Node a = new Node(0, 10_000, 10_000);
		Node b = new Node(0, 10_000, 10_000);
		long moved = EnergyNetwork.distributeAmong(source, List.of(a, b), 300, 0);
		assertEquals(300, moved);
		assertEquals(1_000, source.energy + a.energy + b.energy, "nothing created or destroyed");
		assertEquals(150, a.energy);
		assertEquals(150, b.energy);
	}

	@Test
	void neverSendsMoreThanTheSourceHas() {
		Node source = new Node(50, 1_000, Long.MAX_VALUE);
		Node a = new Node(0, 10_000, 10_000);
		long moved = EnergyNetwork.distributeAmong(source, List.of(a), 10_000, 0);
		assertEquals(50, moved);
		assertEquals(0, source.energy);
	}

	@Test
	void fullOrRateLimitedReceiversGetOnlyWhatTheyAccept() {
		Node source = new Node(1_000, 1_000, Long.MAX_VALUE);
		Node full = new Node(100, 100, 1_000);
		Node slow = new Node(0, 10_000, 30);
		Node open = new Node(0, 10_000, 10_000);
		long moved = EnergyNetwork.distributeAmong(source, List.of(full, slow, open), 600, 0);
		assertEquals(100, full.energy, "a full receiver is skipped");
		assertEquals(30, slow.energy, "the transfer limit is respected");
		assertEquals(570, open.energy, "the rest goes to receivers with room");
		assertEquals(600, moved);
		assertEquals(1_100, source.energy + full.energy + slow.energy + open.energy);
	}

	@Test
	void rotationSpreadsOddRemaindersFairly() {
		long totalA = 0;
		long totalB = 0;
		for (int tick = 0; tick < 100; tick++) {
			Node source = new Node(1, 1, Long.MAX_VALUE);
			Node a = new Node(0, 10, 10);
			Node b = new Node(0, 10, 10);
			EnergyNetwork.distributeAmong(source, List.of(a, b), 1, tick);
			totalA += a.energy;
			totalB += b.energy;
		}

		assertEquals(50, totalA);
		assertEquals(50, totalB);
	}

	@Test
	void nothingHappensWithoutReceiversOrBudget() {
		Node source = new Node(500, 500, Long.MAX_VALUE);
		assertEquals(0, EnergyNetwork.distributeAmong(source, List.of(), 100, 0));
		assertEquals(0, EnergyNetwork.distributeAmong(source, List.of(new Node(0, 10, 10)), 0, 0));
		assertTrue(source.energy == 500);
	}
}
