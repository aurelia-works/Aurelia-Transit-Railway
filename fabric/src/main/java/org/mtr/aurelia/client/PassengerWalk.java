package org.mtr.aurelia.client;

/** One person seen walking between the platform and a train: drawn only, the server still counts them as boarded or alighted. */
public record PassengerWalk(double x1, double y1, double z1, double x2, double y2, double z2, long startMillis, long durationMillis, long place, int index, long vehicle, boolean boarding) {

	/** Blocks a second: a person's pace, slower than a player's walk. */
	public static final double SPEED = 2.2;
	private static final long MIN_MILLIS = 600;

	public static PassengerWalk of(double x1, double y1, double z1, double x2, double y2, double z2, long startMillis, long place, int index, long vehicle, boolean boarding) {
		final long duration = Math.max(MIN_MILLIS, Math.round(Math.hypot(x2 - x1, z2 - z1) / SPEED * 1000));
		return new PassengerWalk(x1, y1, z1, x2, y2, z2, startMillis, duration, place, index, vehicle, boarding);
	}

	/** 0 until they set off, 1 once they arrive. */
	public double progress(long now) {
		return Math.max(0, Math.min(1, (now - startMillis) / (double) durationMillis));
	}

	public boolean finished(long now) {
		return now >= startMillis + durationMillis;
	}
}
