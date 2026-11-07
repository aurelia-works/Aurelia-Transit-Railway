package com.aureliatransit.architecture.station;

import java.util.Objects;

/**
 * One player edit of a planned cell, saved with the station: the block to stand there instead of the planner's choice. The
 * override is applied after every planning rule, so a fine edit never changes the rest of the plan.
 *
 * @param blockId a namespaced block id, or {@code minecraft:air} to clear the cell
 * @param front   where the block's front points
 * @param state   extra block state properties, empty for none
 */
public record StationOverride(String blockId, LocalDir front, String state) {

	public StationOverride {
		Objects.requireNonNull(blockId, "blockId");
		Objects.requireNonNull(front, "front");
		Objects.requireNonNull(state, "state");
	}

	public StationOverride(String blockId, LocalDir front) {
		this(blockId, front, "");
	}
}
