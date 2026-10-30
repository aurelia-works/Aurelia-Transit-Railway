package com.aureliatransit.architecture.airport;

import java.util.List;

/**
 * Whether a jet bridge is docking, and how far it has travelled. Pure: the block entity and the tests both call this,
 * and nothing here reads the world.
 *
 * <p>A bridge extends only for an airplane that is stopped and whose doors are opening. Doors open because a platform
 * block is beside the doorway ({@code RenderVehicleHelper}); the apron platform is that block at a stand. A moving
 * airplane, or one whose doors have shut, retracts the bridge. The reach is short of the next stand (28 blocks on a
 * compact airport, 66 on a real one) and short of the taxiway.
 */
public final class JetBridgeMotion {

	/** Chebyshev blocks from the bridge head to a door that still counts as this stand. */
	public static final double REACH = 14;
	/** Fraction of the travel covered in one client tick. Forty ticks is a full extend or retract. */
	public static final float STEP = 1f / 40f;

	private JetBridgeMotion() {
	}

	public static boolean wants(double x, double y, double z, List<AircraftSample> aircraft) {
		if (aircraft == null) {
			return false;
		}
		for (final AircraftSample sample : aircraft) {
			if (!sample.airplane() || sample.moving() || sample.doorValue() <= 0) {
				continue;
			}
			final double distance = Math.max(Math.abs(sample.x() - x), Math.max(Math.abs(sample.y() - y), Math.abs(sample.z() - z)));
			if (distance <= REACH) {
				return true;
			}
		}
		return false;
	}

	/** Moves {@code current} one step toward 1 when {@code extend} is set, and toward 0 otherwise. Both ends clamp. */
	public static float step(float current, boolean extend) {
		if (extend) {
			return Math.min(1f, current + STEP);
		}
		return Math.max(0f, current - STEP);
	}
}
