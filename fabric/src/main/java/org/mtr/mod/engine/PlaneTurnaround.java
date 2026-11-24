package org.mtr.mod.engine;

/**
 * How long an airplane stays at the gate between flights. Real turnarounds run about 25 minutes for a regional plane,
 * 40 for a narrow-body and 70 for a wide-body; here they run on Minecraft's clock, where an hour passes in 50 seconds.
 */
public final class PlaneTurnaround {

	/** Real milliseconds in one Minecraft minute. */
	public static final long MILLIS_PER_GAME_MINUTE = 50_000 / 60;
	/** Up to this length, in blocks, a plane is regional. */
	static final double REGIONAL_LENGTH = 35;
	/** Up to this length, a narrow-body; longer is a wide-body. */
	static final double NARROW_BODY_LENGTH = 55;

	private PlaneTurnaround() {
	}

	/** Game minutes at the gate for a plane this long. */
	public static int minutes(double planeLength) {
		return planeLength <= REGIONAL_LENGTH ? 25 : planeLength <= NARROW_BODY_LENGTH ? 40 : 70;
	}

	/** The dwell to use at a gate: the platform's own dwell, or the turnaround if that is longer. */
	public static long dwellMillis(long platformDwellMillis, double planeLength) {
		return Math.max(platformDwellMillis, minutes(planeLength) * MILLIS_PER_GAME_MINUTE);
	}
}
