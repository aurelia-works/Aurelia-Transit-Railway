package org.mtr.aurelia.client;

/**
 * One person seen walking between the platform and a train, along {@code points} (x, y, z triples): drawn only, the server
 * still counts them as boarded or alighted.
 */
public record PassengerWalk(double[] points, double length, long startMillis, long durationMillis, long place, int index, long platform, long vehicle, boolean boarding, boolean arriving) {

	/** Blocks a second: a person's pace, slower than a player's walk. */
	public static final double SPEED = 2.2;
	private static final long MIN_MILLIS = 600;

	public static PassengerWalk of(double[] points, long startMillis, long place, int index, long platform, long vehicle, boolean boarding) {
		return of(points, startMillis, place, index, platform, vehicle, boarding, false);
	}

	/** {@code arriving} is someone coming to the platform from an exit, not from a train. */
	public static PassengerWalk of(double[] points, long startMillis, long place, int index, long platform, long vehicle, boolean boarding, boolean arriving) {
		double length = 0;
		for (int i = 3; i < points.length; i += 3) {
			length += Math.hypot(points[i] - points[i - 3], points[i + 2] - points[i - 1]);
		}
		return new PassengerWalk(points, length, startMillis, Math.max(MIN_MILLIS, Math.round(length / SPEED * 1000)), place, index, platform, vehicle, boarding, arriving);
	}

	/** How solid they are: arrivals fade in over the first blocks, those leaving fade out over the last. */
	public float fade(double progress, double blocks) {
		if (boarding) {
			return 1;
		}
		return (float) Math.min(1, (arriving ? progress : 1 - progress) * length / blocks);
	}

	/** 0 until they set off, 1 once they arrive. */
	public double progress(long now) {
		return Math.max(0, Math.min(1, (now - startMillis) / (double) durationMillis));
	}

	public boolean finished(long now) {
		return now >= startMillis + durationMillis;
	}

	/** Writes x, y, z and the heading dx, dz of the leg they are on into {@code out}. */
	public void at(double progress, double[] out) {
		double left = progress * length;
		for (int i = 3; i < points.length; i += 3) {
			final double dx = points[i] - points[i - 3];
			final double dz = points[i + 2] - points[i - 1];
			final double leg = Math.hypot(dx, dz);
			if (left <= leg || i == points.length - 3) {
				final double t = leg <= 0 ? 1 : Math.min(1, left / leg);
				out[0] = points[i - 3] + dx * t;
				out[1] = points[i - 2] + (points[i + 1] - points[i - 2]) * t;
				out[2] = points[i - 1] + dz * t;
				out[3] = dx;
				out[4] = dz;
				return;
			}
			left -= leg;
		}
	}
}
