package org.mtr.mod.zip;

/**
 * One tick of travel along a {@link ZipCurve}. Gravity lines speed up downhill and slow uphill. Seat lines accelerate
 * toward the panel speed and brake across a window so a single step cannot pass the pole.
 */
public final class ZipTravel {

	/** Blocks per second squared. Milder than vanilla so a short drop does not hit the cap in one tick. */
	public static final double GRAVITY = 8.0;
	public static final double TICK_SECONDS = 0.05;
	/** The lower pole must be at least this far below the start. */
	public static final double MIN_DROP = 0.5;

	private ZipTravel() {
	}

	public record Step(double t, double speed, boolean release, boolean arrived) {
	}

	public static boolean mayDrop(double startY, double endY) {
		return startY > endY + MIN_DROP;
	}

	/**
	 * @param direction +1 toward the second pole, -1 toward the first
	 */
	public static Step gravity(double t, double speed, int direction, double dt, double x0, double y0, double z0, double x1, double y1, double z1, double sag, double maxSpeed, double brake) {
		final int dir = direction < 0 ? -1 : 1;
		final double slope = ZipCurve.sinSlope(t, x0, y0, z0, x1, y1, z1, sag);
		final double accel = -dir * GRAVITY * slope - brake * speed;
		final double capped = clamp(speed + accel * dt, 0, Math.max(0, maxSpeed));
		final double advance = capped * dt / ZipCurve.dsDt(t, x0, y0, z0, x1, y1, z1, sag);
		final double next = t + dir * advance;
		if (dir > 0 && next >= 1) {
			return new Step(1, 0, true, true);
		}
		if (dir < 0 && next <= 0) {
			return new Step(0, 0, true, true);
		}
		return new Step(next, capped, false, false);
	}

	/**
	 * Seat travel. {@code arrived} means the seat is waiting at a pole. {@code release} stays false: the rider gets off
	 * by sneaking, or by the line being removed.
	 */
	public static Step seat(double t, double speed, int direction, double dt, double x0, double y0, double z0, double x1, double y1, double z1, double sag, double maxSpeed, double brake) {
		final int dir = direction < 0 ? -1 : 1;
		final double ds = ZipCurve.dsDt(t, x0, y0, z0, x1, y1, z1, sag);
		final double arcLeft = (dir > 0 ? 1.0 - t : t) * ds;
		final double window = 1.5 + Math.max(0, brake) * 8.0;
		final double limit = Math.max(0, maxSpeed);
		final double cap = arcLeft < window ? limit * (arcLeft / window) : limit;
		final double nextSpeed = Math.min(cap, speed + limit * 2.0 * dt);
		if (arcLeft < 0.3 || nextSpeed < 0.05 && arcLeft < window) {
			return new Step(dir > 0 ? 1 : 0, 0, false, true);
		}
		final double advance = nextSpeed * dt / ds;
		final double next = t + dir * advance;
		if (dir > 0 && next >= 1) {
			return new Step(1, 0, false, true);
		}
		if (dir < 0 && next <= 0) {
			return new Step(0, 0, false, true);
		}
		return new Step(next, nextSpeed, false, false);
	}

	private static double clamp(double value, double min, double max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
