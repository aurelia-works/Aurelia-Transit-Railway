package org.mtr.aurelia.client;

/**
 * How many figures to draw for one platform or train, and where they stand.
 * Up close everyone is a full figure, farther away one simple figure stands in for the whole crowd, and past the draw
 * distance nothing is drawn.
 */
public final class PassengerFigures {

	/** Clear of a train's sides: floor is looked for this far from the rail centre. */
	private static final int EDGE_NEAREST = 2;
	private static final int EDGE_FARTHEST = 6;
	/** How far along the platform a spot moves to get out of a pillar, in steps each way. */
	private static final double NUDGE = 0.8;
	private static final int NUDGE_STEPS = 6;
	private static final double ROOM = 0.7;
	/** How far people at the same door stand either side of it. */
	private static final double DOOR_SPREAD = 0.75;

	/** How much of a person to draw at this distance. */
	public enum Detail {NONE, SIMPLE, FULL}

	private PassengerFigures() {
	}

	/**
	 * Nothing past the draw distance, a simple figure in the middle band, a full figure inside the detail distance.
	 * A detail distance past the draw distance is treated as the draw distance, so detail never outlives drawing.
	 */
	public static Detail detail(double distance, double drawDistance, double detailDistance) {
		if (drawDistance <= 0 || distance > drawDistance) {
			return Detail.NONE;
		}
		return distance > Math.min(detailDistance, drawDistance) ? Detail.SIMPLE : Detail.FULL;
	}

	/**
	 * @param people        people the server reported
	 * @param maxPerPlace   most figures at this platform or train
	 * @param distance      blocks from the camera to the place
	 * @param drawDistance  nothing is drawn past this
	 * @param detailDistance full figures inside this; one figure between here and the draw distance
	 */
	public static int toDraw(int people, int maxPerPlace, double distance, double drawDistance, double detailDistance) {
		if (people <= 0 || maxPerPlace <= 0) {
			return 0;
		}
		return switch (detail(distance, drawDistance, detailDistance)) {
			case NONE -> 0;
			case SIMPLE -> 1;
			case FULL -> Math.min(people, maxPerPlace);
		};
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
			double top = roomy(ground, x + normalX * sign * offset, z + normalZ * sign * offset, railY, edge[1]);
			double along = 0;
			// Something stands here: the nearest clear place along the platform, in the same row or the one by the edge
			for (int step = 1; step <= NUDGE_STEPS * 2 && (Double.isNaN(top) || Math.abs(top - edge[1]) > 0.6); step++) {
				along = (step + 1) / 2 * NUDGE * (step % 2 == 0 ? -1 : 1);
				final double tryOffset = edge[0] + 0.5 + (step % 4 < 2 ? (i % 3) * 0.7 : 0);
				final double alongX = length < 1.0E-4 ? 0 : dx / length * along;
				final double alongZ = length < 1.0E-4 ? 0 : dz / length * along;
				top = roomy(ground, x + alongX + normalX * sign * tryOffset, z + alongZ + normalZ * sign * tryOffset, railY, edge[1]);
				offset = tryOffset;
			}
			out[i * 3] = x + (length < 1.0E-4 ? 0 : dx / length * along) + normalX * sign * offset;
			out[i * 3 + 1] = Double.isNaN(top) ? edge[1] : top;
			out[i * 3 + 2] = z + (length < 1.0E-4 ? 0 : dz / length * along) + normalZ * sign * offset;
		}
	}

	/** The floor here, or NaN when something stands within arm's reach: people keep off pillars and walls. */
	private static double roomy(Ground ground, double x, double z, int railY, double floor) {
		final double top = ground.top(x, z, railY);
		for (int i = 0; i < 4 && !Double.isNaN(top); i++) {
			final double near = ground.top(x + (i == 0 ? ROOM : i == 1 ? -ROOM : 0), z + (i == 2 ? ROOM : i == 3 ? -ROOM : 0), railY);
			if (near > floor + 0.6) {
				return Double.NaN;
			}
			if (Double.isNaN(near)) {
				return Double.NaN;
			}
		}
		return top;
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

	/**
	 * Where people wait in front of a stopped train's doors. {@code doors} is door x, z pairs, as {@code doors()} builds
	 * them. People at the same door stand either side of it, and each rank behind them stands a little further out.
	 * With no doors this is {@link #spots}, so a platform with no train keeps its spread-out crowd.
	 */
	public static void spotsAtDoors(double x1, double y1, double z1, double x2, double y2, double z2, double[] doors, int count, Ground ground, double[] out) {
		if (doors.length < 2 || count <= 0) {
			spots(x1, y1, z1, x2, y2, z2, count, ground, out);
			return;
		}
		final double dx = x2 - x1;
		final double dz = z2 - z1;
		final double length = Math.hypot(dx, dz);
		final double alongX = length < 1.0E-4 ? 0 : dx / length;
		final double alongZ = length < 1.0E-4 ? 0 : dz / length;
		final double normalX = length < 1.0E-4 ? 0 : -dz / length;
		final double normalZ = length < 1.0E-4 ? 1 : dx / length;
		final int railY = (int) Math.floor(y1);
		final double[] plus = edge(x1, z1, dx, dz, normalX, normalZ, ground, railY);
		final double[] minus = edge(x1, z1, dx, dz, -normalX, -normalZ, ground, railY);
		final double height = Math.max(plus == null ? Double.NEGATIVE_INFINITY : plus[1], minus == null ? Double.NEGATIVE_INFINITY : minus[1]);
		final boolean usePlus = plus != null && plus[1] > height - 0.25;
		final boolean useMinus = minus != null && minus[1] > height - 0.25;
		final double[] noFloor = {EDGE_NEAREST, railY + 1};
		final int doorCount = doors.length / 2;

		for (int i = 0; i < count; i++) {
			final int door = i % doorCount;
			final double doorX = doors[door * 2];
			final double doorZ = doors[door * 2 + 1];
			final double at = length < 1.0E-4 ? 0.5 : clamp(((doorX - x1) * dx + (doorZ - z1) * dz) / (length * length), 0.02, 0.98);
			final int rank = i / doorCount;
			final double along = (rank == 0 ? 0 : ((rank + 1) / 2) * DOOR_SPREAD * (rank % 2 == 1 ? 1 : -1)) + ((i * 37) % 7 - 3) * 0.04;
			final boolean positive = usePlus && useMinus ? rank % 2 == 0 : usePlus || !useMinus;
			final double[] edge = usePlus || useMinus ? (positive ? plus : minus) : noFloor;
			final double sign = positive ? 1 : -1;
			final double baseX = x1 + dx * at + alongX * along;
			final double baseZ = z1 + dz * at + alongZ * along;
			double offset = edge[0] + 0.5 + (rank / 2 % 3) * 0.65 + ((i * 53) % 5 - 2) * 0.07;
			double top = roomy(ground, baseX + normalX * sign * offset, baseZ + normalZ * sign * offset, railY, edge[1]);
			double nudge = 0;
			for (int step = 1; step <= NUDGE_STEPS * 2 && (Double.isNaN(top) || Math.abs(top - edge[1]) > 0.6); step++) {
				nudge = (step + 1) / 2 * NUDGE * (step % 2 == 0 ? -1 : 1);
				top = roomy(ground, baseX + alongX * nudge + normalX * sign * offset, baseZ + alongZ * nudge + normalZ * sign * offset, railY, edge[1]);
			}
			out[i * 3] = baseX + alongX * nudge + normalX * sign * offset;
			out[i * 3 + 1] = Double.isNaN(top) ? edge[1] : top;
			out[i * 3 + 2] = baseZ + alongZ * nudge + normalZ * sign * offset;
		}
	}

	/**
	 * Copies waiting spots to {@code to}, moving those within {@code reach} of a door sideways along the platform by
	 * {@code strength} times {@code shift}, so the people getting off have a clear doorway. {@code strength} 0 leaves
	 * everyone where they were; 1 is fully aside.
	 */
	public static void stepAside(double[] from, double[] to, int count, double x1, double z1, double x2, double z2, double[] doors, double strength, double reach, double shift) {
		System.arraycopy(from, 0, to, 0, count * 3);
		if (strength <= 0 || doors.length < 2) {
			return;
		}
		final double dx = x2 - x1;
		final double dz = z2 - z1;
		final double length = Math.hypot(dx, dz);
		final double alongX = length < 1.0E-4 ? 0 : dx / length;
		final double alongZ = length < 1.0E-4 ? 0 : dz / length;
		final double push = clamp(strength, 0, 1) * shift;
		for (int i = 0; i < count; i++) {
			double nearest = Double.MAX_VALUE;
			double doorAt = 0;
			for (int door = 0; door < doors.length; door += 2) {
				final double distance = Math.hypot(doors[door] - from[i * 3], doors[door + 1] - from[i * 3 + 2]);
				if (distance < nearest) {
					nearest = distance;
					doorAt = length < 1.0E-4 ? 0.5 : ((doors[door] - x1) * dx + (doors[door + 1] - z1) * dz) / (length * length);
				}
			}
			if (nearest > reach) {
				continue;
			}
			final double at = length < 1.0E-4 ? 0.5 : ((from[i * 3] - x1) * dx + (from[i * 3 + 2] - z1) * dz) / (length * length);
			final double sign = at == doorAt ? (i % 2 == 0 ? 1 : -1) : at > doorAt ? 1 : -1;
			to[i * 3] += alongX * push * sign;
			to[i * 3 + 2] += alongZ * push * sign;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
