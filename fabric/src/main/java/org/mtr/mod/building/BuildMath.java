package org.mtr.mod.building;

/**
 * Volume, yaw snap and the quadratic preview for the building tools. Pure: the tests call it, and the renderer fills one
 * reused buffer from {@link #curve}.
 */
public final class BuildMath {

	public static final int MAX_SIDE = 48;
	public static final int CURVE_SAMPLES = 8;

	public enum Mode {
		FULL, ABOVE, SHELL;

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Mode byOrdinal(int ordinal) {
			final Mode[] values = values();
			if (ordinal < 0 || ordinal >= values.length) {
				return FULL;
			}
			return values[ordinal];
		}
	}

	private BuildMath() {
	}

	public static int side(int a, int b) {
		return Math.abs(a - b) + 1;
	}

	/** Inclusive block count. {@link Mode#ABOVE} leaves the floor row in place, so a one-block-tall box counts as nothing. */
	public static long volume(int x0, int y0, int z0, int x1, int y1, int z1, Mode mode) {
		final long dx = side(x0, x1);
		final long dy = side(y0, y1);
		final long dz = side(z0, z1);
		if (mode == Mode.ABOVE) {
			return dx * Math.max(0, dy - 1) * dz;
		}
		return dx * dy * dz;
	}

	public static boolean sidesFit(int x0, int y0, int z0, int x1, int y1, int z1) {
		return side(x0, x1) <= MAX_SIDE && side(y0, y1) <= MAX_SIDE && side(z0, z1) <= MAX_SIDE;
	}

	public static boolean volumeFits(long blocks, int max) {
		return blocks > 0 && blocks <= max;
	}

	/** Block at {@code index} inside the box, x then y then z. {@link Mode#ABOVE} starts one above the lower Y. */
	public static void at(int index, int x0, int y0, int z0, int x1, int y1, int z1, Mode mode, int[] out) {
		final int minX = Math.min(x0, x1);
		final int minY = Math.min(y0, y1);
		final int minZ = Math.min(z0, z1);
		final int dy = side(y0, y1);
		final int dz = side(z0, z1);
		final int spanY = mode == Mode.ABOVE ? Math.max(1, dy - 1) : dy;
		final int yBase = mode == Mode.ABOVE ? minY + 1 : minY;
		final int slab = spanY * dz;
		out[0] = minX + index / slab;
		final int rem = index % slab;
		out[1] = yBase + rem / dz;
		out[2] = minZ + rem % dz;
	}

	public static boolean shell(int x, int y, int z, int x0, int y0, int z0, int x1, int y1, int z1) {
		final int minX = Math.min(x0, x1);
		final int maxX = Math.max(x0, x1);
		final int minY = Math.min(y0, y1);
		final int maxY = Math.max(y0, y1);
		final int minZ = Math.min(z0, z1);
		final int maxZ = Math.max(z0, z1);
		return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
	}

	/** Degrees in {@code [0, 360)}. A step of 0 keeps the nearest degree. */
	public static int snapYaw(double yaw, int step) {
		final int rounded = (int) Math.round(yaw);
		if (step <= 0) {
			return Math.floorMod(rounded, 360);
		}
		return Math.floorMod((int) Math.round(rounded / (double) step) * step, 360);
	}

	/**
	 * Horizontal step along a Minecraft yaw (0 is south, +Z; 90 is west, -X).
	 *
	 * @param out length 2, dx then dz
	 */
	public static void aim(int distance, int yawDegrees, int[] out) {
		final double radians = Math.toRadians(yawDegrees);
		out[0] = (int) Math.round(-Math.sin(radians) * distance);
		out[1] = (int) Math.round(Math.cos(radians) * distance);
	}

	/**
	 * Quadratic from the start, through a control pulled along the two yaws, to the end. A straight pair of yaws stays on
	 * the chord. {@code out} is x, y, z.
	 */
	public static void curve(double t, double x0, double y0, double z0, double x1, double y1, double z1, int yaw0, int yaw1, double[] out) {
		final double half = Math.hypot(x1 - x0, z1 - z0) * 0.5;
		final double r0 = Math.toRadians(yaw0);
		final double r1 = Math.toRadians(yaw1);
		final double cx = (x0 - Math.sin(r0) * half + x1 + Math.sin(r1) * half) * 0.5;
		final double cy = (y0 + y1) * 0.5;
		final double cz = (z0 + Math.cos(r0) * half + z1 - Math.cos(r1) * half) * 0.5;
		final double u = 1 - t;
		out[0] = u * u * x0 + 2 * u * t * cx + t * t * x1;
		out[1] = u * u * y0 + 2 * u * t * cy + t * t * y1;
		out[2] = u * u * z0 + 2 * u * t * cz + t * t * z1;
	}
}
