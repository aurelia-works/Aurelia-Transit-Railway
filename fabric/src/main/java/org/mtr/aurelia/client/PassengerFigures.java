package org.mtr.aurelia.client;

/**
 * How many box figures to draw for one platform or train, and where they stand.
 * Far away, one figure stands in for the whole crowd. Past the draw distance, nothing is drawn.
 */
public final class PassengerFigures {

	/** Clear of a train's sides: floor is looked for this far from the rail centre. */
	private static final int EDGE_NEAREST = 2;
	private static final int EDGE_FARTHEST = 6;

	private PassengerFigures() {
	}

	/**
	 * @param people        people the server reported
	 * @param maxPerPlace   most figures at this platform or train
	 * @param distance      blocks from the camera to the place
	 * @param drawDistance  nothing is drawn past this
	 * @param detailDistance full figures inside this; one figure between here and the draw distance
	 */
	public static int toDraw(int people, int maxPerPlace, double distance, double drawDistance, double detailDistance) {
		if (people <= 0 || maxPerPlace <= 0 || drawDistance <= 0 || distance > drawDistance) {
			return 0;
		}
		if (distance > detailDistance) {
			return 1;
		}
		return Math.min(people, maxPerPlace);
	}

	/**
	 * Figure {@code index} of {@code count} along the segment, stood off to the side of the rail so the row is on the platform.
	 * The result is written into {@code out} as x, y, z.
	 */
	public static void place(double x1, double y1, double z1, double x2, double y2, double z2, int index, int count, double sideOffset, double[] out) {
		final double t = count <= 1 ? 0.5 : (index + 0.5) / count;
		final double x = x1 + (x2 - x1) * t;
		final double y = y1 + (y2 - y1) * t;
		final double z = z1 + (z2 - z1) * t;
		final double dx = x2 - x1;
		final double dz = z2 - z1;
		final double len = Math.hypot(dx, dz);
		double sideX = 0;
		double sideZ = 0;
		if (len > 1.0E-4 && sideOffset != 0) {
			sideX = -dz / len * sideOffset;
			sideZ = dx / len * sideOffset;
		}
		final int side = index % 2 == 0 ? 1 : -1;
		out[0] = x + sideX * side;
		out[1] = y;
		out[2] = z + sideZ * side;
	}

	/** The floor a figure can stand on in one column near the rail. */
	public interface Ground {
		/** @return the height of the floor at x, z, or NaN when there is none */
		double top(double x, double z, int railY);
	}

	/** Blocks from the camera to the nearest point of the platform, so standing on it counts as near however long it is. */
	public static double distanceToPlatform(double cx, double cy, double cz, double x1, double y1, double z1, double x2, double y2, double z2) {
		final double sx = x2 - x1;
		final double sy = y2 - y1;
		final double sz = z2 - z1;
		final double length = sx * sx + sy * sy + sz * sz;
		final double t = length <= 0 ? 0 : Math.max(0, Math.min(1, ((cx - x1) * sx + (cy - y1) * sy + (cz - z1) * sz) / length));
		final double dx = x1 + sx * t - cx;
		final double dy = y1 + sy * t - cy;
		final double dz = z1 + sz * t - cz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * Where {@code count} figures stand, written into {@code out} as x, y, z triples. They stand clear of the train on
	 * the floor beside the rail: on the higher side when one side is a platform, on both sides of an island.
	 */
	public static void spots(double x1, double y1, double z1, double x2, double y2, double z2, int count, Ground ground, double[] out) {
		final double dx = x2 - x1;
		final double dz = z2 - z1;
		final double length = Math.hypot(dx, dz);
		final double normalX = length < 1.0E-4 ? 0 : -dz / length;
		final double normalZ = length < 1.0E-4 ? 1 : dx / length;
		final int railY = (int) Math.floor(y1);
		final double[] plus = edge(x1, z1, dx, dz, normalX, normalZ, ground, railY);
		final double[] minus = edge(x1, z1, dx, dz, -normalX, -normalZ, ground, railY);
		final double height = Math.max(plus == null ? Double.NEGATIVE_INFINITY : plus[1], minus == null ? Double.NEGATIVE_INFINITY : minus[1]);
		final boolean usePlus = plus != null && plus[1] > height - 0.25;
		final boolean useMinus = minus != null && minus[1] > height - 0.25;
		final double[] noFloor = {EDGE_NEAREST, railY + 1};

		for (int i = 0; i < count; i++) {
			final boolean positive = usePlus && useMinus ? i % 2 == 0 : usePlus || !useMinus;
			final double[] edge = usePlus || useMinus ? (positive ? plus : minus) : noFloor;
			final double sign = positive ? 1 : -1;
			final double t = (i + 0.5) / count;
			final double x = x1 + dx * t;
			final double z = z1 + dz * t;
			double offset = edge[0] + 0.5 + (i % 3) * 0.7;
			double top = ground.top(x + normalX * sign * offset, z + normalZ * sign * offset, railY);
			if (Double.isNaN(top) || Math.abs(top - edge[1]) > 0.6) {
				offset = edge[0] + 0.5;
				top = ground.top(x + normalX * sign * offset, z + normalZ * sign * offset, railY);
			}
			out[i * 3] = x + normalX * sign * offset;
			out[i * 3 + 1] = Double.isNaN(top) ? edge[1] : top;
			out[i * 3 + 2] = z + normalZ * sign * offset;
		}
	}

	/** The first distance from the rail with a floor on this side, and its height, or null when this side has none. */
	private static double[] edge(double x1, double z1, double dx, double dz, double sideX, double sideZ, Ground ground, int railY) {
		for (int offset = EDGE_NEAREST; offset <= EDGE_FARTHEST; offset++) {
			double height = Double.NaN;
			for (double t = 0.25; t < 1; t += 0.25) {
				final double top = ground.top(x1 + dx * t + sideX * offset, z1 + dz * t + sideZ * offset, railY);
				if (!Double.isNaN(top) && (Double.isNaN(height) || top > height)) {
					height = top;
				}
			}
			if (!Double.isNaN(height)) {
				return new double[]{offset, height};
			}
		}
		return null;
	}
}
