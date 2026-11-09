package com.aureliatransit.architecture.station;

/**
 * A block a planning rule asked for in a cell an earlier rule had already taken or kept clear. The earlier rule wins, as the
 * planner has always worked; recording the loss lets the studio and the audit show what went missing instead of failing
 * silently.
 *
 * @param role      the role the later rule wanted
 * @param blockId   the block the later rule wanted there
 * @param keptClear true when the cell was kept clear (a doorway, stair headroom) rather than built on
 */
public record SkippedBlock(Role role, String blockId, int u, int y, int v, boolean keptClear) {
}
