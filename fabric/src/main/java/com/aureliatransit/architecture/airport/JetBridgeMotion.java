package com.aureliatransit.architecture.airport;

import java.util.List;

/**
 * Whether a jet bridge is docking, and how far it has travelled. Pure: the block entity and the tests both call this,
 * and nothing here reads the world.
 *
 * <p>A bridge extends only for an airplane that is stopped at its stand with its doors open (MTR's door value, which runs through
 * the dwell whether or not the doors are drawn open). A moving airplane, or one whose doors have shut, retracts the bridge. The
 * door is drawn open once the docked bridge has laid its deck, whose blocks are MTR platform blocks. The reach is short of the next stand (28 blocks on a
 * compact airport, 66 on a real one) and short of the taxiway.
 */
public final class JetBridgeMotion {

	/** Chebyshev blocks from the bridge head to a door that still counts as this stand. */
	public static final double REACH = 14;
	/** Fraction of the travel covered in one client tick. Forty ticks is a full extend or retract. */
	public static final float STEP = 1f / 40f;

	private JetBridgeMotion() {
	}

	/** Blocks from a plane's head (MTR's car front) back to the middle of its forward door: MTR's A320 model, 19 pixels. */
	public static final double DOOR_BEHIND_HEAD = 1.2;
	/** The cab's depth: three blocks, so its middle is 1.5 past where it leaves the bridge block's face. */
	public static final double CAB_DEPTH = 3;
	/** The bridge block sits in the fixed tube's roof, this many blocks over the walking level, so the tube's end stays open. */
	public static final int ROOF_ABOVE_WALK = 2;
	/** The furthest the telescoping section runs out. */
	public static final double MAX_TRAVEL = 12;

	/**
	 * How far the cab must run out of the bridge block (centre at x, y, z) along its facing (fx, fz, a unit step) to stop beside
	 * the forward door of the nearest stopped plane with open doors, or a negative number when no plane is docking. The plane
	 * points its nose at the terminal, against the facing, so its door is {@link #DOOR_BEHIND_HEAD} further out than its head.
	 */
	public static double travel(double x, double y, double z, int fx, int fz, List<AircraftSample> aircraft) {
		if (aircraft == null) {
			return -1;
		}
		double best = -1;
		double nearest = Double.MAX_VALUE;
		for (final AircraftSample sample : aircraft) {
			if (!sample.airplane() || sample.moving() || sample.doorValue() <= 0) {
				continue;
			}
			final double distance = Math.max(Math.abs(sample.x() - x), Math.max(Math.abs(sample.y() - y), Math.abs(sample.z() - z)));
			if (distance > REACH || distance >= nearest) {
				continue;
			}
			nearest = distance;
			final double door = (sample.x() - x) * fx + (sample.z() - z) * fz + DOOR_BEHIND_HEAD;
			// the block's face is half a block out from its centre; the cab's middle is half its depth past that
			best = Math.max(0, Math.min(MAX_TRAVEL, door - 0.5 - CAB_DEPTH / 2));
		}
		return best;
	}

	/** Moves {@code current} one step toward 1 when {@code extend} is set, and toward 0 otherwise. Both ends clamp. */
	public static float step(float current, boolean extend) {
		if (extend) {
			return Math.min(1f, current + STEP);
		}
		return Math.max(0f, current - STEP);
	}
}
