package org.mtr.aurelia.client;

/**
 * How many box figures to draw for one platform or train, and where they stand.
 * Far away, one figure stands in for the whole crowd. Past the draw distance, nothing is drawn.
 */
public final class PassengerFigures {

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
}
