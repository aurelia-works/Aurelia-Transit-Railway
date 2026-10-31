package org.mtr.mod.sound;

/**
 * How loud the flange should be on a curve. BVE packs already load {@code flange[]} clips; this only picks the gain,
 * the pitch and which loaded slot to play. A straight or a crawl stays silent.
 */
public final class FlangeSqueal {

	/** About a 250-block radius: gentler than this does not squeal. */
	public static final double CURVE_START = 1.0 / 250.0;
	/** About a 30-block radius: full squeal. */
	public static final double CURVE_FULL = 1.0 / 30.0;
	public static final float SPEED_START_KMH = 8f;
	public static final float SPEED_FULL_KMH = 45f;
	/** Below this movement the heading is noise, not a curve. */
	public static final double MIN_STEP = 0.05;

	private FlangeSqueal() {
	}

	public static float gain(double curvatureRadPerBlock, float speedKilometersPerHour) {
		if (!(curvatureRadPerBlock >= CURVE_START) || speedKilometersPerHour < SPEED_START_KMH) {
			return 0f;
		}
		final float tight = (float) Math.min(1.0, (curvatureRadPerBlock - CURVE_START) / (CURVE_FULL - CURVE_START));
		final float fast = Math.min(1f, (speedKilometersPerHour - SPEED_START_KMH) / (SPEED_FULL_KMH - SPEED_START_KMH));
		return tight * fast;
	}

	public static float pitch(float speedKilometersPerHour) {
		return Math.min(1.6f, 0.85f + Math.max(0f, speedKilometersPerHour) / 140f);
	}

	/** Which loaded flange clip to use. One clip always uses slot 0; extra clips step up with speed. */
	public static int slot(float speedKilometersPerHour, int loaded) {
		if (loaded <= 1) {
			return 0;
		}
		final int band = (int) (Math.max(0f, speedKilometersPerHour) / 40f);
		return Math.min(loaded - 1, band);
	}

	/** Absolute heading change per block, in radians. A tiny step or a non-finite heading is straight. */
	public static double curvature(double previousHeading, double heading, double distance) {
		if (!(distance >= MIN_STEP) || !Double.isFinite(previousHeading) || !Double.isFinite(heading)) {
			return 0;
		}
		double delta = heading - previousHeading;
		while (delta > Math.PI) {
			delta -= Math.PI * 2;
		}
		while (delta < -Math.PI) {
			delta += Math.PI * 2;
		}
		return Math.abs(delta) / distance;
	}

	public static double heading(double dx, double dz) {
		return Math.atan2(dz, dx);
	}
}
