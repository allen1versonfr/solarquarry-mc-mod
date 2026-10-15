package com.solarquarry.construction;

/**
 * Lifecycle of a self-building structure.
 * <pre>
 *   IDLE --plan--> VALIDATING --> BUILDING --last block--> COMPLETED
 *   BUILDING --obstruction / no power / unloaded--> PAUSED --cleared--> BUILDING
 *   COMPLETED --a built block went missing--> BUILDING (repair)
 *   VALIDATING --empty or impossible plan--> ERROR
 * </pre>
 */
public enum ConstructionState {
	IDLE,
	VALIDATING,
	BUILDING,
	PAUSED,
	COMPLETED,
	ERROR;

	public static ConstructionState byOrdinal(int ordinal) {
		ConstructionState[] values = values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
	}
}
