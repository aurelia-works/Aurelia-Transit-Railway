package org.mtr.mod.zip;

/**
 * A cable from one pole to the other. Horizontally it is a straight lerp. Vertically it is the same lerp minus a
 * parabola, so the middle hangs by {@code sag * horizontal span}. {@code sag} is a fraction of that span (0 is tight).
 * Points are written into a caller-owned array so rendering and travel can reuse it.
 */
public final class ZipCurve {

	private ZipCurve() {
	}

	public static double clamp01(double t) {
		if (t < 0) {
			return 0;
		}
		if (t > 1) {
			return 1;
		}
		return t;
	}

	public static double horizontalSpan(double x0, double z0, double x1, double z1) {
		final double dx = x1 - x0;
		final double dz = z1 - z0;
		return Math.hypot(dx, dz);
	}

	/** How far the middle of the cable hangs below the straight line, in blocks. */
	public static double midDrop(double x0, double z0, double x1, double z1, double sag) {
		return 4.0 * sag * horizontalSpan(x0, z0, x1, z1) * 0.25;
	}

	public static void point(double t, double x0, double y0, double z0, double x1, double y1, double z1, double sag, double[] out) {
		final double u = clamp01(t);
		final double h = horizontalSpan(x0, z0, x1, z1);
		out[0] = x0 + (x1 - x0) * u;
		out[1] = y0 + (y1 - y0) * u - 4.0 * sag * h * u * (1.0 - u);
		out[2] = z0 + (z1 - z0) * u;
	}

	/** dy/dt of the cable, blocks of height per unit of t. */
	public static double dyDt(double t, double y0, double y1, double horizontalSpan, double sag) {
		final double u = clamp01(t);
		return (y1 - y0) - 4.0 * sag * horizontalSpan * (1.0 - 2.0 * u);
	}

	/** Blocks of cable per unit of t. Never zero. */
	public static double dsDt(double t, double x0, double y0, double z0, double x1, double y1, double z1, double sag) {
		final double h = horizontalSpan(x0, z0, x1, z1);
		final double dy = dyDt(t, y0, y1, h, sag);
		final double ds = Math.sqrt(h * h + dy * dy);
		return ds < 1.0e-4 ? 1.0e-4 : ds;
	}

	/**
	 * Rise of the cable as t increases, divided by arc length. Positive when the cable climbs toward the second pole.
	 */
	public static double sinSlope(double t, double x0, double y0, double z0, double x1, double y1, double z1, double sag) {
		final double h = horizontalSpan(x0, z0, x1, z1);
		final double dy = dyDt(t, y0, y1, h, sag);
		return dy / dsDt(t, x0, y0, z0, x1, y1, z1, sag);
	}
}
